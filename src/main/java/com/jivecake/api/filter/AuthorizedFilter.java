package com.jivecake.api.filter;

import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.interfaces.DecodedJWT;

@Authorized
public class AuthorizedFilter implements ContainerRequestFilter {
    private final List<JWTVerifier> verifiers;

    @Inject
    public AuthorizedFilter(List<JWTVerifier> verifiers) {
        this.verifiers = verifiers;
    }

    @Override
    public void filter(ContainerRequestContext context) {
        String header = context.getHeaderString("Authorization");

        Response aborted = null;

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());

            DecodedJWT decoded = null;

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
                Date exp = decoded.getExpiresAt();

                if (new Date().after(exp)) {
                    aborted = Response.status(Status.BAD_REQUEST)
                        .type(MediaType.APPLICATION_JSON)
                        .entity("{\"error\": \"invalid_grant\", \"error_description\": \"exp\"}")
                        .build();
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