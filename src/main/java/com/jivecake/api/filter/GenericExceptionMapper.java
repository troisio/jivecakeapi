package com.jivecake.api.filter;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

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
import javax.ws.rs.ext.ExceptionMapper;

import org.mongodb.morphia.Datastore;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.APIConfiguration;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.Auth0Service;
import com.jivecake.api.service.MandrillService;

public class GenericExceptionMapper implements ExceptionMapper<Exception> {
    private final Datastore datastore;
    private final APIConfiguration apiConfiguration;
    private final Auth0Service auth0Service;
    private final MandrillService mandrillService;
    private final HttpServletRequest request;
    private final ApplicationService applicationService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    public GenericExceptionMapper(
        Datastore datastore,
        APIConfiguration apiConfiguration,
        Auth0Service auth0Service,
        MandrillService mandrillService,
        HttpServletRequest request,
        ApplicationService applicationService
    ) {
        this.datastore = datastore;
        this.apiConfiguration = apiConfiguration;
        this.auth0Service = auth0Service;
        this.mandrillService = mandrillService;
        this.request = request;
        this.applicationService = applicationService;
    }

    @Override
    public Response toResponse(Exception applicationException) {
        applicationException.printStackTrace();

        String authorization = this.request.getHeader("Authorization");

        ResponseBuilder builder;

        if (applicationException instanceof NotFoundException) {
            builder = Response.status(Status.NOT_FOUND);
        } else if (applicationException instanceof NotAllowedException) {
            builder = Response.status(Status.METHOD_NOT_ALLOWED);
        } else if (applicationException instanceof BadRequestException) {
            builder = Response.status(Status.BAD_REQUEST);
        } else if (applicationException instanceof ForbiddenException) {
            builder = Response.status(Status.FORBIDDEN);
        } else if (applicationException instanceof NotAuthorizedException) {
            builder = Response.status(Status.UNAUTHORIZED);
        } else if (applicationException instanceof NotSupportedException) {
            builder = Response.status(Status.UNSUPPORTED_MEDIA_TYPE);
        } else if (applicationException instanceof NotAcceptableException) {
            builder = Response.status(Status.NOT_ACCEPTABLE);
        } else {
            this.executor.execute(() -> {
                long hour = 1000 * 60 * 60;
                Date oneHourEarlier = new Date(new Date().getTime() - hour);

                long errorsInLastHour = this.datastore.createQuery(com.jivecake.api.model.Exception.class)
                    .field("timeCreated").greaterThan(oneHourEarlier)
                    .count();

                if (errorsInLastHour == 0) {
                    List<Map<String, String>> tos = this.apiConfiguration.errorRecipients.stream()
                        .map(recipient -> {
                            Map<String, String> to = new HashMap<>();
                            to.put("email", recipient);
                            to.put("type", "to");
                            return to;
                        })
                        .collect(Collectors.toList());

                    Map<String, Object> message = new HashMap<>();
                    message.put("text", "An exception has been thrown");
                    message.put("subject", "JiveCake Exception");
                    message.put("from_email", "noreply@jivecake.com");
                    message.put("from_name", "JiveCake");
                    message.put("to", tos);

                    this.mandrillService.send(message);
                }

                String userId = null;

                if (authorization != null && authorization.startsWith("Bearer ")) {
                    DecodedJWT jwt = this.auth0Service.getClaimsFromToken(authorization.substring("Bearer ".length()));

                    if (jwt != null) {
                        userId = jwt.getSubject();
                    }
                }

                this.applicationService.saveException(applicationException, userId);
            });

            builder = Response.status(500);
        }

        return builder.build();
    }
}