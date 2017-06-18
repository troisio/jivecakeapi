package com.jivecake.api.resources;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.QueryRestrict;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.Permission;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.OrganizationService;
import com.jivecake.api.service.PermissionService;

@Path("permission")
@CORS
@Singleton
public class PermissionResource {
    private final PermissionService permissionService;
    private final OrganizationService organizationService;
    private final EntityService entityService;
    private final NotificationService notificationService;
    private final Datastore datastore;

    @Inject
    public PermissionResource(
        PermissionService permissionService,
        OrganizationService organizationService,
        EntityService entityService,
        NotificationService notificationService,
        Datastore datastore
    ) {
        this.permissionService = permissionService;
        this.organizationService = organizationService;
        this.entityService = entityService;
        this.notificationService = notificationService;
        this.datastore = datastore;
    }

    @DELETE
    @Authorized
    @QueryRestrict(hasAny=true, target={"user_id", "objectId"})
    public Response delete(
        @QueryParam("user_id") List<String> userIds,
        @QueryParam("objectId") List<ObjectId> objectIds,
        @QueryParam("objectClass") String objectClass,
        @Context DecodedJWT jwt
    ) {
        ResponseBuilder builder;

        boolean hasUserPermission = userIds.size() == 1 && jwt.getSubject().equals(userIds.get(0));
        boolean isOrganizationQuery = this.organizationService.getPermissionObjectClass().equals(objectClass) &&
            !objectIds.isEmpty();
        boolean hasOrganizationPermission;

        if (isOrganizationQuery) {
            hasOrganizationPermission = this.permissionService.hasAllHierarchicalPermission(
                jwt.getSubject(),
                PermissionService.WRITE,
                objectIds
            );
        } else {
            hasOrganizationPermission = false;
        }

        if (hasUserPermission || hasOrganizationPermission) {
            Query<Permission> query = this.datastore.createQuery(Permission.class);

            List<ObjectId> requesterAllPermissions = this.datastore.createQuery(Permission.class)
                .field("user_id").equal(jwt.getSubject())
                .field("objectClass").equal(this.organizationService.getPermissionObjectClass())
                .field("include").equal(PermissionService.ALL)
                .asList()
                .stream()
                .map(permission -> permission.id)
                .collect(Collectors.toList());

            if (!requesterAllPermissions.isEmpty()) {
                query.field("id").notIn(requesterAllPermissions);
            }

            if (!userIds.isEmpty()) {
                query.field("user_id").in(userIds);
            }

            if (objectClass != null) {
                query.field("objectClass").equal(objectClass);
            }

            if (!objectIds.isEmpty()) {
                query.field("objectId").in(objectIds);
            }

            List<Permission> permissions = query.asList();

            this.datastore.delete(query);
            this.notificationService.notify(new ArrayList<>(permissions), "permission.delete");

            List<Organization> organizations = this.datastore.get(Organization.class, objectIds)
                .asList();
            this.entityService.cascadeLastActivity(organizations, new Date());

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
        @QueryParam("permission") Set<Integer> permissions,
        @QueryParam("limit") Integer limit,
        @QueryParam("offset") Integer offset,
        @Context DecodedJWT jwt
     ) {
        ResponseBuilder builder;

        Query<Permission> query = this.datastore.createQuery(Permission.class);

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
                     query.criteria("include").equal(PermissionService.ALL),
                     query.and(
                         query.criteria("include").equal(PermissionService.INCLUDE),
                         query.criteria("permissions").equal(permissions)
                     ),
                     query.and(
                         query.criteria("include").equal(PermissionService.EXCLUDE),
                         query.criteria("permissions").notEqual(permissions)
                     )
                 )
             );
        }

        FindOptions options = new FindOptions();

        if (limit != null && limit > -1) {
            options.limit(limit);
        }

        if (offset != null && offset > -1) {
            options.skip(offset);
        }

        List<Permission> entities = query.asList(options);

        List<Permission> permissionsNotBelongingToRequester = entities.stream()
            .filter(permission -> !permission.user_id.equals(jwt.getSubject()))
            .collect(Collectors.toList());

        boolean hasPermission = permissionsNotBelongingToRequester.isEmpty();

        if (!hasPermission) {
            List<ObjectId> organizationIds = entities.stream()
                .filter(permission -> permission.objectClass.equals(this.organizationService.getPermissionObjectClass()))
                .map(permission -> permission.objectId)
                .collect(Collectors.toList());

            hasPermission = this.permissionService.hasAllHierarchicalPermission(
                jwt.getSubject(),
                PermissionService.READ,
                organizationIds
            );
        }

        if (hasPermission) {
            Paging<Permission> entity = new Paging<>(entities, query.count());
            builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }
}