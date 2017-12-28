package com.jivecake.api.resources;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

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
import com.jivecake.api.filter.HasPermission;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.Permission;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.PermissionService;

@Path("permission")
@CORS
@Singleton
public class PermissionResource {
    private final PermissionService permissionService;
    private final EntityService entityService;
    private final NotificationService notificationService;
    private final Datastore datastore;

    @Inject
    public PermissionResource(
        PermissionService permissionService,
        EntityService entityService,
        NotificationService notificationService,
        Datastore datastore
    ) {
        this.permissionService = permissionService;
        this.entityService = entityService;
        this.notificationService = notificationService;
        this.datastore = datastore;
    }

    @DELETE
    @Authorized
    @Path("{id}")
    @HasPermission(clazz=Permission.class, id="id", write=true)
    public Response delete(
        @PathObject("id") Permission permission,
        @Context DecodedJWT jwt
    ) {
        boolean deletesPermissionFromOrganizationCreator = false;

        if ("Organization".equals(permission.objectClass)) {
            Organization organization = this.datastore.get(Organization.class, permission.objectId);
            deletesPermissionFromOrganizationCreator = jwt.getSubject().equals(organization.createdBy);
        }

        ResponseBuilder builder;

        if (deletesPermissionFromOrganizationCreator) {
            builder = Response.status(Status.UNAUTHORIZED);
        } else {
            this.datastore.delete(permission);

            this.entityService.cascadeLastActivity(
                Arrays.asList(permission),
                new Date()
            );
            this.notificationService.notify(
                Arrays.asList(permission),
                "permission.delete"
            );

            builder = Response.ok();
        }

        return builder.build();
    }

    @GET
    @Authorized
    public Response search(
        @QueryParam("user_id") String userId,
        @QueryParam("objectId") ObjectId objectId,
        @QueryParam("objectClass") String objectClass,
        @Context DecodedJWT jwt
     ) {
        ResponseBuilder builder;

        Query<Permission> query = this.datastore.createQuery(Permission.class);

        if (userId != null) {
            query.field("user_id").equal(userId);
        }

        if (objectId != null) {
            query.field("objectId").equal(objectId);
        }

        if (objectClass != null) {
            query.field("objectClass").equal(objectClass);
        }

        FindOptions options = new FindOptions();
        options.limit(ApplicationService.LIMIT_DEFAULT);

        boolean hasUserPermission = jwt.getSubject().equals(userId);
        boolean hasOrganizationPermission = false;

        if ("Organization".equals(objectClass)) {
            Organization organization = this.datastore.get(Organization.class, objectId);

            if (organization != null) {
                hasOrganizationPermission = this.permissionService.hasRead(
                    jwt.getSubject(),
                    Arrays.asList(organization)
                );
            }
        }

        boolean hasPermission = hasUserPermission || hasOrganizationPermission;

        if (hasPermission) {
            List<Permission> entities = query.asList(options);
            Paging<Permission> entity = new Paging<>(entities, query.count());
            builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }
}