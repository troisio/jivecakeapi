package com.jivecake.api.filter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;

import org.apache.commons.io.IOUtils;
import org.mongodb.morphia.Datastore;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.model.Request;
import com.jivecake.api.service.Auth0Service;

@Log
public class LogFilter implements ContainerRequestFilter {
    @Context
    private HttpServletRequest request;
    @Context
    private ResourceInfo resourceInfo;
    private final Datastore datastore;
    private final Auth0Service auth0Service;

    @Inject
    public LogFilter(Datastore datastore, Auth0Service auth0Service) {
        this.datastore = datastore;
        this.auth0Service = auth0Service;
    }

    @Override
    public void filter(ContainerRequestContext context) throws IOException {
        Log log = this.resourceInfo.getResourceMethod().getAnnotation(Log.class);

        Request request = new Request();
        URI uri = context.getUriInfo().getRequestUri();
        request.uri = uri.toString();
        request.path = uri.getPath();
        request.ip = this.request.getRemoteAddr();
        request.method = this.request.getMethod();
        request.timeCreated = new Date();

        Map<String, List<String>> headers = context.getHeaders();
        Map<String, List<String>> headersCopy = new HashMap<>();

        Map<String, String[]> query = this.request.getParameterMap();
        Map<String, List<String>> queryCopy = new HashMap<>();

        for (String key: headers.keySet()) {
            headersCopy.put(key.replace('.', ' '), headers.get(key));
        }

        for (String key: query.keySet()) {
            queryCopy.put(key.replace('.', ' '), Arrays.asList(query.get(key)));
        }

        request.headers = headersCopy;
        request.query = queryCopy;

        if (log.body()) {
            String requestEncoding = this.request.getCharacterEncoding();
            String encoding = requestEncoding == null ? "UTF-8" : requestEncoding;

            StringWriter writer = new StringWriter();
            IOUtils.copy(context.getEntityStream(), writer, encoding);
            request.body = writer.toString();
            context.setEntityStream(new ByteArrayInputStream(request.body.getBytes()));
        }

        String authorization = context.getHeaderString("Authorization");

        if (authorization != null && authorization.startsWith("Bearer .")) {
            String token = authorization.substring("Bearer ".length());

            DecodedJWT jwt = this.auth0Service.getClaimsFromToken(token);

            if (jwt != null) {
                request.user_id = jwt.getSubject();
            }
        }

        this.datastore.save(request);
    }
}