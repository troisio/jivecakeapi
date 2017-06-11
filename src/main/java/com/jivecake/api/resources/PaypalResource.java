package com.jivecake.api.resources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.Log;
import com.jivecake.api.model.Application;
import com.jivecake.api.model.CartPaymentDetails;
import com.jivecake.api.model.PaymentDetail;
import com.jivecake.api.model.PaypalIPN;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.request.Paging;
import com.jivecake.api.serializer.JsonTools;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.PaypalService;
import com.jivecake.api.service.PermissionService;
import com.jivecake.api.service.TransactionService;

@Path("paypal")
@CORS
@Singleton
public class PaypalResource {
    private final Datastore datastore;
    private final PermissionService permissionService;
    private final ApplicationService applicationService;
    private final PaypalService paypalService;
    private final NotificationService notificationService;
    private final TransactionService transactionService;
    private final EntityService entityService;
    private final Logger logger = LogManager.getLogger(PaypalResource.class);
    private final JsonTools jsonTools = new JsonTools();

    @Inject
    public PaypalResource(
        Datastore datastore,
        PermissionService permissionService,
        ApplicationService applicationService,
        PaypalService paypalService,
        NotificationService notificationService,
        TransactionService transactionService,
        EntityService entityService
    ) {
        this.datastore = datastore;
        this.permissionService = permissionService;
        this.applicationService = applicationService;
        this.paypalService = paypalService;
        this.notificationService = notificationService;
        this.transactionService = transactionService;
        this.entityService = entityService;
    }

    @POST
    @Path("detail")
    public Response getPaymentDetails(@Context DecodedJWT jwt) {
        PaymentDetail detail = new CartPaymentDetails();
        detail.custom = new ObjectId();
        detail.timeCreated = new Date();

        if (jwt != null) {
            detail.user_id = jwt.getSubject();
        }

        Key<PaymentDetail> key = this.datastore.save(detail);
        PaymentDetail entity = this.datastore.get(PaymentDetail.class, key.getId());
        return Response.ok(entity).type(MediaType.APPLICATION_JSON).build();
    }

    private InvocationCallback<Response> onIPN(MultivaluedMap<String, String> form, AsyncResponse response) {
        return new InvocationCallback<Response>() {
            @Override
            public void completed(Response paypalResponse) {
                Object entity = paypalResponse.getEntity();
                String body = entity instanceof String ? (String)entity : paypalResponse.readEntity(String.class);

                boolean verified = body.equals(PaypalResource.this.paypalService.getVerified());

                if (verified) {
                    List<PaypalIPN> ipns;

                    String txn_id = form.getFirst("txn_id");

                    if (txn_id == null) {
                        ipns = new ArrayList<>();
                    } else {
                        ipns = PaypalResource.this.datastore.createQuery(PaypalIPN.class)
                            .field("txn_id").equal(txn_id)
                            .field("payment_status").equal(form.getFirst("payment_status"))
                            .asList();
                    }

                    boolean transactionHasBeenProcessed = !ipns.isEmpty();
                    Date currentTime = new Date();

                    if (transactionHasBeenProcessed) {
                        PaypalResource.this.logger.warn(String.format("%s has already been processed", PaypalResource.this.jsonTools.pretty(form)));
                    } else {
                        PaypalIPN ipn = PaypalResource.this.paypalService.create(form);
                        ipn.timeCreated = currentTime;

                        Key<PaypalIPN> paypalIPNKey = PaypalResource.this.datastore.save(ipn);

                        if ("cart".equals(ipn.txn_type)) {
                            List<Transaction> transactions = PaypalResource.this.paypalService.processTransactions(ipn);
                            Iterable<Key<Transaction>> keys = PaypalResource.this.datastore.save(transactions);
                            List<Transaction> newTransactions = PaypalResource.this.datastore.getByKeys(keys);

                            PaypalResource.this.entityService.cascadeLastActivity(newTransactions, currentTime);
                            PaypalResource.this.notificationService.notify(new ArrayList<>(newTransactions), "transaction.create");

                            if (transactions.isEmpty()) {
                                PaypalResource.this.logger.warn(String.format("paypal Cart IPN %s did not produce processed transactions", paypalIPNKey.getId()));
                            }
                        } else if ("Refunded".equals(ipn.payment_status)) {
                            List<Transaction> transactions = PaypalResource.this.paypalService.processRefund(ipn);
                            Iterable<Key<Transaction>> keys = PaypalResource.this.datastore.save(transactions);
                            List<Transaction> newTransactions = PaypalResource.this.datastore.getByKeys(keys);

                            PaypalResource.this.entityService.cascadeLastActivity(newTransactions, currentTime);
                            PaypalResource.this.notificationService.notify(new ArrayList<>(newTransactions), "transaction.create");
                        } else {
                            PaypalResource.this.logger.warn(String.format("paypal IPN %s did not match any processed scopes", paypalIPNKey.getId()));
                        }
                    }
                } else {
                    PaypalResource.this.logger.warn(
                        String.format(
                            "Paypal IPN returned \"%s\", expecting \"%s\"",
                            entity,
                            PaypalResource.this.paypalService.getVerified()
                        )
                    );
                }

                response.resume(Response.ok().build());
            }

            @Override
            public void failed(Throwable throwable) {
                response.resume(throwable);
            }
        };
    }

