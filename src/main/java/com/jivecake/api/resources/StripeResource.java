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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;

import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.client.mgmt.filter.UserFilter;
import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.users.User;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.APIConfiguration;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.GZip;
import com.jivecake.api.filter.HasPermission;
import com.jivecake.api.filter.Log;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.filter.ValidEntity;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.StripePaymentProfile;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.request.AggregatedEvent;
import com.jivecake.api.request.EntityQuantity;
import com.jivecake.api.request.ErrorData;
import com.jivecake.api.request.ItemData;
import com.jivecake.api.request.StripeOrderPayload;
import com.jivecake.api.service.Auth0Service;
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

import io.sentry.SentryClient;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.ExceptionInterface;

@CORS
@Path("stripe")
@Singleton
public class StripeResource {
    private final SentryClient sentry;
    private final APIConfiguration apiConfiguration;
    private final Auth0Service auth0Service;
    private final MandrillService mandrillService;
    private final StripeService stripeService;
    private final TransactionService transactionService;
    private final PermissionService permissionService;
    private final Datastore datastore;
    private final EventService eventService;
    private final NotificationService notificationService;
    private final EntityService entityService;

    @Inject
    public StripeResource(
        SentryClient sentry,
        APIConfiguration apiConfiguration,
        Auth0Service auth0Service,
        MandrillService mandrillService,
        StripeService stripeService,
        TransactionService transactionService,
        PermissionService permissionService,
        EntityService entityService,
        EventService eventService,
        NotificationService notificationService,
        Datastore datastore
    ) {
        this.sentry = sentry;
        this.apiConfiguration = apiConfiguration;
        this.auth0Service = auth0Service;
        this.mandrillService = mandrillService;
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
    ) throws SignatureVerificationException {
        Webhook.constructEvent(
            body,
            signature,
            this.apiConfiguration.stripe.signingSecret
        );
        return Response.ok().build();
    }

    @POST
    @Path("{id}/refund")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    @HasPermission(id="id", clazz=Transaction.class, write=true)
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
                this.sentry.sendEvent(
                    new EventBuilder()
                        .withEnvironment(this.sentry.getEnvironment())
                        .withMessage(exception.getMessage())
                        .withEnvironment(this.apiConfiguration.sentry.environment)
                        .withLevel(io.sentry.event.Event.Level.ERROR)
                        .withSentryInterface(new ExceptionInterface(exception))
                        .withExtra("sub", jwt.getSubject())
                        .build()
                );
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
        @ValidEntity StripeOrderPayload payload,
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
                            transaction.organizationName = payload.data.organizationName;
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
                        this.entityService.cascadeLastActivity(completedTransactions, date);
                        this.notificationService.notify(
                            new ArrayList<>(completedTransactions),
                            "transaction.create"
                        );

                        if (userId == null) {
                            List<Item> items = aggregated.itemData.stream()
                                .map(data -> data.item)
                                .collect(Collectors.toList());

                            Map<String, Object> message = this.mandrillService.getTransactionConfirmation(
                                token,
                                event,
                                items,
                                completedTransactions
                            );

                            this.mandrillService.send(message);
                        } else {
                            try {
                                Event updatedEvent = this.eventService.assignNumberToUserSafely(userId, event).get();
                                this.notificationService.notify(
                                    Arrays.asList(updatedEvent),
                                    "event.update"
                                );
                            } catch (InterruptedException | ExecutionException e) {
                                EventBuilder eventBuilder = new EventBuilder()
                                    .withEnvironment(this.sentry.getEnvironment())
                                    .withMessage(e.getMessage())
                                    .withLevel(io.sentry.event.Event.Level.ERROR)
                                    .withSentryInterface(new ExceptionInterface(e));

                                if (jwt.getSubject() != null) {
                                    eventBuilder.withExtra("sub", jwt.getSubject());
                                }

                                this.sentry.sendEvent(eventBuilder.build());
                            }
                        }

