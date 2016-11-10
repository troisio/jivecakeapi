package com.jivecake.api.resources;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.bson.types.ObjectId;
import org.mongodb.morphia.query.Query;

import com.fasterxml.jackson.databind.JsonNode;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.QueryRestrict;
import com.jivecake.api.model.Application;
import com.jivecake.api.model.Permission;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.OrganizationService;
import com.jivecake.api.service.PermissionService;

@Path("/permission")
@CORS
public class PermissionResource {
    private final PermissionService permissionService;
    private final ApplicationService applicationService;
    private final OrganizationService organizationService;

    @Inject
    public PermissionResource(
        PermissionService permissionService,
        ApplicationService applicationService,
        OrganizationService organizationService
    ) {
        this.permissionService = permissionService;
        this.applicationService = applicationService;
        this.organizationService = organizationService;
    }

    @GET
    @Path("/type")
    @Authorized
    public Response getPermissionTypes() {
        Map<String, Set<String>> entity = this.permissionService.getPermissionsByObjectClass();
        Response response = Response.ok(entity).build();
        return response;
    }

    @DELETE
    @Authorized
    @QueryRestrict(hasAny=true, target={"user_id", "objectId"})
    public Response write(
        @QueryParam("user_id") List<String> userIds,
        @QueryParam("objectId") Set<ObjectId> objectIds,
        @QueryParam("objectClass") String objectClass,
        @Context JsonNode claims
    ) {
        ResponseBuilder builder;

        boolean hasUserPermission = userIds.size() == 1 && claims.get("sub").asText().equals(userIds.get(0));
        boolean isOrganizationQuery = this.organizationService.getPermissionObjectClass().equals(objectClass) && !objectIds.isEmpty();
        boolean hasOrganizationPermission;

        if (isOrganizationQuery) {
            Set<ObjectId> organizationIds = this.permissionService.query()
                .field("objectClass").equal(objectClass)
                .field("objectId").in(objectIds)
                .asList()
                .stream()
                .map(permission -> permission.objectId)
                .collect(Collectors.toSet());

            hasOrganizationPermission = this.permissionService.hasAllHierarchicalPermission(
                claims.get("sub").asText(),
                this.organizationService.getWritePermission(),
                organizationIds
            );
        } else {
            hasOrganizationPermission = false;
        }

        if (hasUserPermission || hasOrganizationPermission) {
            Query<Permission> query = this.permissionService.query();

            if (!userIds.isEmpty()) {
                query.field("user_id").in(userIds);
            }

            if (objectClass != null) {
                query.field("objectClass").equal(objectClass);
            }

            if (!objectIds.isEmpty()) {
                query.field("objectId").in(objectIds);
            }

            this.permissionService.delete(query);
            builder = Response.ok().type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    public Response create(@Context JsonNode claims, List<Permission> permissions) {
        Application application = this.applicationService.read();

        boolean hasPermission = this.permissionService.has(
            claims.get("sub").asText(),
            Application.class,
            this.applicationService.getWritePermission(),
            application.id
        );

        ResponseBuilder builder;

        if (hasPermission) {
            Date timeCreated = new Date();

            for (Permission permission : permissions) {
                permission.id = null;
                permission.timeCreated = timeCreated;
                permission.objectClass = this.organizationService.getPermissionObjectClass();
            }

            this.permissionService.write(permissions);
            builder = Response.ok();
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @GET
    @Authorized
    public Response search(
        @QueryParam("user_id") List<String> user_ids,
        @QueryParam("objectId") List<ObjectId> objectIds,
        @QueryParam("objectClass") List<String> objectClasses,
        @QueryParam("permission") Set<String> permissions,
        @QueryParam("limit") Integer limit,
        @QueryParam("offset") Integer offset,
        @Context JsonNode claims
     ) {
        ResponseBuilder builder;

        Query<Permission> query = this.permissionService.query();

        if (!user_ids.isEmpty()) {
            query.field("user_id").in(user_ids);
        }

        if (!objectIds.isEmpty()) {
            query.field("objectId").in(objectIds);
        }

        if (!objectClasses.isEmpty()) {
            query.field("objectClass").in(objectClasses);
        }

        if (!permissions.isEmpty()) {
            query.and(
                query.or(
                     query.criteria("include").equal(this.permissionService.getIncludeAllPermission()),
                     query.and(
                         query.criteria("include").equal(this.permissionService.getIncludePermision()),
                         query.criteria("permissions").equal(permissions)
                     ),
                     query.and(
                         query.criteria("include").equal(this.permissionService.getExcludePermission()),
                         query.criteria("permissions").notEqual(permissions)
                     )
                 )
             );
        }

        if (limit != null && limit > -1) {
            query.limit(limit);
        }

        if (offset != null && offset > -1) {
            query.offset(offset);
        }

        List<Permission> entities = query.asList();

        String user_id = claims.get("sub").asText();
        List<Permission> permissionsNotBelongingToRequester = entities.stream()
            .filter(permission -> !permission.user_id.equals(user_id))
            .collect(Collectors.toList());

        boolean hasUserPermission = permissionsNotBelongingToRequester.isEmpty();

        Application application = this.applicationService.read();

        boolean hasApplicationRead = this.permissionService.has(
            user_id,
            Application.class,
            this.applicationService.getReadPermission(),
            application.id
        );

        boolean hasOrganizationPermission;

        Set<ObjectId> organizationIds = entities.stream()
            .filter(permission -> permission.objectClass.equals(this.organizationService.getPermissionObjectClass()))
            .map(permission -> permission.objectId)
            .collect(Collectors.toSet());

        if (organizationIds.size() == entities.size()) {
            hasOrganizationPermission = this.permissionService.hasAllHierarchicalPermission(
                user_id,
                this.organizationService.getReadPermission(),
                organizationIds
            );
        } else {
            hasOrganizationPermission = false;
        }

        if (hasUserPermission || hasOrganizationPermission || hasApplicationRead) {
            Paging<Permission> entity = new Paging<>(entities, query.countAll());
            builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }
}