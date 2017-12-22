package com.jivecake.api.filter;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.auth0.jwk.JwkException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.service.Auth0Service;

@Authorized
public class AuthorizedFilter implements ContainerRequestFilter {
    private final Auth0Service auth0Service;

    @Inject
    public AuthorizedFilter(Auth0Service auth0Service) {
        this.auth0Service = auth0Service;
    }

    @Override
    public void filter(ContainerRequestContext context) {
        String header = context.getHeaderString("Authorization");

        Response aborted = null;

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            DecodedJWT decoded = null;

            try {
                decoded = this.auth0Service.getClaimsFromToken(token);
            } catch (JwkException e) {
            }

            if (decoded == null) {
                aborted = Response.status(Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"error\": \"invalid_grant\"}")
                    .build();
            }
        } else {
            aborted = Response.status(Status.BAD_REQUEST).build();
        }

        if (aborted != null) {
            context.abortWith(aborted);
        }
    }
}