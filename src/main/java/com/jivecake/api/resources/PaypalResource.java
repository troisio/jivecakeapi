package com.jivecake.api.resources;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jivecake.api.APIConfiguration;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.HasPermission;
import com.jivecake.api.filter.Log;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.filter.ValidEntity;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.PaypalPaymentProfile;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.request.AggregatedEvent;
import com.jivecake.api.request.EntityQuantity;
import com.jivecake.api.request.ErrorData;
import com.jivecake.api.request.ItemData;
import com.jivecake.api.request.OrderData;
import com.jivecake.api.request.PaypalAuthorizationPayload;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.MandrillService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.TransactionService;
import com.paypal.api.payments.Amount;
import com.paypal.api.payments.Details;
import com.paypal.api.payments.ItemList;
import com.paypal.api.payments.Payee;
import com.paypal.api.payments.Payer;
import com.paypal.api.payments.PayerInfo;
import com.paypal.api.payments.Payment;
import com.paypal.api.payments.PaymentExecution;
import com.paypal.api.payments.RedirectUrls;
import com.paypal.api.payments.RefundRequest;
import com.paypal.api.payments.RelatedResources;
import com.paypal.api.payments.Sale;
import com.paypal.base.Constants;
import com.paypal.base.rest.APIContext;
import com.paypal.base.rest.PayPalRESTException;

@Path("paypal")
@CORS
@Singleton
public class PaypalResource {
    private final Datastore datastore;
    private final MandrillService mandrillService;
    private final ApplicationService applicationService;
    private final EventService eventService;
    private final EntityService entityService;
    private final NotificationService notificationService;
    private final TransactionService transactionService;
    private final APIConfiguration configuration;
    private final APIContext context;
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    public PaypalResource(
        Datastore datastore,
        MandrillService mandrillService,
        ApplicationService applicationService,
        EventService eventService,
        EntityService entityService,
        NotificationService notificationService,
        TransactionService transactionService,
        APIConfiguration configuration
    ) {
        this.datastore = datastore;
        this.mandrillService = mandrillService;
        this.applicationService = applicationService;
        this.eventService = eventService;
        this.entityService = entityService;
        this.notificationService = notificationService;
        this.transactionService = transactionService;
        this.configuration = configuration;

        this.context = new APIContext(
            this.configuration.paypal.clientId,
            this.configuration.paypal.clientSecret,
            this.configuration.paypal.mode
        );
    }

    @POST
    @Path("{id}/refund")
    @Consumes(MediaType.APPLICATION_JSON)
    @HasPermission(id="id", clazz=Transaction.class, write=true)
    public Response refund(@PathObject("id") Transaction transaction) {
        ResponseBuilder builder;

        boolean canRefund = transaction.status == TransactionService.SETTLED &&
            transaction.leaf &&
            "PaypalPayment".equals(transaction.linkedObjectClass);

        if (canRefund) {
            Payment payment = null;
            PayPalRESTException exception = null;

            try {
                payment = Payment.get(this.context, transaction.linkedId);
            } catch (PayPalRESTException e) {
                exception = e;
            }

            if (exception == null) {
                List<com.paypal.api.payments.Transaction> transactions = payment.getTransactions();

                if (transactions.size() == 1) {
                    com.paypal.api.payments.Transaction paypalTransaction = transactions.get(0);
                    String currency = paypalTransaction.getAmount().getCurrency();

                    List<Sale> sales = paypalTransaction.getRelatedResources().stream()
                        .map(RelatedResources::getSale)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                    if (sales.size() == 1) {
                        Sale sale = sales.get(0);

                        PayPalRESTException refundException = null;

                        RefundRequest request = new RefundRequest();

                        Amount amount = new Amount();
                        amount.setCurrency(currency);
                        amount.setTotal(
                            TransactionService.DEFAULT_DECIMAL_FORMAT.format(
                                transaction.amount
                            )
                        );

                        try {
                            sale.refund(this.context, request);
                        } catch (PayPalRESTException e) {
                            refundException = e;
                        }

                        if (refundException == null) {
                            Transaction refundTransaction = new Transaction(transaction);
                            refundTransaction.id = null;
                            refundTransaction.parentTransactionId = transaction.id;
                            refundTransaction.leaf = true;
                            refundTransaction.amount = transaction.amount * -1;
                            refundTransaction.status = TransactionService.REFUNDED;
                            refundTransaction.timeCreated = new Date();

                            transaction.leaf = false;

                            this.datastore.save(Arrays.asList(transaction, refundTransaction));

                            this.notificationService.notify(Arrays.asList(transaction), "transaction.update");
                            this.notificationService.notify(Arrays.asList(refundTransaction), "transaction.create");
                            this.entityService.cascadeLastActivity(Arrays.asList(transaction, refundTransaction), new Date());

                            builder = Response.ok(refundTransaction).type(MediaType.APPLICATION_JSON);
                        } else {
                            refundException.printStackTrace();
                            builder = Response.status(Status.SERVICE_UNAVAILABLE);
                        }
                    } else {
                        ErrorData data = new ErrorData();
                        data.error = "saleCount";
                        data.data = sales.size();

                        builder = Response.status(Status.BAD_REQUEST)
                            .entity(data)
                            .type(MediaType.APPLICATION_JSON);
                    }
                } else {
                    ErrorData data = new ErrorData();
                    data.error = "multiplePaypalTransactions";

                    builder = Response.status(Status.BAD_REQUEST)
                        .entity(data)
                        .type(MediaType.APPLICATION_JSON);
                }
            } else {
                exception.printStackTrace();
                builder = Response.status(Status.SERVICE_UNAVAILABLE);
            }
        } else {
            builder = Response.status(Status.BAD_REQUEST);
        }

        return builder.build();
    }

