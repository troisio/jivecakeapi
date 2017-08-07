package com.jivecake.api.resources;

import java.util.Arrays;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.GZip;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.model.AssetType;
import com.jivecake.api.model.EntityAsset;
import com.jivecake.api.model.EntityType;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Organization;
import com.jivecake.api.request.ErrorData;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.PermissionService;

@CORS
@Path("asset")
@Singleton
public class AssetResource {
    private final Datastore datastore;
    private final PermissionService permissionService;
    private final NotificationService notificationService;

    @Inject
    public AssetResource(
        Datastore datastore,
        PermissionService permissionService,
        NotificationService notificationService
    ) {
        this.datastore = datastore;
        this.permissionService = permissionService;
        this.notificationService = notificationService;
    }

    @GZip
    @GET
    @Authorized
    public Response search(
        @QueryParam("entityId") String entityId,
        @QueryParam("entityType") Integer entityType,
        @QueryParam("assetType") Integer assetType,
        @QueryParam("order") String order,
        @QueryParam("limit") Integer limit,
        @QueryParam("skip") Integer skip,
        @Context DecodedJWT jwt
    ) {
        ResponseBuilder builder;

        boolean hasUserPermission = jwt.getSubject().equals(entityId) &&
            Objects.equals(entityType, EntityType.USER);
        boolean hasOrganizationPermission = false;

        if (Objects.equals(entityType, EntityType.ORGANIZATION) && entityId != null) {
            hasOrganizationPermission = this.permissionService.has(
                jwt.getSubject(),
                Organization.class,
                PermissionService.READ,
                new ObjectId(entityId)
            );
        }

        if (hasUserPermission || hasOrganizationPermission) {
            Query<EntityAsset> query = this.datastore.createQuery(EntityAsset.class);

            if (entityType != null) {
                query.field("entityType").equal(entityType);
            }

            if (entityId != null) {
                query.field("entityId").equal(entityId);
            }

            if (assetType != null) {
                query.field("assetType").equal(assetType);
            }

            if (order != null) {
                query.order(order);
            }

            FindOptions options = new FindOptions();

            if (limit != null && limit > -1 && limit <= ApplicationService.LIMIT_DEFAULT) {
                options.limit(limit);
            } else {
                options.limit(ApplicationService.LIMIT_DEFAULT);
            }

            if (skip != null && skip > -1) {
                options.skip(skip);
            }

            Paging<EntityAsset> paging = new Paging<>(query.asList(options), query.count());
            builder = Response.ok(paging).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @DELETE
    @Path("{id}")
    @Authorized
    public Response delete(
        @PathObject("id") EntityAsset asset,
        @Context DecodedJWT jwt
    ) {
        ResponseBuilder builder;

        if (asset == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            boolean hasUserPermission = asset.entityType == EntityType.USER &&
                jwt.getSubject().equals(asset.entityId);
            boolean hasOrganizationPermission = false;

            if (asset.entityType == EntityType.ORGANIZATION) {
                hasOrganizationPermission = this.permissionService.has(
                    jwt.getSubject(),
                    Organization.class,
                    PermissionService.WRITE,
                    new ObjectId(asset.entityId)
                );
            }

            if (hasOrganizationPermission || hasUserPermission) {
                boolean inUseByEvent = this.datastore.createQuery(Event.class)
                    .field("entityAssetConsentId").equal(asset.id)
                    .count() > 0;

                if (inUseByEvent) {
                    ErrorData data = new ErrorData();
                    data.error = "entityAssetConsentId";
                    builder = Response.status(Status.BAD_REQUEST)
                        .entity(data)
                        .type(MediaType.APPLICATION_JSON);
                } else {
                    boolean isInGoogleCoudStorage = asset.assetType == AssetType.GOOGLE_CLOUD_STORAGE_BLOB_FACE ||
                        asset.assetType == AssetType.GOOGLE_CLOUD_STORAGE_CONSENT_PDF;

                    if (isInGoogleCoudStorage) {
                        Storage storage = StorageOptions.getDefaultInstance().getService();
                        String[] parts = asset.assetId.split("/");

                        try {
                            storage.delete(BlobId.of(parts[0], parts[1]));
                        } catch (StorageException e) {
                            e.printStackTrace();
                        }
                    }

                    this.datastore.delete(asset);
                    this.notificationService.notify(Arrays.asList(asset), "asset.delete");
                    builder = Response.ok();
                }
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }
}
