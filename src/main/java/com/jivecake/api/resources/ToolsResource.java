package com.jivecake.api.resources;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;

import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.model.Transaction;

@CORS
@Path("tool")
public class ToolsResource {
    private final Datastore datastore;

    @Inject
    public ToolsResource(Datastore datastore) {
        this.datastore = datastore;
    }

    @GET
    @Path("echo")
    @Authorized
    public Response echo(@Context ContainerRequestContext context, @Context HttpServletRequest request) {
        Map<String, Object> entity = new HashMap<>();
        entity.put("headers", context.getHeaders());
        entity.put("ip", request.getRemoteAddr());
        return Response.ok(entity).type(MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("db")
    @Authorized
    public Response db() {
        Long start = System.currentTimeMillis();
        this.datastore.get(Transaction.class, new ObjectId());
        Long end = System.currentTimeMillis();
        return Response.ok(end - start).type(MediaType.APPLICATION_JSON).build();
    }
}
