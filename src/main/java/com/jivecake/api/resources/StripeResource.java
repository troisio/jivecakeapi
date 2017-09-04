package com.jivecake.api.resources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
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

import org.apache.log4j.Logger;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.StripeConfiguration;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.HasPermission;
import com.jivecake.api.filter.Log;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.StripePaymentProfile;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.request.AggregatedEvent;
import com.jivecake.api.request.EntityQuantity;
import com.jivecake.api.request.ErrorData;
import com.jivecake.api.request.ItemData;
import com.jivecake.api.request.StripeOrderPayload;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.MandrillService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.PermissionService;
import com.jivecake.api.service.StripeService;
import com.jivecake.api.service.TransactionService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.Refund;
import com.stripe.model.Subscription;
import com.stripe.model.Token;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;

@CORS
@Path("stripe")
@Singleton
public class StripeResource {
    private final Logger logger = Logger.getLogger(StripeResource.class);
    private final ApplicationService applicationService;
    private final MandrillService mandrillService;
    private final StripeConfiguration stripeConfiguration;
    private final StripeService stripeService;
    private final TransactionService transactionService;
    private final PermissionService permissionService;
    private final Datastore datastore;
    private final EventService eventService;
    private final NotificationService notificationService;
    private final EntityService entityService;

    @Inject
    public StripeResource(
        ApplicationService applicationService,
        MandrillService mandrillService,
        StripeConfiguration stripeConfiguration,
        StripeService stripeService,
        TransactionService transactionService,
        PermissionService permissionService,
        EntityService entityService,
        EventService eventService,
        NotificationService notificationService,
        Datastore datastore
    ) {
        this.applicationService = applicationService;
        this.mandrillService = mandrillService;
        this.stripeConfiguration = stripeConfiguration;
        this.stripeService = stripeService;
        this.transactionService = transactionService;
        this.permissionService = permissionService;
        this.entityService = entityService;
        this.eventService = eventService;
        this.notificationService = notificationService;
        this.datastore = datastore;
    }

    @Log
    @POST
    @Path("webhook")
    public Response webhook(
        @HeaderParam("Stripe-Signature") String signature,
        String body
    ) {
        com.stripe.model.Event event = null;
        SignatureVerificationException exception = null;

        try {
            event = Webhook.constructEvent(body, signature, this.stripeConfiguration.signingSecret);
        } catch (SignatureVerificationException e) {
            exception = e;
        }

        if (exception == null) {
        } else {
            this.logger.info(exception);
        }

        return Response.ok().build();
    }

    @POST
    @Path("{id}/refund")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    @HasPermission(id="id", clazz=Transaction.class, permission=PermissionService.WRITE)
    public Response refund(@PathObject("id") Transaction transaction, @Context DecodedJWT jwt) {
        boolean canRefund = transaction.leaf = true &&
            transaction.status == TransactionService.SETTLED &&
            "StripeCharge".equals(transaction.linkedObjectClass);

        ResponseBuilder builder;

        if (canRefund) {
            RequestOptions options = this.stripeService.getRequestOptions();

            long amount = (long)(transaction.amount * 100);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("charge", transaction.linkedId);
            parameters.put("amount", amount);

            StripeException exception = null;

            try {
                Refund.create(parameters, options);
            } catch (StripeException e) {
                exception = e;
            }

            if (exception == null) {
                Transaction refundTransaction = new Transaction(transaction);
                refundTransaction.id = null;
                refundTransaction.parentTransactionId = transaction.id;
                refundTransaction.leaf = true;
                refundTransaction.amount = new Double(TransactionService.DEFAULT_DECIMAL_FORMAT.format(amount / -100));
                refundTransaction.status = TransactionService.REFUNDED;
                refundTransaction.paymentStatus = TransactionService.PAYMENT_EQUAL;
                refundTransaction.timeCreated = new Date();
                transaction.leaf = false;

                this.datastore.save(Arrays.asList(transaction, refundTransaction));

                this.notificationService.notify(Arrays.asList(transaction), "transaction.update");
                this.notificationService.notify(Arrays.asList(refundTransaction), "transaction.create");
                this.entityService.cascadeLastActivity(Arrays.asList(transaction, refundTransaction), new Date());

                builder = Response.ok(refundTransaction).type(MediaType.APPLICATION_JSON);
            } else {
                this.applicationService.saveException(exception, jwt.getSubject());
                builder = Response.status(Status.SERVICE_UNAVAILABLE);
            }
        } else {
            builder = Response.status(Status.BAD_REQUEST);
        }

        return builder.build();
    }

