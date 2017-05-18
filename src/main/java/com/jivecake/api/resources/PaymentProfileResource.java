package com.jivecake.api.resources;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;

import com.fasterxml.jackson.databind.JsonNode;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.HasPermission;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.filter.QueryRestrict;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.PaymentProfile;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.PermissionService;

@Path("payment/profile")
@CORS
@Singleton
public class PaymentProfileResource {
    private final PermissionService permissionService;
    private final EntityService entityService;
    private final NotificationService notificationService;
    private final Datastore datastore;

    @Inject
    public PaymentProfileResource(
        PermissionService permissionService,
        EntityService entityService,
        NotificationService notificationService,
        Datastore datastore
    ) {
        this.permissionService = permissionService;
        this.entityService = entityService;
        this.notificationService = notificationService;
        this.datastore = datastore;
    }

    @GET
    @Authorized
    @Path("{id}")
    @HasPermission(clazz=PaymentProfile.class, id="id", permission=PermissionService.READ)
    public Response readPaymentProfile(@PathObject(value="id") PaymentProfile profile, @Context JsonNode claims) {
        return Response.ok(profile).type(MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("search")
    public Response search(@QueryParam("id") List<ObjectId> ids) {
        Query<PaymentProfile> query = this.datastore.createQuery(PaymentProfile.class)
            .field("id").in(ids);

        FindOptions options = new FindOptions();
        options.limit(100);

        Paging<PaymentProfile> paging = new Paging<>(query.asList(options), query.count());
        return Response.ok(paging).type(MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Authorized
    @QueryRestrict(hasAny=true, target={"organizationId"})
    public Response search(
        @QueryParam("organizationId") Set<ObjectId> organizationIds,
        @QueryParam("name") String name,
        @QueryParam("email") String email,
        @QueryParam("limit") Integer limit,
        @QueryParam("offset") Integer offset,
        @Context JsonNode claims
    ) {
        ResponseBuilder builder;

        boolean hasPermission = this.permissionService.hasAllHierarchicalPermission(
            claims.get("sub").asText(),
            PermissionService.READ,
            organizationIds
        );

        if (hasPermission) {
            Query<PaymentProfile> query = this.datastore.createQuery(PaymentProfile.class)
                .field("organizationId").in(organizationIds);

            if (name != null) {
                query.field("name").startsWithIgnoreCase(name);
            }

            if (email != null) {
                query.field("email").startsWithIgnoreCase(email);
            }

            FindOptions options = new FindOptions();

            if (limit != null && limit > -1) {
                options.limit(limit);
            }

            if (offset != null && offset > -1) {
                options.skip(offset);
            }

            List<PaymentProfile> profiles = query.asList(options);
            Paging<PaymentProfile> paging = new Paging<>(profiles, query.count());

            builder = Response.ok(paging).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @DELETE
    @Authorized
    @Path("{id}")
    @HasPermission(clazz=PaymentProfile.class, id="id", permission=PermissionService.WRITE)
    public Response delete(@PathObject("id") PaymentProfile profile, @Context JsonNode claims) {
        ResponseBuilder builder;

        long count = this.datastore.createQuery(Event.class)
            .field("paymentProfileId").equal(profile.id)
            .count();

        if (count == 0) {
            this.datastore.delete(PaymentProfile.class, profile.id);
            this.entityService.cascadeLastActivity(Arrays.asList(profile), new Date());
            this.notificationService.notify(Arrays.asList(profile), "paymentprofile.delete");
            builder = Response.ok();
        } else {
            Map<String, Object> entity = new HashMap<>();
            entity.put("error", "event");
            entity.put("data", count);

            builder = Response.status(Status.BAD_REQUEST)
                .entity(entity)
                .type(MediaType.APPLICATION_JSON);
        }

        return builder.build();
    }
}