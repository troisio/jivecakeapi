package com.jivecake.api.filter;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.NotAllowedException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.NotSupportedException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.server.ParamException.QueryParamException;
import org.mongodb.morphia.Datastore;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.APIConfiguration;
import com.jivecake.api.service.Auth0Service;
import com.jivecake.api.service.MandrillService;

import io.sentry.SentryClient;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.ExceptionInterface;

public class ExceptionMapper implements javax.ws.rs.ext.ExceptionMapper<Exception> {
    private final SentryClient sentry;
    private final Auth0Service auth0Service;
    private final HttpServletRequest request;

    @Inject
    public ExceptionMapper(
        SentryClient sentry,
        Datastore datastore,
        APIConfiguration apiConfiguration,
        Auth0Service auth0Service,
        MandrillService mandrillService,
        HttpServletRequest request
    ) {
        this.sentry = sentry;
        this.auth0Service = auth0Service;
        this.request = request;
    }

    @Override
    public Response toResponse(Exception exception) {
        ResponseBuilder builder;

        if (exception instanceof NotFoundException) {
            builder = Response.status(Status.NOT_FOUND);
        } else if (exception instanceof NotAllowedException) {
            builder = Response.status(Status.METHOD_NOT_ALLOWED);
        } else if (exception instanceof BadRequestException) {
            builder = Response.status(Status.BAD_REQUEST);
        } else if (exception instanceof ForbiddenException) {
            builder = Response.status(Status.FORBIDDEN);
        } else if (exception instanceof NotAuthorizedException) {
            builder = Response.status(Status.UNAUTHORIZED);
        } else if (exception instanceof NotSupportedException) {
            builder = Response.status(Status.UNSUPPORTED_MEDIA_TYPE);
        } else if (exception instanceof NotAcceptableException) {
            builder = Response.status(Status.NOT_ACCEPTABLE);
        } else if (exception instanceof QueryParamException) {
            builder = Response.status(Status.BAD_REQUEST);
        } else {
            EventBuilder eventBuilder = new EventBuilder()
                .withEnvironment(this.sentry.getEnvironment())
                .withMessage(exception.getMessage())
                .withLevel(Event.Level.ERROR)
                .withSentryInterface(new ExceptionInterface(exception));

            String authorization = this.request.getHeader("Authorization");

            if (authorization != null && authorization.startsWith("Bearer ")) {
                try {
                    DecodedJWT jwt = this.auth0Service.getClaimsFromToken(authorization.substring("Bearer ".length()));
                    eventBuilder.withExtra("sub", jwt.getSubject());
                } catch (Exception e) {
                }
            }

            this.sentry.sendEvent(eventBuilder.build());
            builder = Response.status(500);
        }

        return builder.build();
    }
}