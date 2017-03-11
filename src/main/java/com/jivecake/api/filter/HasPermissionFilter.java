package com.jivecake.api.filter;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;

import com.jivecake.api.service.Auth0Service;
import com.jivecake.api.service.PermissionService;

@HasPermission(clazz=HasPermission.class, id="", permission=0)
public class HasPermissionFilter implements ContainerRequestFilter {
    @Context
    private ResourceInfo resourceInfo;
    private final Auth0Service auth0Service;
    private final PermissionService permissionService;
    private final Datastore datastore;

    @Inject
    public HasPermissionFilter(Auth0Service auth0Service, PermissionService permissionService, Datastore datastore) {
        this.auth0Service = auth0Service;
        this.permissionService = permissionService;
        this.datastore = datastore;
    }

    @Override
    public void filter(ContainerRequestContext context) {
        Map<String, List<String>> parameters = context.getUriInfo().getPathParameters();
        HasPermission annotation = this.resourceInfo.getResourceMethod().getAnnotation(HasPermission.class);

        Response response;

        String header = context.getHeaderString("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());

            ObjectId objectId;

            try {
                List<String> value = parameters.get(annotation.id());
                objectId = new ObjectId(value.get(0));
            } catch (IllegalArgumentException e) {
                objectId = null;
            }

            if (objectId == null) {
                response = Response.status(Status.BAD_REQUEST).build();
            } else {
                Collection<?> entities = this.datastore.find(annotation.clazz()).field("id").equal(objectId).asList();

                if (!entities.isEmpty()) {
                    Map<String, Object> claims = this.auth0Service.getClaimsFromToken(token);
                    boolean hasPermission = this.permissionService.has((String)claims.get("sub"), entities, annotation.permission());

                    if (hasPermission) {
                        response = null;
                    } else {
                        response = Response.status(Status.UNAUTHORIZED).build();
                    }
                } else {
                    response = Response.status(Status.NOT_FOUND).build();
                }
            }
        } else {
            response = Response.status(Status.UNAUTHORIZED).build();
        }

        if (response != null) {
            context.abortWith(response);
        }
    }
}