    @POST
    @Path("{eventId}/order")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response order(
        @PathObject("eventId") Event event,
        StripeOrderPayload payload,
        @Context DecodedJWT jwt
    ) {
        ResponseBuilder builder;

        if (event == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            Date date = new Date();

            String userId = jwt == null ? null : jwt.getSubject();
            AggregatedEvent aggregated = this.eventService.getAggregatedaEventData(
                event,
                this.transactionService,
                date
            );
            List<ErrorData> dataError = this.eventService.getErrorsFromOrderRequest(
                userId,
                payload.data,
                aggregated
            );

            if (!(aggregated.profile instanceof StripePaymentProfile)) {
                ErrorData error = new ErrorData();
                error.error = "profile";
                dataError.add(error);
            }

            if (dataError.isEmpty()) {
                Map<ObjectId, ItemData> itemIdToItemData = aggregated.itemData.stream()
                    .collect(
                        Collectors.toMap(data -> data.item.id, Function.identity())
                    );

                double total = 0;

                for (EntityQuantity<ObjectId> entityQuantity: payload.data.order) {
                    ItemData itemData = itemIdToItemData.get(entityQuantity.entity);
                    total += entityQuantity.quantity * itemData.amount;
                }

                String string = TransactionService.DEFAULT_DECIMAL_FORMAT.format(total);
                double amountAsDouble = new Double(string);
                int amount = (int)(amountAsDouble * 100);

                StripeException tokenException = null;
                Token token = null;

                try {
                    token = Token.retrieve(payload.token.id, this.stripeService.getRequestOptions());
                } catch (StripeException e) {
                    tokenException = e;
                }

                if (tokenException == null) {
                    Map<String, Object> params = new HashMap<>();
                    params.put("amount", amount);
                    params.put("currency", event.currency);
                    params.put("description", event.name + " / JiveCake");
                    params.put("source", token.getId());

                    StripeException exception = null;
                    Charge charge;

                    try {
                        charge = Charge.create(params, this.stripeService.getRequestOptions());
                    } catch (StripeException e) {
                        exception = e;
                        charge = null;
                    }

                    if (exception == null) {
                        List<Transaction> completedTransactions = new ArrayList<>();

                        for (EntityQuantity<ObjectId> entityQuantity: payload.data.order) {
                            ItemData itemData = itemIdToItemData.get(entityQuantity.entity);

                            Transaction transaction = new Transaction();
                            transaction.organizationId = itemData.item.organizationId;
                            transaction.eventId = itemData.item.eventId;
                            transaction.itemId = itemData.item.id;
                            transaction.quantity = entityQuantity.quantity;
                            transaction.amount = itemData.amount * transaction.quantity;
                            transaction.status = TransactionService.SETTLED;
                            transaction.paymentStatus = TransactionService.PAYMENT_EQUAL;
                            transaction.linkedId = charge.getId();
                            transaction.linkedObjectClass = "StripeCharge";
                            transaction.currency = charge.getCurrency().toUpperCase();
                            transaction.leaf = true;
                            transaction.timeCreated = date;

                            if (userId == null) {
                                transaction.email = token.getEmail();
                                transaction.given_name = payload.data.firstName;
                                transaction.family_name = payload.data.lastName;
                            } else {
                                transaction.user_id = userId;
                            }

                            completedTransactions.add(transaction);
                        }

                        this.datastore.save(completedTransactions);
                        this.notificationService.notify(
                            new ArrayList<>(completedTransactions),
                            "transaction.create"
                        );

                        if (userId == null) {
                            Map<String, String> to = new HashMap<>();
                            to.put("email", token.getEmail());
                            to.put("type", "to");

                            Map<String, Object> message = new HashMap<>();
                            message.put(
                                "text",
                                "Your payment for " + event.name + " has been received. Your Stripe transaction ID is "
                                + charge.getId()
                            );
                            message.put("subject", event.name);
                            message.put("from_email", "noreply@jivecake.com");
                            message.put("from_name", "JiveCake");
                            message.put("to", Arrays.asList(to));

                            this.mandrillService.send(message);
                        } else {
                            try {
                                Event updatedEvent = this.eventService.assignNumberToUserSafely(userId, event).get();
                                this.notificationService.notify(
                                    Arrays.asList(updatedEvent),
                                    "event.update"
                                );
                            } catch (InterruptedException | ExecutionException e) {
                                this.applicationService.saveException(e, userId);
                            }
                        }

                        builder = Response.ok();
                    } else {
                        this.applicationService.saveException(exception, userId);
                        builder = Response.status(Status.SERVICE_UNAVAILABLE);
                    }
                } else {
                    this.applicationService.saveException(tokenException, userId);
                    builder = Response.status(Status.SERVICE_UNAVAILABLE);
                }
            } else {
                builder = Response.status(Status.BAD_REQUEST)
                    .entity(dataError)
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
            stripeException = e;
        }

        if (stripeException != null) {
            this.applicationService.saveException(stripeException, jwt.getSubject());
            builder = Response.status(Status.SERVICE_UNAVAILABLE);
        } else {
            String organizationId = subscription.getMetadata().get("organizationId");
            Organization organization = this.datastore.get(Organization.class, new ObjectId(organizationId));

            boolean hasPermission = this.permissionService.has(
                jwt.getSubject(),
                Arrays.asList(organization),
                PermissionService.WRITE
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
                    this.applicationService.saveException(exception, jwt.getSubject());
                    builder = Response.status(Status.SERVICE_UNAVAILABLE);
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
    public Response subscribe(
        @PathObject("organizationId") Organization organization,
        @Context DecodedJWT jwt
    ) {
        ResponseBuilder builder;

        List<Subscription> subscriptions = null;
        StripeException exception = null;

        try {
            subscriptions = this.stripeService.getCurrentSubscriptions(organization.id);
        } catch (StripeException e) {
            exception = e;
        }

        if (exception == null) {
            builder = Response.ok(subscriptions).type(MediaType.APPLICATION_JSON);
        } else {
            exception.printStackTrace();
            builder = Response.status(Status.SERVICE_UNAVAILABLE);
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