package com.jivecake.api.resources;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jivecake.api.APIConfiguration;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.Log;
import com.jivecake.api.service.CryptoService;

@CORS
@Singleton
@Path("facebook")
public class FaceBookResource {
    private final APIConfiguration configuration;
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    public FaceBookResource(APIConfiguration configuration) {
        this.configuration = configuration;
    }

    @Log
    @POST
    @Path("messenger/webhook")
    public Response onWebhookPost(
        @NotNull @HeaderParam("X-Hub-Signature") String signature,
        @NotNull String body
    ) throws InvalidKeyException, NoSuchAlgorithmException {
        boolean isValidSHA1 = signature.length() > 4 &&
            CryptoService.isValidSHA1(
                body,
                this.configuration.facebook.secret,
                signature
            );

        ResponseBuilder builder;

        if (isValidSHA1) {
            JsonNode node;

            try {
                node = this.mapper.readTree(body);
            } catch (IOException e) {
                return Response.status(Status.BAD_REQUEST).build();
            }

System.out.println(node);

            builder = Response.ok();
        } else {
            builder = Response.status(Status.FORBIDDEN);
        }

        return builder.build();
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