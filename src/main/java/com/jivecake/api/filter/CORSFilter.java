package com.jivecake.api.filter;

import java.util.Arrays;
import java.util.List;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;

@CORS
public class CORSFilter implements ContainerResponseFilter {
    private static final List<String> allowedOrigins = Arrays.asList(
        "http://127.0.0.1",
        "https://jivecake",
        "http://jivecake.com"
    );

    @Override
    public void filter(ContainerRequestContext context, ContainerResponseContext response) {
        MultivaluedMap<String, Object> headers = response.getHeaders();

        String origin = context.getHeaderString("Origin");

        if (CORSFilter.allowedOrigins.contains(origin)) {
            headers.remove("Access-Control-Allow-Origin");
            headers.add("Access-Control-Allow-Origin", origin);
        }

        headers.add("Access-Control-Allow-Headers", "*");
        headers.add("Access-Control-Allow-Credentials", "true");
    }
}