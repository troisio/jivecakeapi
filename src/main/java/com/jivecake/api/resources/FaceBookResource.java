package com.jivecake.api.resources;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import com.jivecake.api.APIConfiguration;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.Log;

@CORS
@Singleton
@Path("facebook")
public class FaceBookResource {
    private final APIConfiguration configuration;

    @Inject
    public FaceBookResource(APIConfiguration configuration) {
        this.configuration = configuration;
    }

    @Log
    @POST
    @Path("messenger/webhook")
    public Response onWebhookPost() {
        return Response.ok().build();
    }

    @GET
    @Path("messenger/webhook")
    public Response onWebhookGet(
        @QueryParam("hub.mode") String mode,
        @QueryParam("hub.verify_token") String verifyToken,
        @QueryParam("hub.challenge") String challenge
    ) {
        boolean valid = "subscribe".equals(mode) &&
            this.configuration.facebook.messengerWebhookVerifyToken.equals(verifyToken);
        ResponseBuilder builder = valid ? Response.ok(challenge) : Response.status(Status.FORBIDDEN);
        return builder.build();
    }
}