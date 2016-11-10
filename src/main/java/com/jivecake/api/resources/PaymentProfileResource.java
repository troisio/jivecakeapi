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
import org.mongodb.morphia.query.Query;

import com.fasterxml.jackson.databind.JsonNode;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.filter.QueryRestrict;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.PaymentProfile;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.OrganizationService;
import com.jivecake.api.service.PaymentProfileService;
import com.jivecake.api.service.PermissionService;

@Path("/payment/profile")
@CORS
public class PaymentProfileResource {
    private final PaymentProfileService paymentProfileService;
    private final OrganizationService organizationService;
    private final EventService eventService;
    private final PermissionService permissionService;

    @Inject
    public PaymentProfileResource(
        OrganizationService organizationService,
        EventService eventService,
        PaymentProfileService paymentProfileService,
        PermissionService permissionService
    ) {
        this.paymentProfileService = paymentProfileService;
        this.organizationService = organizationService;
        this.eventService = eventService;
        this.permissionService = permissionService;
    }

    @GET
    @Authorized
    @Path("/{id}")
    public Response readPaymentProfile(@PathObject(value="id") PaymentProfile profile, @Context JsonNode claims) {
        ResponseBuilder builder;

        if (profile == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            boolean hasPermission = this.permissionService.hasAnyHierarchicalPermission(
                profile.organizationId,
                claims.get("sub").asText(),
                this.organizationService.getReadPermission()
            );

            if (hasPermission) {
                builder = Response.ok(profile).type(MediaType.APPLICATION_JSON);
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }

    @GET
    @Path("/search")
    @QueryRestrict(hasAny=true, target={"id"})
    public Response search(
        @QueryParam("id") List<ObjectId> ids
    ) {
        Query<PaymentProfile> query = this.paymentProfileService.query();

        if (!ids.isEmpty()) {
            query.field("id").in(ids);
        }

        Paging<PaymentProfile> paging = new Paging<>(query.asList(), query.countAll());

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
            this.organizationService.getReadPermission(),
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

            if (limit != null && limit > -1) {
                query.limit(limit);
            }

            if (offset != null && offset > -1) {
                query.offset(offset);
            }

            List<PaymentProfile> profiles = query.asList();
            Paging<PaymentProfile> paging = new Paging<>(profiles, query.countAll());

            builder = Response.ok(paging).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @DELETE
    @Authorized
    @Path("/{id}")
    public Response delete(@PathObject("id") PaymentProfile profile, @Context JsonNode claims) {
        ResponseBuilder builder;

        if (profile == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            boolean hasPermission = this.permissionService.hasAnyHierarchicalPermission(
                profile.organizationId,
                claims.get("sub").asText(),
                this.organizationService.getWritePermission()
            );

            if (hasPermission) {
                List<Event> eventsUsingProfile = this.eventService.query()
                    .field("paymentProfileId")
                    .equal(profile.id)
                    .asList();

                if (eventsUsingProfile.isEmpty()) {
                    this.paymentProfileService.delete(profile.id);
                    builder = Response.ok().type(MediaType.APPLICATION_JSON);
                } else {
                    builder = Response.status(Status.BAD_REQUEST).entity(eventsUsingProfile).type(MediaType.APPLICATION_JSON);
                }
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }
}