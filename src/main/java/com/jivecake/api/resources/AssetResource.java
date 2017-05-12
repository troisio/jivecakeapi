package com.jivecake.api.resources;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;

import com.fasterxml.jackson.databind.JsonNode;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.model.EntityAsset;
import com.jivecake.api.model.EntityType;
import com.jivecake.api.service.ApplicationService;

@CORS
@Path("asset")
@Singleton
public class AssetResource {
    private final Datastore datastore;

    @Inject
    public AssetResource(Datastore datastore) {
        this.datastore = datastore;
    }

    @GET
    @Authorized
    public Response search(
        @QueryParam("entityId") String entityId,
        @QueryParam("entityType") Integer entityType,
        @QueryParam("assetType") Integer assetType,
        @QueryParam("order") String order,
        @QueryParam("limit") Integer limit,
        @QueryParam("skip") Integer skip,
        @Context JsonNode claims
    ) {
        ResponseBuilder builder;

        boolean hasPermission = claims.get("sub").asText().equals(entityId);

        if (hasPermission) {
            Query<EntityAsset> query = this.datastore.createQuery(EntityAsset.class);

            if (entityType != null) {
                query.field("entityType").equal(EntityType.USER);
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
            options.limit(ApplicationService.LIMIT_DEFAULT);

            if (skip != null && skip > -1) {
                options.skip(skip);
            }

            builder = Response.ok(query.asList(options)).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }
}
