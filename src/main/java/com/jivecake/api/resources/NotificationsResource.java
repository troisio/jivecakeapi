package com.jivecake.api.resources;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.glassfish.jersey.media.sse.SseFeature;

import com.fasterxml.jackson.databind.JsonNode;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.model.Application;
import com.jivecake.api.request.ServerSentEvent;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.Auth0Service;
import com.jivecake.api.service.ClientConnectionService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.PermissionService;

@CORS
@Path("notification")
@Singleton
public class NotificationsResource {
    private final Auth0Service auth0Service;
    private final ClientConnectionService clientConnectionService;
    private final ApplicationService applicationService;
    private final PermissionService permissionService;
    private final NotificationService notificationService;

    @Inject
    public NotificationsResource(
        Auth0Service auth0Service,
        ClientConnectionService clientConnectionService,
        ApplicationService applicationService,
        PermissionService permissionService,
        NotificationService notificationService
    ) {
        this.auth0Service = auth0Service;
        this.clientConnectionService = clientConnectionService;
        this.applicationService = applicationService;
        this.permissionService = permissionService;
        this.notificationService = notificationService;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    public Response sendEvent(
        @QueryParam("user_id") Set<String> userIds,
        @Context JsonNode claims,
        ServerSentEvent event
    ) {
        Application application = this.applicationService.read();

        ResponseBuilder builder;

        boolean hasPermission = this.permissionService.has(
            claims.get("sub").asText(),
            Arrays.asList(application),
            PermissionService.WRITE
        );

        if (hasPermission) {
            OutboundEvent.Builder eventBuilder = new OutboundEvent.Builder();

            if (event.data != null) {
                eventBuilder.mediaType(MediaType.APPLICATION_JSON_TYPE).data(event.data);
            }

            if (event.comment != null) {
                eventBuilder.comment(event.comment);
            }

            if (event.name != null) {
                eventBuilder.name(event.name);
            }

            if (event.id != null) {
                eventBuilder.id(event.id);
            }

            this.notificationService.sendEvent(userIds, eventBuilder.build());

            builder = Response.ok();
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @GET
    @Produces(SseFeature.SERVER_SENT_EVENTS)
    public Response getServerSentEvents(
        @QueryParam("Authorization") String token
    ) {
        ResponseBuilder builder;

        if (token == null || !token.startsWith("Bearer ")) {
            builder = Response.status(Status.BAD_REQUEST);
        } else {
            Map<String, Object> claims = this.auth0Service.getClaimsFromToken(token.substring("Bearer ".length()));

            if (claims == null) {
                builder = Response.status(Status.UNAUTHORIZED);
            } else {
                String user_id = (String)claims.get("sub");
                EventOutput output = this.clientConnectionService.getEventOutput(user_id);
                builder = Response.ok(output);
            }
        }

        return builder.build();
    }
}