package com.jivecake.api.resources;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import com.jivecake.api.filter.CORS;

@CORS
@Path("tool")
public class ToolsResource {
    @GET
    @Path("echo")
    public Response echo(@Context ContainerRequestContext context, @Context HttpServletRequest request) {
        Map<String, Object> entity = new HashMap<>();
        entity.put("headers", context.getHeaders());
        entity.put("ip", request.getRemoteAddr());

        ResponseBuilder builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
        return builder.build();
    }
}
