package com.jivecake.api.resources;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
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
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;

import com.fasterxml.jackson.databind.JsonNode;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.model.Application;
import com.jivecake.api.model.Feature;
import com.jivecake.api.model.OrganizationFeature;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.FeatureService;
import com.jivecake.api.service.OrganizationService;
import com.jivecake.api.service.PermissionService;

@CORS
@Path("/feature")
public class FeatureResource {
    private final ApplicationService applicationService;
    private final PermissionService permissionService;
    private final OrganizationService organizationService;
    private final FeatureService featureService;

    @Inject
    public FeatureResource(ApplicationService applicationService, PermissionService permissionService, OrganizationService organizationService, FeatureService featureService) {
        this.applicationService = applicationService;
        this.permissionService = permissionService;
        this.organizationService = organizationService;
        this.featureService = featureService;
    }

    @GET
    @Authorized
    public Response search(
        @QueryParam("organizationId") Set<ObjectId> organizationIds,
        @QueryParam("type") Set<Integer> types,
        @QueryParam("timeStartGreaterThan") Long timeStartGreaterThan,
        @QueryParam("timeStartLessThan") Long timeStartLessThan,
        @QueryParam("timeEndGreaterThan") Long timeEndGreaterThan,
        @QueryParam("timeEndLessThan") Long timeEndLessThan,
        @QueryParam("limit") Integer limit,
        @QueryParam("offset") Integer offset,
        @QueryParam("order") String order,
        @Context JsonNode claims
    ) {
        Query<Feature> query = this.featureService.query().disableValidation();

        if (!organizationIds.isEmpty()) {
            query.field("organizationId").in(organizationIds);
        }

        if (!types.isEmpty()) {
            query.field("type").in(types);
        }

        if (timeStartGreaterThan != null) {
            query.field("timeStart").greaterThan(new Date(timeStartGreaterThan));
        }

        if (timeStartGreaterThan != null) {
            query.field("timeStart").lessThan(new Date(timeStartLessThan));
        }

        if (timeEndGreaterThan != null) {
            query.field("timeEnd").greaterThan(new Date(timeEndGreaterThan));
        }

        if (timeEndLessThan != null) {
            query.field("timeEnd").lessThan(new Date(timeEndLessThan));
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

        List<Feature> features = query.asList(options);

        Set<ObjectId> organizationPermissionIds = features.stream()
            .filter(feature -> feature instanceof OrganizationFeature)
            .map(feature -> ((OrganizationFeature)feature).organizationId)
            .collect(Collectors.toSet());

        boolean hasPermission = this.permissionService.hasAllHierarchicalPermission(
            claims.get("sub").asText(),
            this.organizationService.getReadPermission(),
            organizationPermissionIds
        );

        ResponseBuilder builder;

        if (hasPermission) {
            Paging<Feature> entity = new Paging<>(features, query.count());
            builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @DELETE
    @Path("/{id}")
    @Authorized
    public Response create(@PathObject("id") Feature feature, @Context JsonNode claims) {
        ResponseBuilder builder;

        if (feature == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            Application application = this.applicationService.read();

            boolean hasPermission = this.permissionService.has(
                claims.get("sub").asText(),
                Application.class,
                this.applicationService.getWritePermission(),
                application.id
            );

            if (hasPermission) {
                this.featureService.delete(feature);
                builder = Response.ok().type(MediaType.APPLICATION_JSON);
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }
}