    @GET
    @Path("ipn")
    @Authorized
    public Response search(
        @QueryParam("id") ObjectId id,
        @QueryParam("txn_id") String txn_id,
        @QueryParam("parent_txn_id") String parent_txn_id,
        @QueryParam("timeCreated") Long timeCreated,
        @QueryParam("timeCreatedLessThan") Long timeCreatedLessThan,
        @QueryParam("item_number") String itemNumber,
        @QueryParam("timeCreatedGreaterThan") Long timeCreatedGreaterThan,
        @QueryParam("custom") List<String> custom,
        @QueryParam("payment_status") List<String> paymentStatuses,
        @QueryParam("limit") Integer limit,
        @QueryParam("offset") Integer offset,
        @QueryParam("order") String order,
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
            Query<PaypalIPN> query = this.datastore.createQuery(PaypalIPN.class);

            if (id != null) {
                query.field("id").equal(id);
            }

            if (txn_id != null) {
                query.field("txn_id").equal(txn_id);
            }

            if (parent_txn_id != null) {
                query.field("parent_txn_id").equal(parent_txn_id);
            }

            if (!custom.isEmpty()) {
                query.field("custom").in(custom);
            }

            if (itemNumber != null) {
                query.field("item_number").equal(itemNumber);
            }

            if (!paymentStatuses.isEmpty()) {
                query.field("payment_status").in(paymentStatuses);
            }

            if (timeCreated != null) {
                query.field("timeCreated").equal(new Date(timeCreated));
            }

            if (timeCreatedLessThan != null) {
                query.field("timeCreated").lessThan(new Date(timeCreatedLessThan));
            }

            if (timeCreatedGreaterThan != null) {
                query.field("timeCreated").greaterThan(new Date(timeCreatedGreaterThan));
            }

            if (order != null) {
                query.order(order);
            }

            FindOptions options = new FindOptions();
            options.limit(ApplicationService.LIMIT_DEFAULT);

            if (offset != null) {
                options.skip(offset);
            }

            Paging<PaypalIPN> entity = new Paging<>(query.asList(options), query.count());
            builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @Log
    @Authorized
    @POST
    @Path("ipn/{status}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public void transaction(
        @PathParam("status") String status,
        MultivaluedMap<String, String> form,
        @Suspended AsyncResponse response,
        @Context DecodedJWT jwt
    ) {
        Application application = this.applicationService.read();
        boolean hasPermission = this.permissionService.has(
            jwt.getSubject(),
            Arrays.asList(application),
            PermissionService.WRITE
        );

        if (hasPermission) {
            PaypalService paypalService = new PaypalService(this.datastore, this.notificationService, this.transactionService) {
                @Override
                public Future<Response> isValidIPN(MultivaluedMap<String, String> paramaters, String paypalURL, InvocationCallback<Response> callback) {
                    Response response = Response.ok(status).build();

                    Future<Response> future = CompletableFuture.completedFuture(response);

                    callback.completed(response);
                    return future;
                }
            };

            paypalService.isValidIPN(form, this.paypalService.getSandboxURL(), this.onIPN(form, response));
        } else {
            response.resume(Response.status(Status.UNAUTHORIZED).build());
        }
    }

    @Log
    @POST
    @Path("ipn/sandbox")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public void ipnSandbox(MultivaluedMap<String, String> form, @Suspended AsyncResponse response) {
        this.paypalService.isValidIPN(form, this.paypalService.getSandboxURL(), this.onIPN(form, response));
    }

    @Log
    @POST
    @Path("ipn")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public void ipn(MultivaluedMap<String, String> form, @Suspended AsyncResponse response) {
        this.paypalService.isValidIPN(form, this.paypalService.getIPNUrl(), this.onIPN(form, response));
    }
}