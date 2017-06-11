package com.jivecake.api.resources;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.Query;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.cloud.storage.Acl;
import com.google.cloud.storage.Acl.Role;
import com.google.cloud.storage.Acl.User;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageClass;
import com.google.cloud.storage.StorageOptions;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.LimitUserRequest;
import com.jivecake.api.filter.Log;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.Permission;
import com.jivecake.api.service.OrganizationService;

@CORS
@Path("user")
@Singleton
public class UserResource {
    private final Datastore datastore;
    private final OrganizationService organizationService;

    @Inject
    public UserResource(
        Datastore datastore,
        OrganizationService organizationService
    ) {
        this.datastore = datastore;
        this.organizationService = organizationService;
    }

    @GET
    @Path("{user_id}/organization")
    @Authorized
    public Response getOrganizations(
        @PathParam("user_id") String userId,
        @QueryParam("order") String order,
        @Context DecodedJWT jwt
    ) {
        boolean hasPermission = userId.equals(jwt.getSubject());

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
    @Path("{user_id}/selfie")
    @Log(body = false)
    @Authorized
    @LimitUserRequest(count=2, per=1000 * 60)
    public Response uploadSelfie(
        @PathParam("user_id") String pathUserId,
        @HeaderParam("Content-Type") String contentType,
        InputStream stream
    ) {
        Storage storage = StorageOptions.getDefaultInstance().getService();
        BlobInfo info = BlobInfo.newBuilder(BlobId.of("jivecake", UUID.randomUUID().toString()))
            .setContentType(contentType)
            .setAcl(Arrays.asList(Acl.of(User.ofAllUsers(), Role.READER)))
            .setStorageClass(StorageClass.REGIONAL)
            .build();

        Blob blob = storage.create(info, stream);

        Map<String, Object> entity = new HashMap<>();
        entity.put("bucket", blob.getBucket());
        entity.put("name", blob.getName());

        return Response.ok(entity).type(MediaType.APPLICATION_JSON).build();
    }
}