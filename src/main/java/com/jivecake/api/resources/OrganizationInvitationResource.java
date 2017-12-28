package com.jivecake.api.resources;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.mongodb.morphia.Datastore;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.OrganizationInvitation;
import com.jivecake.api.model.Permission;
import com.jivecake.api.request.ErrorData;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.PermissionService;

@CORS
@Path("organizationInvitation")
@Singleton
public class OrganizationInvitationResource {
    private final Datastore datastore;
    private final NotificationService notificationService;
    private final PermissionService permissionService;

    @Inject
    public OrganizationInvitationResource(
        Datastore datastore,
        NotificationService notificationService,
        PermissionService permissionService
    ) {
        this.datastore = datastore;
        this.notificationService = notificationService;
        this.permissionService = permissionService;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{id}/accept")
    @Authorized
    public Response acceptInvite(
        @Context DecodedJWT jwt,
        @PathObject("id") OrganizationInvitation invitation
    ) {
        ResponseBuilder builder;

        if (invitation == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            long userIdsMatched = invitation.userIds.stream()
                .filter(userId -> jwt.getSubject().equals(userId))
                .count();

            if (userIdsMatched == 0 || invitation.timeAccepted != null) {
                builder = Response.status(Status.UNAUTHORIZED);
            } else {
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.DATE, 7);
                Date now = new Date();

                if (now.getTime() > calendar.getTimeInMillis()) {
                    ErrorData entity = new ErrorData();
                    entity.error = "expired";
                    builder = Response.status(Status.BAD_REQUEST)
                        .entity(entity)
                        .type(MediaType.APPLICATION_JSON);
                } else {
                    Permission permission = new Permission();
                    permission.read = invitation.read;
                    permission.write = invitation.write;
                    permission.user_id = jwt.getSubject();
                    permission.objectId = invitation.organizationId;
                    permission.objectClass = "Organization";
                    permission.timeCreated = new Date();
                    invitation.timeAccepted = new Date();

                    this.datastore.save(Arrays.asList(permission, invitation));
                    this.notificationService.notify(
                        Arrays.asList(permission),
                        "permission.create"
                    );
                    this.notificationService.notify(
                        Arrays.asList(invitation),
                        "organizationInvitation.update"
                    );

                    builder = Response.ok(permission).type(MediaType.APPLICATION_JSON);
                }
            }
        }

        return builder.build();
    }

    @DELETE
    @Path("{id}")
    @Authorized
    public Response deleteInvite(
        @Context DecodedJWT jwt,
        @PathObject("id") OrganizationInvitation invitation
    ) {
        ResponseBuilder builder;

        if (invitation == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            boolean isInvitedUser = invitation.userIds.contains(jwt.getSubject());
            Organization organization = this.datastore.get(Organization.class, invitation.organizationId);
            boolean hasOrganizationPermission = this.permissionService.hasWrite(
                jwt.getSubject(),
                Arrays.asList(organization)
            );

            if (isInvitedUser || hasOrganizationPermission) {
                this.datastore.delete(invitation);
                this.notificationService.notify(
                    Arrays.asList(invitation),
                    "organizationInvitation.delete"
                );
                builder = Response.ok();
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }
}