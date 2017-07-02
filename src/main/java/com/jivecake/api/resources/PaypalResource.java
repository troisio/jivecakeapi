package com.jivecake.api.resources;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.APIConfiguration;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.Log;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.model.Application;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.PaypalIPN;
import com.jivecake.api.model.PaypalPaymentProfile;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.request.AggregatedEvent;
import com.jivecake.api.request.EntityQuantity;
import com.jivecake.api.request.ItemData;
import com.jivecake.api.request.Paging;
import com.jivecake.api.request.PaypalAuthorization;
import com.jivecake.api.request.PaypalOrder;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.ItemService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.PermissionService;
import com.jivecake.api.service.TransactionService;
import com.paypal.api.payments.Amount;
import com.paypal.api.payments.Details;
import com.paypal.api.payments.ItemList;
import com.paypal.api.payments.Payee;
import com.paypal.api.payments.Payer;
import com.paypal.api.payments.Payment;
import com.paypal.api.payments.PaymentExecution;
import com.paypal.api.payments.RedirectUrls;
import com.paypal.base.rest.APIContext;
import com.paypal.base.rest.PayPalRESTException;

@Path("paypal")
@CORS
@Singleton
public class PaypalResource {
    private final Datastore datastore;
    private final PermissionService permissionService;
    private final ItemService itemService;
    private final EntityService entityService;
    private final ApplicationService applicationService;
    private final NotificationService notificationService;
    private final TransactionService transactionService;
    private final APIContext context;

    @Inject
    public PaypalResource(
        Datastore datastore,
        PermissionService permissionService,
        ItemService itemService,
        EntityService entityService,
        ApplicationService applicationService,
        NotificationService notificationService,
        TransactionService transactionService,
        APIConfiguration apiConfiguration
    ) {
        this.datastore = datastore;
        this.permissionService = permissionService;
        this.itemService = itemService;
        this.entityService = entityService;
        this.applicationService = applicationService;
        this.notificationService = notificationService;
        this.transactionService = transactionService;

        this.context = new APIContext(
            apiConfiguration.paypal.clientId,
            apiConfiguration.paypal.clientSecret,
            apiConfiguration.paypal.mode
        );
    }

