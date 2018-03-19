package com.jivecake.api.filter;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.request.ErrorData;
import com.jivecake.api.service.Auth0Service;

import io.sentry.SentryClient;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.ExceptionInterface;

@Authorized
public class AuthorizedFilter implements ContainerRequestFilter {
    private final SentryClient sentry;
    private final Auth0Service auth0Service;

    @Inject
    public AuthorizedFilter(SentryClient sentry, Auth0Service auth0Service) {
        this.sentry = sentry;
        this.auth0Service = auth0Service;
    }

    @Override
    public void filter(ContainerRequestContext context) {
        String header = context.getHeaderString("Authorization");

        Response aborted = null;

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            DecodedJWT decoded = null;

            boolean isJWT = token.split("\\.").length == 3;

            if (isJWT) {
                try {
                    decoded = this.auth0Service.getDecodedJWT(token);
                } catch (Exception e) {
                    this.sentry.sendEvent(
                        new EventBuilder()
                            .withEnvironment(this.sentry.getEnvironment())
                            .withMessage(e.getMessage())
                            .withLevel(Event.Level.WARNING)
                            .withSentryInterface(new ExceptionInterface(e))
                            .build()
                    );
                }

                if (decoded == null) {
                    ErrorData errorData = new ErrorData();
                    errorData.error = "invalid_grant";
                    aborted = Response.status(Status.UNAUTHORIZED)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(errorData)
                        .build();
                }
            } else {
                ErrorData errorData = new ErrorData();
                errorData.error = "invalid_grant";
                errorData.data = "not a jwt";
                aborted = Response.status(Status.UNAUTHORIZED)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(errorData)
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