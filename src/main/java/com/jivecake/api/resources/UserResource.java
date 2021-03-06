package com.jivecake.api.resources;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.poi.util.IOUtils;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;

import com.auth0.client.mgmt.filter.UserFilter;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.jivecake.api.APIConfiguration;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.GZip;
import com.jivecake.api.filter.LimitUserRequest;
import com.jivecake.api.model.Application;
import com.jivecake.api.model.AssetType;
import com.jivecake.api.model.EntityAsset;
import com.jivecake.api.model.EntityType;
import com.jivecake.api.model.OrganizationInvitation;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.Auth0Service;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.PermissionService;
import com.jivecake.api.service.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;

@CORS
@Path("user")
@Singleton
public class UserResource {
    private final Datastore datastore;
    private final StripeService stripeService;
    private final NotificationService notificationService;
    private final PermissionService permissionService;
    private final ApplicationService applicationService;
    private final Auth0Service auth0Service;
    private final APIConfiguration configuration;

    @Inject
    public UserResource(
        Datastore datastore,
        StripeService stripeService,
        NotificationService notificationService,
        PermissionService permissionService,
        ApplicationService applicationService,
        Auth0Service auth0Service,
        APIConfiguration configuration
    ) {
        this.datastore = datastore;
        this.stripeService = stripeService;
        this.notificationService = notificationService;
        this.permissionService = permissionService;
        this.applicationService = applicationService;
        this.auth0Service = auth0Service;
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

        return Response.ok(asset).type(MediaType.APPLICATION_JSON).build();
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

            builder = Response.ok(invitations, MediaType.APPLICATION_JSON);
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

            Iterable<Subscription> subscriptions = Subscription
                .list(query, this.stripeService.getRequestOptions())
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

    @POST
    @Authorized
    @Path("{userId}/token")
    public Response getToken(
        @PathParam("userId") String userId,
        @Context DecodedJWT jwt
    ) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        Application application = this.applicationService.read();

        boolean hasPermission = this.permissionService.hasWrite(
            jwt.getSubject(),
            Arrays.asList(application)
        );

        if (!hasPermission) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        com.auth0.json.mgmt.users.User user = this.auth0Service.getManagementApi()
            .users()
            .get(userId, new UserFilter())
            .execute();

        if (user == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        String value = new ObjectMapper().writeValueAsString(this.auth0Service.getSignedJWT(user));

        return Response.ok(value, MediaType.APPLICATION_JSON).build();
    }
}