package com.jivecake.api.resources;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;

import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.client.mgmt.filter.UserFilter;
import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.users.User;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.net.Request;
import com.jivecake.api.APIConfiguration;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.HasPermission;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.filter.ValidEntity;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.request.ErrorData;
import com.jivecake.api.service.Auth0Service;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.ItemService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.PermissionService;
import com.jivecake.api.service.TransactionService;

@Path("item")
@CORS
@Singleton
public class ItemResource {
    private final Auth0Service auth0Service;
    private final EventService eventService;
    private final TransactionService transactionService;
    private final NotificationService notificationService;
    private final EntityService entityService;
    private final Datastore datastore;
    private final APIConfiguration apiConfiguration;

    @Inject
    public ItemResource(
        Auth0Service auth0Service,
        EventService eventService,
        TransactionService transactionService,
        NotificationService notificationService,
        EntityService entityService,
        Datastore datastore,
        APIConfiguration apiConfiguration
    ) {
        this.auth0Service = auth0Service;
        this.eventService = eventService;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
        this.entityService = entityService;
        this.datastore = datastore;
        this.apiConfiguration = apiConfiguration;
    }

    @POST
    @Authorized
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{id}/purchase")
    public Response purchase(
        @PathObject("id") Item item,
        @Context DecodedJWT jwt,
        Transaction transaction
    ) throws InterruptedException, ExecutionException {
        ResponseBuilder builder;

        if (item == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else if (item.amount == 0) {
            Event event = this.datastore.get(Event.class, item.eventId);

            List<Transaction> countedTransactions = this.transactionService.getTransactionQueryForCounting()
                .field("itemId").equal(item.id)
                .asList();

            List<Transaction> transactionsForUser = countedTransactions.stream()
                .filter(subject -> jwt.getSubject().equals(subject.user_id))
                .collect(Collectors.toList());

            boolean maximumPerUserViolation = item.maximumPerUser != null &&
                transactionsForUser.size() > item.maximumPerUser + transaction.quantity;
            boolean maximumReached = item.totalAvailible != null &&
                countedTransactions.size() > item.totalAvailible + transaction.quantity;

            boolean activeViolation = item.status != ItemService.STATUS_ACTIVE ||
                event.status != EventService.STATUS_ACTIVE;

            if (event.currency == null) {
                builder = Response.status(Status.BAD_REQUEST)
                    .entity("{\"error\": \"currency\"}")
                    .type(MediaType.APPLICATION_JSON);
            } else if (activeViolation) {
                builder = Response.status(Status.BAD_REQUEST)
                    .entity("{\"error\": \"active\"}")
                    .type(MediaType.APPLICATION_JSON);
            } else if (maximumPerUserViolation) {
                builder = Response.status(Status.BAD_REQUEST)
                    .entity("{\"error\": \"userlimit\"}")
                    .type(MediaType.APPLICATION_JSON);
            } else if (maximumReached) {
                builder = Response.status(Status.BAD_REQUEST)
                    .entity("{\"error\": \"limit\"}")
                    .type(MediaType.APPLICATION_JSON);
            } else {
                Organization organization = this.datastore.get(Organization.class, event.organizationId);

                Date currentTime = new Date();

                Transaction userTransaction = new Transaction();
                userTransaction.user_id = jwt.getSubject();
                userTransaction.quantity = transaction.quantity;
                userTransaction.status = TransactionService.SETTLED;
                userTransaction.paymentStatus = TransactionService.PAYMENT_EQUAL;
                userTransaction.itemId = item.id;
                userTransaction.eventId = event.id;
                userTransaction.organizationId = organization.id;
                userTransaction.currency = event.currency;
                userTransaction.amount = 0;
                userTransaction.leaf = true;
                userTransaction.timeCreated = currentTime;

                this.datastore.save(userTransaction);

                Event updatedEvent = this.eventService.assignNumberToUserSafely(jwt.getSubject(), event).get();
                this.notificationService.notify(Arrays.asList(updatedEvent), "event.update");
                this.entityService.cascadeLastActivity(Arrays.asList(userTransaction), currentTime);

                builder = Response.ok(userTransaction).type(MediaType.APPLICATION_JSON);
            }
        } else {
            builder = Response.status(Status.BAD_REQUEST);
        }

        return builder.build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    @Path("/{id}/transaction")
    @HasPermission(id="id", clazz=Item.class, permission=PermissionService.WRITE)
    public Response createTransaction(
        @PathObject("id") Item item,
        @Context DecodedJWT jwt,
        @ValidEntity Transaction transaction
    ) {
        boolean isValid = transaction.amount >= 0;

        ResponseBuilder builder;

        if (isValid) {
            boolean totalAvailibleViolation = false;

            if (item.totalAvailible != null) {
                long count = this.transactionService.getTransactionQueryForCounting()
                    .field("itemId").equal(item.id)
                    .asList()
                    .stream()
                    .map(subject -> subject.quantity)
                    .reduce(0L, Long::sum);

                totalAvailibleViolation = count + transaction.quantity > item.totalAvailible;
            }

            if (totalAvailibleViolation) {
                ErrorData error = new ErrorData();
                error.error = "totalAvailible";
                error.data = item.totalAvailible;

                builder = Response.status(Status.BAD_REQUEST)
                    .entity(error)
                    .type(MediaType.APPLICATION_JSON);
            } else {
                boolean validUser = true;

                if (transaction.user_id != null) {
                    ManagementAPI api = new ManagementAPI(
                        this.apiConfiguration.oauth.domain,
                        this.auth0Service.getToken().get("access_token").asText()
                    );

                    Request<User> request = api.users().get(transaction.user_id, new UserFilter());

                    try {
                        request.execute();
                    } catch (Auth0Exception e) {
                        validUser = false;
                    }
                }

                if (validUser) {
                    Date currentTime = new Date();

                    transaction.leaf = true;
                    transaction.linkedId = null;
                    transaction.linkedObjectClass = null;
                    transaction.itemId = item.id;
                    transaction.eventId = item.eventId;
                    transaction.organizationId = item.organizationId;
                    transaction.status = TransactionService.PAYMENT_EQUAL;
                    transaction.paymentStatus = TransactionService.SETTLED;
                    transaction.timeCreated = currentTime;

                    Key<Transaction> key = ItemResource.this.datastore.save(transaction);
                    Transaction entity = ItemResource.this.datastore.get(Transaction.class, key.getId());

                    List<Object> results = this.entityService.cascadeLastActivity(
                        Arrays.asList(entity),
                        currentTime
                    );

                    ItemResource.this.notificationService.notify(
                        Arrays.asList(entity),
                        "transaction.create"
                    );

                    ItemResource.this.notificationService.notify(
                        results.stream()
                            .filter(object -> object instanceof Item)
                            .collect(Collectors.toList()),
                        "item.update"
                    );

                    ItemResource.this.notificationService.notify(
                        results.stream()
                            .filter(object -> object instanceof Event)
                            .collect(Collectors.toList()),
                        "event.update"
                    );

                    ItemResource.this.notificationService.notify(
                        results.stream()
                            .filter(object -> object instanceof Organization)
                            .collect(Collectors.toList()),
                        "organization.update"
                    );

                    builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
                } else {
                    ErrorData error = new ErrorData();
                    error.error = "user";

                    builder = Response.status(Status.BAD_REQUEST)
                        .entity(error)
                        .type(MediaType.APPLICATION_JSON);
                }
            }
        } else {
            builder = Response.status(Status.BAD_REQUEST);
        }

        return builder.build();
    }

    @GET
    @Path("/{id}")
    @Authorized
    @HasPermission(clazz=Item.class, id="id", permission=PermissionService.READ)
    public Response read(@PathObject("id") Item item) {
        return Response.ok(item).type(MediaType.APPLICATION_JSON).build();
    }

    @DELETE
    @Path("/{id}")
    @Authorized
    @HasPermission(clazz=Item.class, id="id", permission=PermissionService.WRITE)
    public Response delete(@PathObject("id") Item item) {
        ResponseBuilder builder;

        long transactionCount = this.datastore.createQuery(Transaction.class)
            .field("itemId").equal(item.id)
            .count();

        if (transactionCount == 0) {
            this.datastore.delete(Item.class, item.id);

            this.notificationService.notify(Arrays.asList(item), "item.delete");
            builder = Response.ok(item).type(MediaType.APPLICATION_JSON);
            this.entityService.cascadeLastActivity(Arrays.asList(item), new Date());
        } else {
            builder = Response.status(Status.BAD_REQUEST)
                .entity("{\"error\": \"transaction\"}")
                .type(MediaType.APPLICATION_JSON);
        }

        return builder.build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    @Authorized
    @HasPermission(clazz=Item.class, id="id", permission=PermissionService.WRITE)
    public Response update(@PathObject("id") Item searchedItem, @ValidEntity Item item) {
        Date currentDate = new Date();

        item.id = searchedItem.id;
        item.eventId = searchedItem.eventId;
        item.organizationId = searchedItem.organizationId;
        item.countAmounts = null;
        item.timeCreated = searchedItem.timeCreated;
        item.lastActivity = currentDate;
        item.timeUpdated = currentDate;

        if (item.timeAmounts != null) {
            if (item.timeAmounts.isEmpty()) {
                item.timeAmounts = null;
            } else {
                Collections.sort(item.timeAmounts, (first, second) -> first.after.compareTo(second.after));
            }
        }

        if (item.countAmounts != null) {
            if (item.countAmounts.isEmpty()) {
                item.countAmounts = null;
            } else {
                Collections.sort(item.countAmounts, (first, second) -> first.count - second.count);
            }
        }

        Key<Item> key = this.datastore.save(item);
        Item updatedItem = this.datastore.get(Item.class, key.getId());

        this.notificationService.notify(Arrays.asList(updatedItem), "item.update");
        this.entityService.cascadeLastActivity(Arrays.asList(updatedItem), new Date());

        return Response.ok(updatedItem).type(MediaType.APPLICATION_JSON).build();
    }
}