package com.jivecake.api.filter;

import java.util.List;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;

@CORS
public class CORSFilter implements ContainerResponseFilter {
    private final List<String> allowedOrigins;

    public CORSFilter(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void filter(ContainerRequestContext context, ContainerResponseContext response) {
        MultivaluedMap<String, Object> headers = response.getHeaders();

        String origin = context.getHeaderString("Origin");

        if (this.allowedOrigins.contains(origin)) {
            headers.remove("Access-Control-Allow-Origin");
            headers.add("Access-Control-Allow-Origin", origin);
        }

        headers.add("Access-Control-Allow-Headers", "*");
        headers.add("Access-Control-Allow-Credentials", "true");
    }
}