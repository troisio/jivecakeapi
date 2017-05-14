package com.jivecake.api.resources;

import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import com.fasterxml.jackson.databind.JsonNode;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.model.Application;
import com.jivecake.api.model.EventBroadcaster;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.ClientConnectionService;
import com.jivecake.api.service.PermissionService;

@CORS
@Path("connection")
@Singleton
public class ConnectionResource {
    private final ClientConnectionService clientConnectionService;
    private final ApplicationService applicationService;
    private final PermissionService permissionService;

    @Inject
    public ConnectionResource(
        ClientConnectionService clientConnectionService,
        ApplicationService applicationService,
        PermissionService permissionService
    ) {
        this.clientConnectionService = clientConnectionService;
        this.applicationService = applicationService;
        this.permissionService = permissionService;
    }

    @GET
    @Path("sse")
    @Authorized
    public Response getServerSentBroadcasters(
        @QueryParam("user_id") Set<String> user_ids,
        @QueryParam("timeCreatedAfter") Long timeCreatedAfter,
        @QueryParam("timeCreatedBefore") Long timeCreatedBefore,
        @Context JsonNode claims
    ) {
        ResponseBuilder builder;
        Application application = this.applicationService.read();

        boolean hasPermission = this.permissionService.has(
            claims.get("sub").asText(),
            Arrays.asList(application),
            PermissionService.READ
        );

        if (hasPermission) {
            Stream<EventBroadcaster> stream = this.clientConnectionService.broadcasters.values().stream();

            if (!user_ids.isEmpty()) {
                stream = stream.filter(broadcaster -> user_ids.contains(broadcaster.user_id));
            }

            if (timeCreatedAfter != null) {
                stream = stream.filter(broadcaster -> broadcaster.timeCreated.after(new Date(timeCreatedAfter)));
            }

            if (timeCreatedBefore != null) {
                stream = stream.filter(broadcaster -> broadcaster.timeCreated.before(new Date(timeCreatedBefore)));
            }

            Paging<EventBroadcaster> entity = new Paging<>(
                stream.collect(Collectors.toList()),
                this.clientConnectionService.broadcasters.size()
            );
            builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }
}
