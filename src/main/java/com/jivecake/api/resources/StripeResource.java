package com.jivecake.api.resources;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.HasPermission;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.model.Organization;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.PermissionService;
import com.jivecake.api.service.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;

@CORS
@Path("stripe")
@Singleton
public class StripeResource {
    private final StripeService stripeService;
    private final PermissionService permissionService;
    private final Datastore datastore;
    private final EntityService entityService;

    @Inject
    public StripeResource(StripeService stripeService, PermissionService permissionService, EntityService entityService, Datastore datastore) {
        this.stripeService = stripeService;
        this.permissionService = permissionService;
        this.entityService = entityService;
        this.datastore = datastore;
    }

    @DELETE
    @Path("subscriptions/{subscriptionId}")
    @Authorized
    public Response cancelSubscription(
        @PathParam("subscriptionId") String subscriptionId,
        @Context DecodedJWT jwt
    ) {
        ResponseBuilder builder;

        Subscription subscription = null;
        StripeException stripeException = null;

        try {
            subscription = Subscription.retrieve(subscriptionId, this.stripeService.getRequestOptions());
        } catch (StripeException e) {
            subscription = null;
            stripeException = e;
        }

        if (stripeException != null) {
            builder = Response.status(Status.SERVICE_UNAVAILABLE).entity(stripeException);
        } else if (subscription == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            String organizationId = subscription.getMetadata().get("organizationId");
            Organization organization = this.datastore.get(Organization.class, new ObjectId(organizationId));

            boolean hasPermission = this.permissionService.has(
                jwt.getSubject(),
                Arrays.asList(organization),
                PermissionService.READ
            );

            if (hasPermission) {
                StripeException exception;

                try {
                    subscription.cancel(new HashMap<>(), this.stripeService.getRequestOptions());
                    exception = null;
                } catch (StripeException e) {
                    exception = e;
                }

                if (exception == null) {
                    this.entityService.cascadeLastActivity(Arrays.asList(organization), new Date());
                    builder = Response.ok();
                } else {
                    builder = Response.status(Status.SERVICE_UNAVAILABLE).entity(exception);
                }
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }

    @GET
    @Path("{organizationId}/subscription")
    @Authorized
    @HasPermission(clazz=Organization.class, id="organizationId", permission=PermissionService.READ)
    public Response subscribe(@PathObject("organizationId") Organization organization) {
        ResponseBuilder builder;

        try {
            List<Subscription> subscriptions = this.stripeService.getCurrentSubscriptions(organization.id);
            builder = Response.ok(subscriptions).type(MediaType.APPLICATION_JSON);
        } catch (StripeException e) {
            builder = Response.status(Status.SERVICE_UNAVAILABLE).entity(e);
        }

        return builder.build();
    }

    @POST
    @Path("{organizationId}/subscribe")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    @HasPermission(clazz=Organization.class, id="organizationId", permission=PermissionService.WRITE)
    public Response subscribe(
        @PathObject("organizationId") Organization organization,
        Map<String, Object> json,
        @Context DecodedJWT jwt
    ) {
        ResponseBuilder builder;

        Map<String, Object> customerOptions = new HashMap<>();
        customerOptions.put("email", json.get("email"));
        customerOptions.put("source", json.get("source"));
        customerOptions.put("plan", this.stripeService.getMonthly10PlanId());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sub", jwt.getSubject());
        customerOptions.put("metadata", metadata);

        Map<String, Object> subscriptionUpdate = new HashMap<>();
        Map<String, Object> subscriptionMetaData = new HashMap<>();
        subscriptionMetaData.put("organizationId", organization.id);
        subscriptionMetaData.put("sub", jwt.getSubject());
        subscriptionUpdate.put("metadata", subscriptionMetaData);

        try {
            Customer customer = Customer.create(customerOptions, this.stripeService.getRequestOptions());
            List<Subscription> subscriptions = customer.getSubscriptions().getData();
            subscriptions.get(0).update(subscriptionUpdate, this.stripeService.getRequestOptions());
            this.entityService.cascadeLastActivity(Arrays.asList(organization), new Date());

            builder = Response.ok(customer).type(MediaType.APPLICATION_JSON);
        } catch (StripeException e) {
            builder = Response.status(Status.SERVICE_UNAVAILABLE).entity(e);
        }

        return builder.build();
    }
}