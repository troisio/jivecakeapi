package com.jivecake.api.resources;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.Log;

@CORS
@Singleton
@Path("facebook")
public class FaceBookResource {

    @Log
    @POST
    @Path("messenger/webhook")
    public Response onWebhookPost() {
        return Response.ok().build();
    }

    @Log
    @GET
    @Path("messenger/webhook")
    public Response onWebhookGet() {
        return Response.ok().build();
    }
}