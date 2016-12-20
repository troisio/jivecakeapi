package com.jivecake.api.filter;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;

@CORS
public class CORSFilter implements ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext context, ContainerResponseContext response) {
        MultivaluedMap<String, Object> headers = response.getHeaders();

        String origin = context.getHeaderString("Origin");

        if (origin != null) {
            headers.remove("Access-Control-Allow-Origin");
            headers.add("Access-Control-Allow-Origin", origin);
        }

        headers.add("Access-Control-Allow-Headers", "*");
        headers.add("Access-Control-Allow-Credentials", "true");
    }
}