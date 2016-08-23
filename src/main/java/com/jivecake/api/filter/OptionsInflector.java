package com.jivecake.api.filter;

import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.glassfish.jersey.process.Inflector;

public class OptionsInflector implements Inflector<ContainerRequestContext, Response> {
    private final String methods;

    public OptionsInflector(Set<String> methods) {
        this.methods = methods.stream().collect(Collectors.joining(", "));
    }

    @Override
    public Response apply(ContainerRequestContext context) {
        String origin = context.getHeaderString("Origin");
        String allowedHeaders = context.getHeaderString("Access-Control-Request-Headers");

        ResponseBuilder builder = Response.ok()
            .header("Access-Control-Allow-Methods", this.methods)
            .header("Access-Control-Allow-Credentials", "true")
            .header("Access-Control-Expose-Headers", "*");

        if (origin != null) {
            builder.header("Access-Control-Allow-Origin", origin);
        }

        if (allowedHeaders != null) {
            builder.header("Access-Control-Allow-Headers", allowedHeaders);
        }

        return builder.build();
    }
}