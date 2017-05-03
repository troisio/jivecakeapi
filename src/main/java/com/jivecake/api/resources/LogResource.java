package com.jivecake.api.resources;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
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
import com.jivecake.api.model.Application;
import com.jivecake.api.model.Request;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.PermissionService;

@Path("/log")
@CORS
public class LogResource {
    private final ApplicationService applicationService;
    private final PermissionService permissionService;
    private final Datastore datastore;

    @Inject
    public LogResource(
        ApplicationService applicationService,
        PermissionService permissionService,
        Datastore datastore
    ) {
        this.applicationService = applicationService;
        this.permissionService = permissionService;
        this.datastore = datastore;
    }

    @GET
    @Path("/http")
    @Authorized
    public Response query(
        @QueryParam("limit") Integer limit,
        @QueryParam("offset") Integer offset,
        @QueryParam("order") String order,
        @QueryParam("user_id") List<String> userIds,
        @QueryParam("timeCreatedLessThan") Long timeCreatedLessThan,
        @QueryParam("timeCreatedGreaterThan") Long timeCreatedGreaterThan,
        @Context JsonNode claims
    ) {
        Application application = this.applicationService.read();

        ResponseBuilder builder;

        boolean hasPermission = this.permissionService.has(
            claims.get("sub").asText(),
            Arrays.asList(application),
            PermissionService.READ
        );

        if (hasPermission) {
            Query<Request> query = this.datastore.createQuery(Request.class);

            if (!userIds.isEmpty()) {
                query.field("user_id").in(userIds);
            }

            if (timeCreatedGreaterThan != null) {
                query.field("timeCreated").greaterThan(new Date(timeCreatedGreaterThan));
            }

            if (timeCreatedLessThan != null) {
                query.field("timeCreated").lessThan(new Date(timeCreatedLessThan));
            }

            if (order != null) {
                query.order(order);
            }

            FindOptions options = new FindOptions();

            if (limit != null && limit > -1) {
                options.limit(limit);
            }

            if (offset != null && offset > -1) {
                options.skip(offset);
            }

            Paging<Request> entity = new Paging<>(query.asList(options), query.count());
            builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }
}