    @POST
    @Path("payment/execute")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response execute(
        @Context DecodedJWT jwt,
        @ValidEntity PaypalAuthorizationPayload payload
    ) {
        ResponseBuilder builder;

        PaymentExecution execution = new PaymentExecution();
        execution.setPayerId(payload.payerID);

        PayPalRESTException exception = null;
        Payment complete = null;
        Payment payment = new Payment();
        payment.setId(payload.paymentID);

        try {
            complete = payment.execute(this.context, execution);
        } catch (PayPalRESTException e) {
            exception = e;
        }

        if (exception == null) {
            Date date = new Date();

            com.paypal.api.payments.Transaction paypalTransaction = complete.getTransactions().get(0);
            List<com.paypal.api.payments.Item> items = paypalTransaction.getItemList().getItems();

            if ("created".equals(complete.getState()) || "approved".equals(complete.getState())) {
                List<Transaction> transactions = new ArrayList<>();

                Sale sale = paypalTransaction.getRelatedResources().stream()
                    .map(RelatedResources::getSale)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .get();

                for (com.paypal.api.payments.Item paypalItem: items) {
                    Item item = this.datastore.get(Item.class, new ObjectId(paypalItem.getSku()));

                    Transaction transaction = new Transaction();
                    transaction.itemId = item.id;
                    transaction.eventId = item.eventId;
                    transaction.organizationId = item.organizationId;
                    transaction.linkedId = payment.getId();
                    transaction.linkedObjectClass = "PaypalPayment";
                    transaction.organizationName = payload.organizationName;
                    transaction.paymentStatus = TransactionService.PAYMENT_EQUAL;
                    transaction.quantity = new Long(paypalItem.getQuantity());
                    transaction.amount = new Double(paypalItem.getPrice()) * transaction.quantity;
                    transaction.currency = paypalItem.getCurrency();
                    transaction.leaf = true;
                    transaction.timeCreated = date;

                    if ("pending".equals(sale.getState())) {
                        transaction.status = TransactionService.PENDING;
                    } else {
                        transaction.status = TransactionService.SETTLED;
                    }

                    if (jwt == null) {
                        Payer payer = complete.getPayer();
                        PayerInfo info = payer.getPayerInfo();

                        transaction.email = payload.email;

                        if (payload.firstName == null) {
                            transaction.given_name = info.getFirstName();
                        } else {
                            transaction.given_name = payload.firstName;
                        }

                        if (payload.lastName == null) {
                            transaction.family_name = info.getLastName();
                        } else {
                            transaction.family_name = payload.lastName;
                        }
                    } else {
                        transaction.user_id = jwt.getSubject();
                    }

                    transactions.add(transaction);
                }

                this.datastore.save(transactions);
                this.notificationService.notify(new ArrayList<>(transactions), "transaction.create");
                this.entityService.cascadeLastActivity(new ArrayList<>(transactions), date);

                Event event = this.datastore.get(Event.class, transactions.get(0).eventId);

                if (jwt == null) {
                    List<ObjectId> itemIds = transactions.stream()
                        .map(transaction -> transaction.itemId)
                        .collect(Collectors.toList());
                    List<Item> transactionItems = this.datastore.get(Item.class, itemIds).asList();

                    Map<String, Object> message = this.mandrillService.getTransactionConfirmation(
                        complete,
                        event,
                        transactionItems,
                        transactions
                    );

                    this.mandrillService.send(message);
                } else {
                    this.eventService.assignNumberToUserSafely(jwt.getSubject(), event).thenAcceptAsync((updatedEvent) -> {
                        this.notificationService.notify(
                            Arrays.asList(updatedEvent),
                            "event.update"
                        );
                    });
                }

                builder = Response.status(Status.CREATED);
            } else if ("failed".equals(complete.getState())) {
                Map<String, Object> body = new HashMap<>();
                body.put("failureReason", payment.getFailureReason());

                builder = Response.ok(body).type(MediaType.APPLICATION_JSON);
            } else {
                Map<String, Object> body = new HashMap<>();
                body.put("state", payment.getState());

                builder = Response.ok(body).type(MediaType.APPLICATION_JSON);
            }
        } else {
            exception.printStackTrace();
            builder = Response.status(Status.SERVICE_UNAVAILABLE);
        }

        return builder.build();
    }

