package com.jivecake.api.resources;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
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
import org.mongodb.morphia.Key;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.StripeConfiguration;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.HasPermission;
import com.jivecake.api.filter.Log;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.StripeCharge;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.request.StripeOrderPayload;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.ItemService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.PermissionService;
import com.jivecake.api.service.StripeService;
import com.jivecake.api.service.TransactionService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import com.stripe.net.Webhook;

@CORS
@Path("stripe")
@Singleton
public class StripeResource {
    private final StripeConfiguration stripeConfiguration;
    private final StripeService stripeService;
    private final TransactionService transactionService;
    private final PermissionService permissionService;
    private final Datastore datastore;
    private final ItemService itemService;
    private final NotificationService notificationService;
    private final EntityService entityService;

    @Inject
    public StripeResource(
        StripeConfiguration stripeConfiguration,
        StripeService stripeService,
        TransactionService transactionService,
        PermissionService permissionService,
        EntityService entityService,
        ItemService itemService,
        NotificationService notificationService,
        Datastore datastore
    ) {
        this.stripeConfiguration = stripeConfiguration;
        this.stripeService = stripeService;
        this.transactionService = transactionService;
        this.permissionService = permissionService;
        this.entityService = entityService;
        this.itemService = itemService;
        this.notificationService = notificationService;
        this.datastore = datastore;
    }

    @Log
    @POST
    @Path("webhook")
    @Authorized
    public Response order(
        @HeaderParam("Stripe-Signature") String signature,
        String body
    ) {
        com.stripe.model.Event event;

        try {
            event = Webhook.constructEvent(
                body,
                signature,
                this.stripeConfiguration.secretKey
            );
        } catch (SignatureVerificationException e) {
            e.printStackTrace();
        }

        return Response.ok().build();
    }

    @Log
    @POST
    @Path("{eventId}/order")
    @Authorized
    public Response order(
        @PathObject("eventId") Event event,
        StripeOrderPayload payload,
        @Context DecodedJWT jwt
    ) {
        ResponseBuilder builder = null;

        if (event == null) {
            builder = Response.status(Status.BAD_REQUEST)
                .entity("{\"error\": \"event\"}")
                .type(MediaType.APPLICATION_JSON);
        } else {
            List<ObjectId> itemIds = payload.itemData.stream()
                .map(data -> data.entity)
                .collect(Collectors.toList());

            List<Item> items = this.datastore.createQuery(Item.class)
                 .field("eventId").equal(event.id)
                .field("id").in(itemIds)
                .field("status").equal(ItemService.STATUS_ACTIVE)
                .asList();

            if (items.size() == itemIds.size()) {
                Date date = new Date();

                List<Transaction> transactions = this.transactionService.getTransactionQueryForCounting()
                    .field("itemId").in(itemIds)
                    .asList();

                Map<ObjectId, Integer> quantities = payload.itemData.stream()
                    .collect(
                        Collectors.toMap(data -> data.entity, data -> data.quantity)
                    );

                double total = 0;

                double[] amounts = this.itemService.getAmounts(items, date, transactions);

                for (int index = 0; index < items.size(); index++) {
                    Item item = items.get(index);
                    double amount = amounts[index];

                    int quantity = quantities.get(item.id).intValue();
                    total += quantity * amount;
                }

                DecimalFormat format = new DecimalFormat("#.##");
                String string = format.format(total);
                double amountAsDouble = new Double(string);
                int amount = (int)(amountAsDouble * 100);

                Map<String, Object> params = new HashMap<>();
                params.put("amount", amount);
                params.put("currency", payload.currency);
                params.put("description", "JiveCake");
                params.put("source", payload.token.id);

                StripeException exception = null;
                Charge charge;

                try {
                    charge = Charge.create(params, this.stripeService.getRequestOptions());
                } catch (StripeException e) {
                    exception = e;
                    e.printStackTrace();
                    charge = null;
                }

                if (exception == null) {
                    Key<StripeCharge> key = this.stripeService.saveCharge(charge);

                    Charge finalCharge = charge;

                    List<Transaction> completedTransactions = new ArrayList<>();

                    for (int index = 0; index < items.size(); index++) {
                        Item item = items.get(index);

                        Transaction transaction = new Transaction();
                        transaction.eventId = item.eventId;
                        transaction.organizationId = item.organizationId;
                        transaction.leaf = true;
                        transaction.quantity = quantities.get(item.id);
                        transaction.amount = amounts[index];
                        transaction.user_id = jwt.getSubject();
                        transaction.status = TransactionService.SETTLED;
                        transaction.paymentStatus = TransactionService.PAYMENT_EQUAL;
                        transaction.linkedObjectClass = StripeCharge.class.getSimpleName();
                        transaction.linkedId = (ObjectId)key.getId();
                        transaction.eventId = item.eventId;
                        transaction.organizationId = item.organizationId;
                        transaction.email = finalCharge.getReceiptEmail();
                        transaction.itemId = item.id;
                        transaction.currency = finalCharge.getCurrency().toUpperCase();
                        transaction.timeCreated = date;

                        completedTransactions.add(transaction);
                    }

                    this.datastore.save(completedTransactions);

                    this.notificationService.notify(
                        new ArrayList<>(completedTransactions),
                        "transaction.create"
                    );

                    builder = Response.ok(charge).type(MediaType.APPLICATION_JSON);
                } else {
                    builder = Response.status(Status.SERVICE_UNAVAILABLE)
                        .entity(exception);
                }
            } else {
                builder = Response.status(Status.BAD_REQUEST)
                    .entity("{\"error\": \"item\"}")
                    .type(MediaType.APPLICATION_JSON);
            }
        }

        return builder.build();
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