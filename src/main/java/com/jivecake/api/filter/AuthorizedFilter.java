package com.jivecake.api.filter;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.auth0.jwt.JWTVerifier;

@Authorized
public class AuthorizedFilter implements ContainerRequestFilter {
    private final List<JWTVerifier> verifiers;

    @Inject
    public AuthorizedFilter(List<JWTVerifier> verifiers) {
        this.verifiers = verifiers;
    }

    @Override
    public void filter(ContainerRequestContext context) {
        Date date = new Date();
        String header = context.getHeaderString("Authorization");

        Response aborted = null;

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());

            Map<String, Object> decoded = null;

            for (JWTVerifier verifier: this.verifiers) {
                try {
                    decoded = verifier.verify(token);
                    break;
                } catch (Exception e) {
                }
            }

            if (decoded == null) {
                aborted = Response.status(Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"error\": \"invalid_grant\"}")
                    .build();
            } else {
                Object exp = decoded.get("exp");

                if (exp != null && exp instanceof Integer) {
                    long expiration = Integer.toUnsignedLong((Integer)exp) * 1000;

                    if (date.after(new Date(expiration))) {
                        aborted = Response.status(Status.BAD_REQUEST)
                            .type(MediaType.APPLICATION_JSON)
                            .entity("{\"error\": \"invalid_grant\", \"error_description\": \"exp\"}")
                            .build();
                    }
                }
            }
        } else {
            aborted = Response.status(Status.BAD_REQUEST).build();
        }

        if (aborted != null) {
            context.abortWith(aborted);
        }
    }
}