    @POST
    @Path("payment/execute")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response execute(
        @Context DecodedJWT jwt,
        PaypalAuthorization authorization
    ) {
        ResponseBuilder builder;

        PaymentExecution execution = new PaymentExecution();
        execution.setPayerId(authorization.payerID);

        PayPalRESTException exception = null;
        Payment complete = null;
        Payment payment = new Payment();
        payment.setId(authorization.paymentID);

        try {
            complete = payment.execute(this.context, execution);
        } catch (PayPalRESTException e) {
            exception = e;
        }

        if (exception == null) {
            Date date = new Date();

            com.paypal.api.payments.Transaction paypalTransaction = complete.getTransactions().get(0);
            List<com.paypal.api.payments.Item> items = paypalTransaction.getItemList().getItems();

            if ("created".equals(complete.getState()) || "approved".equals(complete.getState()) || complete.getState() == null) {
                List<Transaction> transactions = new ArrayList<>();

                for (com.paypal.api.payments.Item paypalItem: items) {
                    Item item = this.datastore.get(Item.class, new ObjectId(paypalItem.getSku()));

                    Transaction transaction = new Transaction();
                    transaction.itemId = item.id;
                    transaction.eventId = item.eventId;
                    transaction.organizationId = item.organizationId;
                    transaction.linkedIdString = payment.getId();
                    transaction.linkedObjectClass = "PaypalPayment";
                    transaction.paymentStatus = TransactionService.PAYMENT_EQUAL;
                    transaction.status = TransactionService.SETTLED;
                    transaction.quantity = new Long(paypalItem.getQuantity());
                    transaction.amount = new Double(paypalItem.getPrice());
                    transaction.currency = paypalItem.getCurrency();
                    transaction.leaf = true;
                    transaction.timeCreated = date;

                    if (jwt != null) {
                        transaction.user_id = jwt.getSubject();
                    }

                    transactions.add(transaction);
                }

                this.datastore.save(transactions);
                this.notificationService.notify(new ArrayList<>(transactions), "transaction.create");
                this.entityService.cascadeLastActivity(new ArrayList<>(transactions), date);

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
            builder = Response.status(Status.SERVICE_UNAVAILABLE).entity(exception);
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
        PaypalOrder order
    ) {
        ResponseBuilder builder;

        if (event == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            Date date = new Date();

            Set<ObjectId> itemIds = order.itemData.stream()
                .map(data -> data.entity)
                .collect(Collectors.toSet());

            AggregatedEvent group = this.itemService.getAggregatedaEventData(event, this.transactionService, date);
            group.itemData = group.itemData.stream()
                .filter(data -> itemIds.contains(data.item.id))
                .collect(Collectors.toList());

            Map<ObjectId, ItemData> idToItemData = group.itemData.stream()
                .collect(
                    Collectors.toMap(data -> data.item.id, Function.identity())
                );

            boolean activeEvent = event.status == EventService.STATUS_ACTIVE;
            boolean validPaypalProfile = group.profile instanceof PaypalPaymentProfile;
            boolean uniqueItemsData = order.itemData.size() == group.itemData.size();
            boolean validRequest = activeEvent && validPaypalProfile && uniqueItemsData;

            if (validRequest) {
                List<com.paypal.api.payments.Item> items = new ArrayList<>();

                DecimalFormat format = new DecimalFormat();
                format.setMinimumFractionDigits(2);
                format.setMaximumFractionDigits(2);

                double total = 0;

                for (EntityQuantity<ObjectId> entityQuantity: order.itemData) {
                    ItemData itemData = idToItemData.get(entityQuantity.entity);

                    com.paypal.api.payments.Item paypalItem = new com.paypal.api.payments.Item();
                    Item item = itemData.item;

                    paypalItem.setPrice(format.format(itemData.amount));
                    paypalItem.setQuantity(Integer.toString(entityQuantity.quantity));
                    paypalItem.setName(item.name);
                    paypalItem.setSku(item.id.toString());
                    paypalItem.setCurrency(group.event.currency);

                    items.add(paypalItem);

                    total += itemData.amount * entityQuantity.quantity;
                }

                ItemList itemList = new ItemList();
                itemList.setItems(items);

                Details details = new Details();
                details.setSubtotal(format.format(total));
                details.setShipping("0");
                details.setTax("0");

                Amount amount = new Amount();
                amount.setCurrency(group.event.currency);
                amount.setTotal(format.format(total));
                amount.setDetails(details);

                PaypalPaymentProfile paypalProfile = (PaypalPaymentProfile)group.profile;

                Payee payee = new Payee();
                payee.setEmail(paypalProfile.email);

                com.paypal.api.payments.Transaction transaction = new com.paypal.api.payments.Transaction();
                transaction.setAmount(amount);
                transaction.setItemList(itemList);
                transaction.setDescription("JiveCake/" + event.name);
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
                    builder = Response.status(Status.SERVICE_UNAVAILABLE).entity(exception);
                }
            } else {
                builder = Response.status(Status.BAD_REQUEST);
            }
        }

        return builder.build();
    }

    @POST
    @Path("webhook")
    @Log
    public Response webhook(@Context HttpHeaders httpHeaders, String body) {
        MultivaluedMap<String, String> map = httpHeaders.getRequestHeaders();
        Map<String, String> headers = map.keySet()
            .stream()
            .collect(Collectors.toMap(Function.identity(), key -> map.getFirst(key)));

        Exception exception = null;
        boolean valid = false;

        try {
            valid = com.paypal.api.payments.Event.validateReceivedEvent(this.context, headers, body);
        } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException | PayPalRESTException e) {
            exception = e;
        }

        if (exception == null) {
            if (valid) {
            }
        } else {
            exception.printStackTrace();
        }

        return Response.ok().build();
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
}