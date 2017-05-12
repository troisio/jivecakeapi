package com.jivecake.api.resources;

import static org.bytedeco.javacpp.opencv_imgcodecs.imread;
import static org.bytedeco.javacpp.opencv_imgcodecs.imwrite;
import static org.bytedeco.javacpp.opencv_imgproc.resize;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.FileUtils;
import org.bson.types.ObjectId;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Size;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.Query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.LimitUserRequest;
import com.jivecake.api.filter.Log;
import com.jivecake.api.model.AssetType;
import com.jivecake.api.model.EntityAsset;
import com.jivecake.api.model.EntityType;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.Permission;
import com.jivecake.api.service.FacialRecognitionService;
import com.jivecake.api.service.ImgurService;
import com.jivecake.api.service.OrganizationService;

@CORS
@Path("/user")
@Singleton
public class UserResource {
    private final Datastore datastore;
    private final OrganizationService organizationService;
    private final FacialRecognitionService facialRecognitionService;
    private final ImgurService imgurService;
    private final Size selfieResize = new Size(200, 200);
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    public UserResource(
        Datastore datastore,
        OrganizationService organizationService,
        FacialRecognitionService facialRecognitionService,
        ImgurService imgurService
    ) {
        this.datastore = datastore;
        this.organizationService = organizationService;
        this.facialRecognitionService = facialRecognitionService;
        this.imgurService = imgurService;
    }

    @GET
    @Path("/{user_id}/organization")
    @Authorized
    public Response getOrganizations(
        @PathParam("user_id") String userId,
        @QueryParam("order") String order,
        @Context JsonNode claims
    ) {
        String user_id = claims.get("sub").asText();
        boolean hasPermission = userId.equals(user_id);

        ResponseBuilder builder;

        if (hasPermission) {
            List<ObjectId> organizationIds = this.datastore.createQuery(Permission.class)
                .field("user_id").equal(userId)
                .field("objectClass").equal(this.organizationService.getPermissionObjectClass())
                .asList()
                .stream()
                .map(permission -> permission.objectId)
                .collect(Collectors.toList());

            List<Organization> organizations = this.datastore.createQuery(Organization.class)
                .field("id").in(organizationIds)
                .asList();

            List<ObjectId> childrenIds = organizations.stream()
                .map(organization -> organization.children)
                .flatMap(collection -> collection.stream())
                .collect(Collectors.toList());

            Query<Organization> query = this.datastore.createQuery(Organization.class);
            query.or(
                query.criteria("id").in(organizationIds),
                query.criteria("id").in(childrenIds)
            );

            if (order != null) {
                query.order(order);
            }

            builder = Response.ok(query.asList()).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @POST
    @Consumes({"image/jpeg", "image/png"})
    @Path("/{user_id}/selfie")
    @Log(body = false)
    @Authorized
    @LimitUserRequest(count=5, per=1000 * 60 * 60)
    public void uploadSelfie(
        @PathParam("user_id") String pathUserId,
        @Context JsonNode claims,
        InputStream stream,
        @Suspended AsyncResponse promise
    ) {
        String user_id = claims.get("sub").asText();

        if (user_id.equals(pathUserId)) {
            List<File> selfies = null;
            IOException exception = null;
            File temporaryFile = null;

            try {
                temporaryFile = File.createTempFile(UUID.randomUUID().toString(), ".jpg");
                FileUtils.copyInputStreamToFile(stream, temporaryFile);
                selfies = this.facialRecognitionService.getSelfies(temporaryFile);
            } catch (IOException e) {
                exception = e;
            }

            if (exception == null) {
                temporaryFile.delete();

                if (selfies.size() == 1) {
                    Mat matrix = imread(selfies.get(0).getAbsolutePath());
                    resize(matrix, matrix, this.selfieResize);

                    /*
                     It ought to be possible to derive a byte[] from the resized image matrix,
                     or from a Mat object in general but I have not found a way to do this,
                     if you know how to hit me up on the sneak
                     */
                    imwrite(temporaryFile.getAbsolutePath(), matrix);
                    IOException readException = null;;
                    byte[] data = null;

                    try {
                        data = Files.readAllBytes(temporaryFile.toPath());
                    } catch (IOException e) {
                        readException = e;
                    }

                    if (readException == null) {
                        MultivaluedHashMap<String, String> form = new MultivaluedHashMap<>();
                        form.putSingle("type", "base64");
                        form.putSingle("description", user_id);
                        form.putSingle("image",  Base64.getEncoder().encodeToString(data));

                        this.imgurService.postImageRequest(form).submit(new InvocationCallback<Response>() {
                            @Override
                            public void completed(Response response) {
                                JsonNode node = null;
                                IOException exception = null;

                                try {
                                    node = UserResource.this.mapper.readTree(response.readEntity(String.class));
                                } catch (IOException e) {
                                    exception = e;
                                }

                                if (exception == null) {
                                    EntityAsset asset = new EntityAsset();
                                    asset.entityId = user_id;
                                    asset.data = node.get("data").toString().getBytes();
                                    asset.assetType = AssetType.IMGUR_IMAGE;
                                    asset.entityType = EntityType.USER;
                                    asset.timeCreated = new Date();

                                    UserResource.this.datastore.save(asset);

                                    promise.resume(Response.ok(asset).build());
                                } else {
                                    promise.resume(exception);
                                }
                            }

                            @Override
                            public void failed(Throwable throwable) {
                                promise.resume(throwable);
                            }
                         });
                    } else {
                        promise.resume(readException);
                    }
                } else {
                    String entity = String.format("{\"error\": \"selfieLength\", \"length\": %d}", selfies.size());
                    Response response = Response.status(Status.BAD_REQUEST)
                        .entity(entity)
                        .type(MediaType.APPLICATION_JSON)
                        .build();
                    promise.resume(response);
                }
            } else {
                promise.resume(exception);
            }
        } else {
            promise.resume(Response.status(Status.UNAUTHORIZED).build());
        }
    }
}