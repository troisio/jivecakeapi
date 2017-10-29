package com.jivecake.api.resources;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
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

import org.apache.poi.util.IOUtils;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.FindOptions;
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
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.FaceAnnotation;
import com.google.cloud.vision.v1.Feature.Type;
import com.jivecake.api.APIConfiguration;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.GZip;
import com.jivecake.api.filter.LimitUserRequest;
import com.jivecake.api.model.AssetType;
import com.jivecake.api.model.EntityAsset;
import com.jivecake.api.model.EntityType;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.OrganizationInvitation;
import com.jivecake.api.model.Permission;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.GoogleCloudPlatformService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.OrganizationService;
import com.jivecake.api.service.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;

@CORS
@Path("user")
@Singleton
public class UserResource {
    private final Datastore datastore;
    private final StripeService stripeService;
    private final ApplicationService applicationService;
    private final OrganizationService organizationService;
    private final GoogleCloudPlatformService googleCloudPlatformService;
    private final NotificationService notificationService;
    private final APIConfiguration configuration;

    @Inject
    public UserResource(
        Datastore datastore,
        StripeService stripeService,
        ApplicationService applicationService,
        OrganizationService organizationService,
        GoogleCloudPlatformService googleCloudPlatformService,
        NotificationService notificationService,
        APIConfiguration configuration
    ) {
        this.datastore = datastore;
        this.stripeService = stripeService;
        this.applicationService = applicationService;
        this.organizationService = organizationService;
        this.googleCloudPlatformService = googleCloudPlatformService;
        this.notificationService = notificationService;
        this.configuration = configuration;
    }

    @GZip
    @GET
    @Path("{id}/invite")
    @Authorized
    public Response getOrganizations(
        @Context DecodedJWT jwt,
        @PathParam("id") String userId
    ) {
        ResponseBuilder builder;

        if (jwt.getSubject().equals(userId)) {
            Query<OrganizationInvitation> query = this.datastore.createQuery(OrganizationInvitation.class)
                .field("userIds").equal(userId)
                .order("-timeCreated");

            FindOptions options = new FindOptions();
            options.limit(ApplicationService.LIMIT_DEFAULT);

            builder = Response.ok(query.asList(options), MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.BAD_REQUEST);
        }

        return builder.build();
    }

    @GZip
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
    @Authorized
    @LimitUserRequest(count=5, per=1000 * 60)
    public Response uploadSelfie(
        @PathParam("user_id") String pathUserId,
        @HeaderParam("Content-Type") String contentType,
        @Context DecodedJWT jwt,
        InputStream stream
    ) throws IOException {
        Storage storage = StorageOptions.getDefaultInstance().getService();
        String name = UUID.randomUUID().toString();
        BlobInfo info = BlobInfo.newBuilder(BlobId.of(this.configuration.gcp.bucket, name))
            .setContentType(contentType)
            .setAcl(Arrays.asList(Acl.of(User.ofAllUsers(), Role.READER)))
            .setStorageClass(StorageClass.REGIONAL)
            .build();

        byte[] bytes = IOUtils.toByteArray(stream);
        Blob blob = storage.create(info, bytes);

        IOException exception = null;
        List<AnnotateImageResponse> responses = null;

        try {
            responses = this.googleCloudPlatformService.getAnnotations(
                Type.FACE_DETECTION,
                String.format("gs://%s/%s", this.configuration.gcp.bucket, name)
            );
            exception = null;
        } catch (IOException e) {
            exception = e;
        }

        ResponseBuilder builder;

        if (exception == null) {
            boolean detectsOneFace = false;

            AnnotateImageResponse response = responses.get(0);
            List<FaceAnnotation> annotations = response.getFaceAnnotationsList();

            if (annotations.size() ==1) {
                FaceAnnotation annotation = annotations.get(0);
                detectsOneFace = annotation.getDetectionConfidence() > 0.9;
            }

            if (detectsOneFace) {
                List<EntityAsset> assets = this.datastore.createQuery(EntityAsset.class)
                    .field("entityType").equal(EntityType.USER)
                    .field("entityId").equal(jwt.getSubject())
                    .field("assetType").equal(AssetType.GOOGLE_CLOUD_STORAGE_BLOB_FACE)
                    .asList();

                for (EntityAsset asset: assets) {
                    String[] parts = asset.assetId.split("/");

                    try {
                        storage.delete(BlobId.of(parts[0], parts[1]));
                    } catch (StorageException e) {
                        e.printStackTrace();
                    }

                    this.datastore.delete(asset);
                }

                EntityAsset asset = new EntityAsset();
                asset.entityType = EntityType.USER;
                asset.entityId = jwt.getSubject();
                asset.assetId = blob.getBucket() + "/" + blob.getName();
                asset.assetType = AssetType.GOOGLE_CLOUD_STORAGE_BLOB_FACE;
                asset.name = "";
                asset.timeCreated = new Date();

                this.datastore.save(asset);
                this.notificationService.notify(Arrays.asList(asset), "asset.create");

                builder = Response.ok(asset).type(MediaType.APPLICATION_JSON);
            } else {
                try {
                    storage.delete(BlobId.of(this.configuration.gcp.bucket, name));
                } catch (StorageException e) {
                    e.printStackTrace();
                }

                Map<String, Object> entity = new HashMap<>();
                entity.put("error", "face");

                Map<String, Object> data = new HashMap<>();
                data.put("annotationsCount", annotations.size());

                if (annotations.size() == 1) {
                    FaceAnnotation annotation = annotations.get(0);
                    data.put("confidence", annotation.getDetectionConfidence());
                }

                entity.put("data", data);

                builder = Response.status(Status.BAD_REQUEST)
                    .entity(entity)
                    .type(MediaType.APPLICATION_JSON);
            }
        } else {
            this.applicationService.saveException(exception, jwt.getSubject());
            builder = Response.status(Status.SERVICE_UNAVAILABLE);
        }

        return builder.build();
    }

    @GET
    @Authorized
    @Path("{userId}/organizationInvitation")
    public Response getOrganizationInvitations(
        @PathParam("userId") String userId,
        @Context DecodedJWT jwt
    ) {
        ResponseBuilder builder;

        if (jwt.getSubject().equals(userId)) {
            long days7 = 1000 * 60 * 60 * 24 * 7;
            Date lessThanTimeCreated = new Date(new Date().getTime() + days7);

            List<OrganizationInvitation> invitations = this.datastore.createQuery(OrganizationInvitation.class)
                .field("userIds").equal(userId)
                .field("timeCreated").lessThan(lessThanTimeCreated)
                .field("timeAccepted").doesNotExist()
                .asList();

            builder = Response.ok(invitations)
                .type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @GET
    @Authorized
    @Path("{userId}/subscription")
    public Response getSubscriptions(
        @PathParam("userId") String userId,
        @Context DecodedJWT jwt
    ) throws StripeException {
        ResponseBuilder builder;

        if (jwt.getSubject().equals(userId)) {
            Map<String, Object> query = new HashMap<>();
            query.put("status", "all");
            query.put("limit", "100");

            Iterable<Subscription> subscriptions = Subscription.list(query, this.stripeService.getRequestOptions())
                .autoPagingIterable();

            List<Subscription> result = new ArrayList<>();

            for (Subscription subscription: subscriptions) {
                if (userId.equals(subscription.getMetadata().get("sub"))) {
                    result.add(subscription);
                }
            }

            builder = Response.ok(result, MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }
}