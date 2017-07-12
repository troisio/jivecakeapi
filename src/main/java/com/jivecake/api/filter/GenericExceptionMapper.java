package com.jivecake.api.filter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

import org.mongodb.morphia.Datastore;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.APIConfiguration;
import com.jivecake.api.service.Auth0Service;
import com.jivecake.api.service.MandrillService;

public class GenericExceptionMapper implements ExceptionMapper<Exception> {
    private final Datastore datastore;
    private final APIConfiguration apiConfiguration;
    private final Auth0Service auth0Service;
    private final MandrillService mandrillService;
    private final HttpServletRequest request;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    public GenericExceptionMapper(
        Datastore datastore,
        APIConfiguration apiConfiguration,
        Auth0Service auth0Service,
        MandrillService mandrillService,
        HttpServletRequest request
    ) {
        this.datastore = datastore;
        this.apiConfiguration = apiConfiguration;
        this.auth0Service = auth0Service;
        this.mandrillService = mandrillService;
        this.request = request;
    }

    @Override
    public Response toResponse(Exception applicationException) {
        String authorization = this.request.getHeader("Authorization");

        ResponseBuilder builder;

        if (applicationException instanceof NotFoundException) {
            builder = Response.status(Status.NOT_FOUND);
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

                StringWriter writer = new StringWriter();
                applicationException.printStackTrace(new PrintWriter(writer));
                com.jivecake.api.model.Exception exception = new com.jivecake.api.model.Exception();
                exception.stackTrace = writer.toString();
                exception.timeCreated = new Date();

                if (authorization != null && authorization.startsWith("Bearer ")) {
                    DecodedJWT jwt = this.auth0Service.getClaimsFromToken(authorization.substring("Bearer ".length()));

                    if (jwt != null) {
                        exception.userId = jwt.getSubject();
                    }
                }

                this.datastore.save(exception);
            });

            builder = Response.status(500);
        }

        return builder.build();
    }
}