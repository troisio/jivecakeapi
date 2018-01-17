package com.jivecake.api.resources;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.SseFeature;

import com.auth0.jwk.JwkException;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.service.Auth0Service;
import com.jivecake.api.service.ClientConnectionService;

@CORS
@Path("notification")
@Singleton
public class NotificationsResource {
    private final Auth0Service auth0Service;
    private final ClientConnectionService clientConnectionService;

    @Inject
    public NotificationsResource(
        Auth0Service auth0Service,
        ClientConnectionService clientConnectionService
    ) {
        this.auth0Service = auth0Service;
        this.clientConnectionService = clientConnectionService;
    }

    @GET
    @Produces(SseFeature.SERVER_SENT_EVENTS)
    public Response subscribe(
        @QueryParam("Authorization") String token
    ) {
        ResponseBuilder builder;

        if (token == null || !token.startsWith("Bearer ")) {
            builder = Response.status(Status.BAD_REQUEST);
        } else {
            DecodedJWT jwt = null;

            try {
                jwt = this.auth0Service.getClaimsFromToken(token.substring("Bearer ".length()));
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (jwt == null) {
                builder = Response.status(Status.UNAUTHORIZED);
            } else {
                EventOutput output = this.clientConnectionService.getEventOutput(jwt.getSubject());
                builder = Response.ok(output);
            }
        }

        return builder.build();
    }
}