    @POST
    @Path("{eventId}/order")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response order(
        @HeaderParam("Origin") String origin,
        @PathObject("eventId") Event event,
        @Context DecodedJWT jwt,
        @ValidEntity OrderData order
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
                order,
                aggregated
            );

            if (!(aggregated.profile instanceof PaypalPaymentProfile)) {
                ErrorData error = new ErrorData();
                error.error = "profile";
                dataError.add(error);
            }

            if (dataError.isEmpty()) {
                Map<ObjectId, ItemData> idToItemData = aggregated.itemData.stream()
                    .collect(
                        Collectors.toMap(data -> data.item.id, Function.identity())
                    );

                List<com.paypal.api.payments.Item> items = new ArrayList<>();

                double total = 0;

                for (EntityQuantity<ObjectId> entityQuantity: order.order) {
                    ItemData itemData = idToItemData.get(entityQuantity.entity);

                    com.paypal.api.payments.Item paypalItem = new com.paypal.api.payments.Item();
                    Item item = itemData.item;

                    paypalItem.setPrice(TransactionService.DEFAULT_DECIMAL_FORMAT.format(itemData.amount));
                    paypalItem.setQuantity(Integer.toString(entityQuantity.quantity));
                    paypalItem.setName(item.name);
                    paypalItem.setSku(item.id.toString());
                    paypalItem.setCurrency(aggregated.event.currency);

                    total += itemData.amount * entityQuantity.quantity;

                    items.add(paypalItem);
                }

                ItemList itemList = new ItemList();
                itemList.setItems(items);

                Details details = new Details();
                details.setSubtotal(TransactionService.DEFAULT_DECIMAL_FORMAT.format(total));
                details.setShipping("0");
                details.setTax("0");

                Amount amount = new Amount();
                amount.setCurrency(aggregated.event.currency);
                amount.setTotal(TransactionService.DEFAULT_DECIMAL_FORMAT.format(total));
                amount.setDetails(details);

                PaypalPaymentProfile paypalProfile = (PaypalPaymentProfile)aggregated.profile;

                Payee payee = new Payee();
                payee.setEmail(paypalProfile.email);

                com.paypal.api.payments.Transaction transaction = new com.paypal.api.payments.Transaction();
                transaction.setAmount(amount);
                transaction.setItemList(itemList);
                transaction.setDescription(event.name);
                transaction.setPayee(payee);

                List<com.paypal.api.payments.Transaction> transactions = new ArrayList<>();
                transactions.add(transaction);

                Payer payer = new Payer();
                payer.setPaymentMethod("paypal");

                RedirectUrls redirectUrls = new RedirectUrls();
                redirectUrls.setCancelUrl(origin);
                redirectUrls.setReturnUrl(origin);

                Payment payment = new Payment();
                payment.setRedirectUrls(redirectUrls);
                payment.setPayer(payer);
                payment.setIntent("sale");
                payment.setTransactions(transactions);

                PayPalRESTException exception = null;
                Payment newPayment = null;

                try {
                    newPayment = payment.create(this.context);
                } catch (PayPalRESTException e) {
                    exception = e;
                }

                if (exception == null) {
                    Map<String, Object> body = new HashMap<>();
                    body.put("id", newPayment.getId());

                    builder = Response.ok(body).type(MediaType.APPLICATION_JSON);
                } else {
                    exception.printStackTrace();
                    this.applicationService.saveException(exception, userId);
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

    @GET
    @Path("{id}/payment")
    @Authorized
    @HasPermission(id="id", clazz=Transaction.class, read=true)
    public Response getPayment(
        @PathObject("id") Transaction transaction,
        @Context DecodedJWT jwt
    ) {
        ResponseBuilder builder;

        if ("PaypalPayment".equals(transaction.linkedObjectClass)) {
            PayPalRESTException exception = null;
            Payment payment = null;

            try {
                payment = Payment.get(this.context, transaction.linkedId);
            } catch (PayPalRESTException e) {
                exception = e;
            }

            if (exception == null) {
                builder = Response.ok(payment.toJSON(), MediaType.APPLICATION_JSON);
            } else {
                exception.printStackTrace();
                builder = Response.status(Status.SERVICE_UNAVAILABLE);
            }
        } else {
            builder = Response.status(Status.BAD_REQUEST);
        }

        return builder.build();
    }

    @Log
    @POST
    @Path("webhook")
    public Response webhook(@Context HttpHeaders httpHeaders, String body) {
        MultivaluedMap<String, String> map = httpHeaders.getRequestHeaders();
        Map<String, String> headers = map.keySet()
            .stream()
            .collect(Collectors.toMap(Function.identity(), key -> map.getFirst(key)));

        Exception exception = null;
        boolean valid = false;

        APIContext context = new APIContext(
            this.configuration.paypal.clientId,
            this.configuration.paypal.clientSecret,
            this.configuration.paypal.mode
        );

        Map<String, String> config = new HashMap<>();
        config.put(Constants.PAYPAL_WEBHOOK_ID, this.configuration.paypal.webhookId);
        context.setConfigurationMap(config);

        try {
            valid = com.paypal.api.payments.Event.validateReceivedEvent(context, headers, body);
        } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException | PayPalRESTException e) {
            exception = e;
        }

        ResponseBuilder builder = Response.ok();

        if (exception == null) {
            if (valid) {
                Exception jsonException = null;
                JsonNode node = null;

                try {
                    node = this.mapper.readTree(body);
                } catch (IOException e) {
                    jsonException = e;
                }

                if (jsonException == null) {
                    boolean isCompleteSale = "PAYMENT.SALE.COMPLETED".equals(node.get("event_type").asText());
                    boolean isDeniedSale = "PAYMENT.SALE.DENIED".equals(node.get("event_type").asText());

                    if (isCompleteSale) {
                        String linkedId = node.get("resource").get("parent_payment").asText();

                        List<Transaction> transactions = this.datastore.createQuery(Transaction.class)
                            .field("linkedObjectClass").equal("PaypalPayment")
                            .field("linkedId").equal(linkedId)
                            .asList();

                        for (Transaction transaction: transactions) {
                            transaction.status = TransactionService.SETTLED;
                        }

                        this.datastore.save(transactions);
                        this.notificationService.notify(new ArrayList<>(transactions), "transaction.update");
                        this.entityService.cascadeLastActivity(new ArrayList<>(transactions), new Date());
                    } else if (isDeniedSale) {
                        String linkedId = node.get("resource").get("parent_payment").asText();

                        List<Transaction> transactions = this.datastore.createQuery(Transaction.class)
                            .field("linkedObjectClass").equal("PaypalPayment")
                            .field("linkedId").equal(linkedId)
                            .asList();

                        for (Transaction transaction: transactions) {
                            this.datastore.delete(transaction);
                        }

                        this.notificationService.notify(new ArrayList<>(transactions), "transaction.delete");
                        this.entityService.cascadeLastActivity(new ArrayList<>(transactions), new Date());
                    }
                } else {
                    jsonException.printStackTrace();
                    builder = Response.status(Status.SERVICE_UNAVAILABLE);
                }
            }
        } else {
            exception.printStackTrace();
        }

        return builder.build();
    }
}