                        builder = Response.ok();
                    } else {
                        EventBuilder eventBuilder = new EventBuilder()
                            .withEnvironment(this.sentry.getEnvironment())
                            .withMessage(exception.getMessage())
                            .withLevel(io.sentry.event.Event.Level.WARNING)
                            .withSentryInterface(new ExceptionInterface(exception));

                        if (jwt != null) {
                            eventBuilder.withExtra("sub", jwt.getSubject());
                        }

                        this.sentry.sendEvent(eventBuilder.build());
                        builder = Response.status(Status.SERVICE_UNAVAILABLE);
                    }
                } else {
                    EventBuilder eventBuilder = new EventBuilder()
                        .withEnvironment(this.sentry.getEnvironment())
                        .withMessage(tokenException.getMessage())
                        .withLevel(io.sentry.event.Event.Level.WARNING)
                        .withSentryInterface(new ExceptionInterface(tokenException));

                    if (jwt != null) {
                        eventBuilder.withExtra("sub", jwt.getSubject());
                    }

                    this.sentry.sendEvent(eventBuilder.build());
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
            this.sentry.sendEvent(
                new EventBuilder()
                    .withMessage(stripeException.getMessage())
                    .withEnvironment(this.sentry.getEnvironment())
                    .withLevel(io.sentry.event.Event.Level.ERROR)
                    .withSentryInterface(new ExceptionInterface(stripeException))
                    .withExtra("sub", jwt.getSubject()).build()
            );
            builder = Response.status(Status.SERVICE_UNAVAILABLE);
        } else {
            String organizationId = subscription.getMetadata().get("organizationId");
            Organization organization = this.datastore.get(Organization.class, new ObjectId(organizationId));

            boolean hasPermission = this.permissionService.hasWrite(
                jwt.getSubject(),
                Arrays.asList(organization)
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
                    this.sentry.sendEvent(
                        new EventBuilder()
                            .withEnvironment(this.sentry.getEnvironment())
                            .withMessage(exception.getMessage())
                            .withLevel(io.sentry.event.Event.Level.ERROR)
                            .withSentryInterface(new ExceptionInterface(exception))
                            .withExtra("sub", jwt.getSubject())
                            .build()
                    );
                    builder = Response.status(Status.SERVICE_UNAVAILABLE);
                }
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }

    @POST
    @Path("{organizationId}/subscribe/{planId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    @HasPermission(clazz=Organization.class, id="organizationId", write=true)
    public Response subscribe(
        @PathObject("organizationId") Organization organization,
        @PathParam("planId") String planId,
        @Context DecodedJWT jwt,
        Map<String, Object> json
    ) throws Auth0Exception, StripeException {
        ResponseBuilder builder;

        boolean validPlan = StripeService.MONTHLY_TRIAL_ID.equals(planId) || StripeService.MONTHLY_ID.equals(planId);

        ManagementAPI api = new ManagementAPI(
            this.apiConfiguration.oauth.domain,
            this.auth0Service.getToken().get("access_token").asText()
        );

        User user = api.users().get(jwt.getSubject(), new UserFilter()).execute();

        if (validPlan) {
            boolean trialViolation = false;

            if (StripeService.MONTHLY_TRIAL_ID.equals(planId)) {
                Map<String, Object> query = new HashMap<>();
                query.put("plan", StripeService.MONTHLY_TRIAL_ID);
                query.put("status", "all");
                query.put("limit", "100");

                Iterable<Subscription> subscriptions = Subscription.list(
                    query,
                    this.stripeService.getRequestOptions()
                ).autoPagingIterable();

                for (Subscription subscription: subscriptions) {
                    Map<String, String> metadata = subscription.getMetadata();

                    trialViolation = organization.id.toString().equals(metadata.get("organizationId")) ||
                        jwt.getSubject().equals(metadata.get("sub")) ||
                        user.getEmail().equals(metadata.get("email"));

                    if (trialViolation) {
                        break;
                    }
                }
            }

            if (trialViolation) {
                ErrorData entity = new ErrorData();
                entity.error = "hasSubscribed";
                builder = Response.status(Status.BAD_REQUEST)
                    .entity(entity)
                    .type(MediaType.APPLICATION_JSON);
            } else {
                Map<String, Object> customerOptions = new HashMap<>();
                customerOptions.put("email", json.get("email"));
                customerOptions.put("source", json.get("source"));
                customerOptions.put("plan", planId);

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("sub", jwt.getSubject());
                customerOptions.put("metadata", metadata);

                Map<String, Object> subscriptionUpdate = new HashMap<>();
                Map<String, Object> subscriptionMetaData = new HashMap<>();
                subscriptionMetaData.put("organizationId", organization.id);
                subscriptionMetaData.put("sub", jwt.getSubject());
                subscriptionMetaData.put("email", user.getEmail());
                subscriptionUpdate.put("metadata", subscriptionMetaData);

                try {
                    Customer customer = Customer.create(customerOptions, this.stripeService.getRequestOptions());
                    List<Subscription> subscriptions = customer.getSubscriptions().getData();
                    Subscription subscription = subscriptions.get(0);
                    subscription.update(subscriptionUpdate, this.stripeService.getRequestOptions());
                    this.entityService.cascadeLastActivity(Arrays.asList(organization), new Date());

                    builder = Response.ok(subscription, MediaType.APPLICATION_JSON);
                } catch (StripeException e) {
                    builder = Response.status(Status.SERVICE_UNAVAILABLE);
                }
            }
        } else {
            builder = Response.status(Status.NOT_FOUND);
        }

        return builder.build();
    }

    @GET
    @GZip
    @Path("subscription")
    @Authorized
    public Response subscribe(
        @QueryParam("organizationId") ObjectId organizationId,
        @QueryParam("sub") String sub,
        @QueryParam("email") String email,
        @QueryParam("status") String status,
        @QueryParam("planId") String plan,
        @Context DecodedJWT jwt
    ) throws StripeException, Auth0Exception {
        boolean validQuery = organizationId != null || sub != null || email != null;
        boolean validStatus = status == null ||
            "active".equals(status) ||
            "all".equals(status) ||
            "trialing".equals(status) ||
            "past_due".equals(status) ||
            "canceled".equals(status) ||
            "unpaid".equals(status);

        if (!validStatus || !validQuery) {
            return Response.status(Status.BAD_REQUEST).build();
        }

        boolean isAuthorized = false;

        if (email != null) {
            ManagementAPI api = new ManagementAPI(
                this.apiConfiguration.oauth.domain,
                this.auth0Service.getToken().get("access_token").asText()
            );

            User user = api.users().get(jwt.getSubject(), new UserFilter()).execute();

            isAuthorized = email != null && email.equals(user.getEmail());
        }

        if (sub != null) {
            isAuthorized = jwt.getSubject().equals(sub);
        }

        if (organizationId != null) {
            Organization organization = this.datastore.get(Organization.class, organizationId);
            isAuthorized = this.permissionService.hasRead(
                jwt.getSubject(),
                Arrays.asList(organization)
            );
        }

        if (!isAuthorized) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        Map<String, Object> query = new HashMap<>();
        query.put("limit", 100);

        if (plan != null) {
            query.put("plan", plan);
        }

        if (status != null) {
            query.put("status", status);
        }

        List<Subscription> result = new ArrayList<>();

        Subscription.list(query, this.stripeService.getRequestOptions())
        .autoPagingIterable()
        .forEach(subscription -> {
            Map<String, String> metaData = subscription.getMetadata();

            if (organizationId != null && organizationId.toString().equals(metaData.get("organizationId"))) {
                result.add(subscription);
            }

            if (email != null && email.equals(metaData.get("email"))) {
                result.add(subscription);
            }

            if (sub != null && sub.equals(metaData.get("sub"))) {
                result.add(subscription);
            }
        });

        return Response.ok(result, MediaType.APPLICATION_JSON).build();
    }
}