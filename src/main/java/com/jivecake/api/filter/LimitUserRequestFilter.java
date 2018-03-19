package com.jivecake.api.filter;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.service.Auth0Service;

@LimitUserRequest(count = 0, per = 0)
public class LimitUserRequestFilter implements ContainerRequestFilter {
    @Context
    private HttpServletRequest request;
    @Context
    private ResourceInfo resourceInfo;
    private final HashDateCount count;
    private final Auth0Service auth0Service;

    @Inject
    public LimitUserRequestFilter(Auth0Service auth0Service, HashDateCount count) {
        this.auth0Service = auth0Service;
        this.count = count;
    }

    @Override
    public void filter(ContainerRequestContext context) throws IOException {
        LimitUserRequest limit = this.resourceInfo.getResourceMethod().getAnnotation(LimitUserRequest.class);

        Response aborted = null;

        String errorEntity = String.format(
            "{\"error\": \"limit\", data: {\"count\": %s, \"per\": %s}}",
            limit.count(),
            limit.per()
        );

        if (limit.count() < 1) {
            aborted = Response.status(429).entity(errorEntity).type(MediaType.APPLICATION_JSON).build();
        } else {
            String authorization = context.getHeaderString("Authorization");

            if (authorization != null && authorization.startsWith("Bearer ")) {
                String token = authorization.substring("Bearer ".length());

                DecodedJWT jwt = null;

                try {
                    jwt = this.auth0Service.getDecodedJWT(token);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (jwt != null) {
                    String user_id = jwt.getSubject();

                    URI uri = context.getUriInfo().getRequestUri();

                    String key = String.format("%s|%s", user_id, uri.getPath());
                    this.count.add(key,  new Date());

                    List<Date> dates = this.count.last(key, limit.count());

                    if (dates.size() >= limit.count() && dates.size() > 1) {
                        Date last = dates.get(dates.size() - 1);
                        Date first = dates.get(0);

                        if (last.getTime() - first.getTime() < limit.per()) {
                            aborted = Response.status(429).build();
                        }

                        this.count.limitToLast(key, limit.count());
                    } else if (limit.count() < 1) {
                        aborted = Response.status(429).entity(errorEntity).type(MediaType.APPLICATION_JSON).build();
                    }
                }
            }
        }

        if (aborted != null) {
            context.abortWith(aborted);
        }
    }
}