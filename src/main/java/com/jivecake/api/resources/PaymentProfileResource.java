package com.jivecake.api.resources;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;
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
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.PaymentProfileService;
import com.jivecake.api.service.PermissionService;

@Path("/payment/profile")
@CORS
public class PaymentProfileResource {
    private final PaymentProfileService paymentProfileService;
    private final EventService eventService;
    private final PermissionService permissionService;

    @Inject
    public PaymentProfileResource(
        EventService eventService,
        PaymentProfileService paymentProfileService,
        PermissionService permissionService
    ) {
        this.paymentProfileService = paymentProfileService;
        this.eventService = eventService;
        this.permissionService = permissionService;
    }

    @GET
    @Authorized
    @Path("/{id}")
    @HasPermission(clazz=PaymentProfile.class, id="id", permission=PermissionService.READ)
    public Response readPaymentProfile(@PathObject(value="id") PaymentProfile profile, @Context JsonNode claims) {
        return Response.ok(profile).type(MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("/search")
    public Response search(
        @QueryParam("id") List<ObjectId> ids
    ) {
        Query<PaymentProfile> query = this.paymentProfileService.query();

        if (!ids.isEmpty()) {
            query.field("id").in(ids);
        }

        Paging<PaymentProfile> paging = new Paging<>(query.asList(), query.count());

        ResponseBuilder builder = Response.ok(paging).type(MediaType.APPLICATION_JSON);
        return builder.build();
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
            Query<PaymentProfile> query = this.paymentProfileService.query().field("organizationId").in(organizationIds);

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

            List<PaymentProfile> profiles = query.asList();
            Paging<PaymentProfile> paging = new Paging<>(profiles, query.count());

            builder = Response.ok(paging).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @DELETE
    @Authorized
    @Path("/{id}")
    @HasPermission(clazz=PaymentProfile.class, id="id", permission=PermissionService.WRITE)
    public Response delete(@PathObject("id") PaymentProfile profile, @Context JsonNode claims) {
        ResponseBuilder builder;

        List<Event> eventsUsingProfile = this.eventService.query()
            .field("paymentProfileId")
            .equal(profile.id)
            .asList();

        if (eventsUsingProfile.isEmpty()) {
            this.paymentProfileService.delete(profile.id);
            builder = Response.ok();
        } else {
            builder = Response.status(Status.BAD_REQUEST)
                .entity(eventsUsingProfile)
                .type(MediaType.APPLICATION_JSON);
        }

        return builder.build();
    }
}