package com.jivecake.api.resources;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.GZip;
import com.jivecake.api.model.Application;
import com.jivecake.api.model.Request;
import com.jivecake.api.model.UserInterfaceEvent;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.PermissionService;

@Path("log")
@CORS
@Singleton
public class LogResource {
    private final ApplicationService applicationService;
    private final PermissionService permissionService;
    private final Datastore datastore;

    @Inject
    public LogResource(
        ApplicationService applicationService,
        PermissionService permissionService,
        Datastore datastore
    ) {
        this.applicationService = applicationService;
        this.permissionService = permissionService;
        this.datastore = datastore;
    }

    @GZip
    @GET
    @Path("http")
    @Authorized
    public Response query(
        @QueryParam("limit") Integer limit,
        @QueryParam("offset") Integer offset,
        @QueryParam("order") String order,
        @QueryParam("path") String path,
        @QueryParam("ip") String ip,
        @QueryParam("user_id") List<String> userIds,
        @QueryParam("timeCreatedLessThan") Long timeCreatedLessThan,
        @QueryParam("timeCreatedGreaterThan") Long timeCreatedGreaterThan,
        @Context DecodedJWT jwt
    ) {
        Application application = this.applicationService.read();

        ResponseBuilder builder;

        boolean hasPermission = this.permissionService.has(
            jwt.getSubject(),
            Arrays.asList(application),
            PermissionService.READ
        );

        if (hasPermission) {
            Query<Request> query = this.datastore.createQuery(Request.class);

            if (!userIds.isEmpty()) {
                query.field("user_id").in(userIds);
            }

            if (timeCreatedGreaterThan != null) {
                query.field("timeCreated").greaterThan(new Date(timeCreatedGreaterThan));
            }

            if (timeCreatedLessThan != null) {
                query.field("timeCreated").lessThan(new Date(timeCreatedLessThan));
            }

            if (path != null) {
                query.field("path").equal(path);
            }

            if (ip != null) {
                query.field("ip").equal(ip);
            }

            if (order != null) {
                query.order(order);
            }

            FindOptions options = new FindOptions();
            options.limit(ApplicationService.LIMIT_DEFAULT);

            if (offset != null && offset > -1) {
                options.skip(offset);
            }

            Paging<Request> entity = new Paging<>(query.asList(options), query.count());
            builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @POST
    @Path("ui")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createUserInterfaceEvent(
        @HeaderParam("User-Agent") String agent,
        @Context HttpServletRequest request,
        @Context DecodedJWT jwt,
        UserInterfaceEvent event
    ) {
        event.id = null;
        event.agent = agent;
        event.ip = request.getRemoteAddr();
        event.timeCreated = new Date();

        if (jwt != null) {
            event.userId = jwt.getSubject();
        }

        this.datastore.save(event);
        return Response.ok().build();
    }

    @GZip
    @GET
    @Path("ui")
    @Authorized
    public Response searchUIInteractions(
        @QueryParam("limit") Integer limit,
        @QueryParam("offset") Integer offset,
        @QueryParam("ip") String ip,
        @QueryParam("order") String order,
        @QueryParam("userId") String userId,
        @QueryParam("event") String event,
        @QueryParam("timeCreatedLessThan") Long timeCreatedLessThan,
        @QueryParam("timeCreatedGreaterThan") Long timeCreatedGreaterThan,
        @Context DecodedJWT jwt
    ) {
        Application application = this.applicationService.read();

        ResponseBuilder builder;

        boolean hasPermission = this.permissionService.has(
            jwt.getSubject(),
            Arrays.asList(application),
            PermissionService.READ
        );

        if (hasPermission) {
            Query<UserInterfaceEvent> query = this.datastore.createQuery(UserInterfaceEvent.class);

            if (userId != null) {
                query.field("userId").equal(userId);
            }

            if (event != null) {
                query.field("event").equal(event);
            }

            if (ip != null) {
                query.field("ip").equal(ip);
            }

            if (timeCreatedGreaterThan != null) {
                query.field("timeCreated").greaterThan(new Date(timeCreatedGreaterThan));
            }

            if (timeCreatedLessThan != null) {
                query.field("timeCreated").lessThan(new Date(timeCreatedLessThan));
            }

            if (order != null) {
                query.order(order);
            }

            FindOptions options = new FindOptions();
            options.limit(ApplicationService.LIMIT_DEFAULT);

            if (offset != null && offset > -1) {
                options.skip(offset);
            }

            Paging<UserInterfaceEvent> entity = new Paging<>(query.asList(options), query.count());
            builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }
}