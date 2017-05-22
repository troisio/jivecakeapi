package com.jivecake.api.resources;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;

import com.fasterxml.jackson.databind.JsonNode;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.HasPermission;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.ApplicationService;
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
    private final ItemService itemService;
    private final EventService eventService;
    private final PermissionService permissionService;
    private final TransactionService transactionService;
    private final NotificationService notificationService;
    private final EntityService entityService;
    private final Datastore datastore;

    @Inject
    public ItemResource(
        Auth0Service auth0Service,
        ItemService itemService,
        EventService eventService,
        PermissionService permissionService,
        TransactionService transactionService,
        NotificationService notificationService,
        EntityService entityService,
        Datastore datastore
    ) {
        this.auth0Service = auth0Service;
        this.itemService = itemService;
        this.eventService = eventService;
        this.permissionService = permissionService;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
        this.entityService = entityService;
        this.datastore = datastore;
    }

    @POST
    @Authorized
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{id}/purchase")
    public Response purchase(
        @PathObject("id") Item item,
        @Context JsonNode claims,
        Transaction transaction
    ) {
        ResponseBuilder builder;

        if (item == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else if (item.amount == 0) {
            Event event = this.datastore.get(Event.class, item.eventId);

            List<Transaction> countedTransactions = this.transactionService.getTransactionsForItemTotal(item.id);

            List<Transaction> transactionsForUser = countedTransactions.stream()
                .filter(subject -> claims.get("sub").asText().equals(subject.user_id))
                .collect(Collectors.toList());

            boolean maximumPerUserViolation = item.maximumPerUser != null &&
                transactionsForUser.size() > item.maximumPerUser + transaction.quantity;
            boolean maximumReached = item.totalAvailible != null &&
                countedTransactions.size() > item.totalAvailible + transaction.quantity;

            boolean activeViolation = item.status != this.itemService.getActiveItemStatus() ||
                event.status != this.eventService.getActiveEventStatus();

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
                userTransaction.user_id = claims.get("sub").asText();
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

                this.notificationService.notify(Arrays.asList(userTransaction), "transaction.create");
                this.entityService.cascadeLastActivity(Arrays.asList(userTransaction), currentTime);

                builder = Response.ok(userTransaction).type(MediaType.APPLICATION_JSON);
            }
        } else {
            builder = Response.status(Status.BAD_REQUEST);
        }

        return builder.build();
    }

    /*
     * TODO:
     *
     * This resource is currently being used to retrieve items which are associated with
     * transactions, to display on the individual user transaction page, to accommodate this
     * the 'active' flag has been taken off the query.
     *
     * The 'active' field check needs to be put back on and another resource needs to be
     * modified or created to retrieve user-authorized item data even when the item is not active
     * Currently, API calls can access item data that have events that are not active or if the item
     * being queried itself is not active, no bueno
     *
     * The resulting resource which retrieves user item data should only return the minimum amount
     * of information necessary to use for user interfaces
     * */
    @GET
    @Path("/search")
    public Response search(
        @QueryParam("name") String name,
        @QueryParam("id") List<ObjectId> ids,
        @QueryParam("status") List<Integer> statuses,
        @QueryParam("eventId") List<ObjectId> eventIds,
        @QueryParam("organizationId") List<ObjectId> organizationIds,
        @QueryParam("timeStartGreaterThan") Long timeStartGreaterThan,
        @QueryParam("timeStartLessThan") Long timeStartLessThan,
        @QueryParam("timeEndGreaterThan") Long timeEndGreaterThan,
        @QueryParam("timeEndLessThan") Long timeEndLessThan
    ) {
        Query<Item> query = this.datastore.createQuery(Item.class);

        if (!ids.isEmpty()) {
            query.field("id").in(ids);
        }

        if (!statuses.isEmpty()) {
            query.field("status").in(statuses);
        }

        if (name != null) {
            query.field("name").startsWithIgnoreCase(name);
        }

        if (!eventIds.isEmpty()) {
            query.field("eventId").in(eventIds);
        }

        if (timeStartGreaterThan != null) {
            query.field("timeStart").greaterThan(new Date(timeStartGreaterThan));
        }

        if (timeStartLessThan != null) {
            query.field("timeStart").lessThan(new Date(timeStartLessThan));
        }

        if (timeEndGreaterThan != null) {
            query.field("timeEnd").greaterThan(new Date(timeEndGreaterThan));
        }

        if (timeEndLessThan != null) {
            query.field("timeEnd").lessThan(new Date(timeEndLessThan));
        }

        FindOptions options = new FindOptions();
        options.limit(ApplicationService.LIMIT_DEFAULT);

        Paging<Item> entity = new Paging<>(query.asList(options), query.count());
        return Response.ok(entity).type(MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Authorized
    public Response search(
        @QueryParam("id") List<ObjectId> ids,
        @QueryParam("status") List<Integer> statuses,
        @QueryParam("eventId") List<ObjectId> eventIds,
        @QueryParam("organizationId") List<ObjectId> organizationIds,
        @QueryParam("name") String name,
        @QueryParam("limit") Integer limit,
        @QueryParam("offset") Integer offset,
        @QueryParam("order") String order,
        @Context JsonNode claims
    ) {
        ResponseBuilder builder;

        Query<Item> query = this.datastore.createQuery(Item.class);

        if (!ids.isEmpty()) {
            query.field("id").in(ids);
        }

        if (!statuses.isEmpty()) {
            query.field("status").in(statuses);
        }

        if (!eventIds.isEmpty()) {
            query.field("eventId").in(eventIds);
        }

        if (!organizationIds.isEmpty()) {
            query.field("organizationId").in(organizationIds);
        }

        if (name != null) {
            query.field("name").startsWithIgnoreCase(name);
        }

        FindOptions options = new FindOptions();

        if (limit != null && limit > -1) {
            options.limit(limit);
        }

        if (offset != null && offset > -1) {
            options.skip(offset);
        }

        if (order != null) {
            query.order(order);
        }

        List<Item> items = query.asList(options);

        boolean hasPermission = this.permissionService.has(
            claims.get("sub").asText(),
            items,
            PermissionService.READ
        );

        if (hasPermission) {
            Paging<Item> entity = new Paging<>(items, query.count());
            builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    @Path("/{id}/transaction")
    @HasPermission(id="id", clazz=Item.class, permission=PermissionService.WRITE)
    public void createTransaction(
        @PathObject("id") Item item,
        @Context JsonNode claims,
        Transaction transaction,
        @Suspended AsyncResponse promise
    ) {
        transaction.status = TransactionService.PAYMENT_EQUAL;
        transaction.paymentStatus = TransactionService.SETTLED;
        boolean isValid = this.transactionService.isValidTransaction(transaction) && transaction.amount >= 0;

        if (isValid) {
            Event event = this.datastore.get(Event.class, item.eventId);
            boolean totalAvailibleViolation;

            if (item.totalAvailible == null) {
                totalAvailibleViolation = false;
            } else {
                long count = this.transactionService.getTransactionsForItemTotal(item.id)
                    .stream()
                    .map(subject -> subject.quantity)
                    .reduce(0L, Long::sum);

                totalAvailibleViolation = count > item.totalAvailible;
            }

            boolean eventActiveViolation = event.status == this.eventService.getInactiveEventStatus();
            boolean hasParentTransactionPermissionViolation = false;

            if (transaction.parentTransactionId != null) {
                Transaction parentTransaction = this.datastore.get(Transaction.class, transaction.parentTransactionId);

                if (parentTransaction == null) {
                    hasParentTransactionPermissionViolation = !this.permissionService.has(
                        claims.get("sub").asText(),
                        Arrays.asList(parentTransaction),
                        PermissionService.WRITE
                    );
                } else {
                    hasParentTransactionPermissionViolation = true;
                }
            }

            if (hasParentTransactionPermissionViolation) {
                promise.resume(Response.status(Status.UNAUTHORIZED).build());
            } else if (eventActiveViolation) {
                promise.resume(
                    Response.status(Status.BAD_REQUEST)
                        .entity("{\"error\": \"inactive\"}")
                        .type(MediaType.APPLICATION_JSON)
                        .build()
                );
            } else if (totalAvailibleViolation) {
                promise.resume(
                    Response.status(Status.BAD_REQUEST)
                        .entity("{\"error\": \"totalAvailible\"}")
                        .type(MediaType.APPLICATION_JSON).build()
                );
            } else {
                CompletableFuture<Boolean> hasValidUserIdPromise = new CompletableFuture<>();

                if (transaction.user_id == null) {
                    hasValidUserIdPromise.complete(true);
                } else {
                    this.auth0Service.getUser(transaction.user_id).submit(new InvocationCallback<Response>(){
                        @Override
                        public void completed(Response response) {
                            hasValidUserIdPromise.complete(response.getStatus() == 200);
                        }

                        @Override
                        public void failed(Throwable throwable) {
                            hasValidUserIdPromise.completeExceptionally(throwable);
                        }
                    });
                }

                hasValidUserIdPromise.thenAcceptAsync(hasValidUserId -> {
                    if (hasValidUserId) {
                        Date currentTime = new Date();

                        transaction.leaf = true;
                        transaction.linkedId = null;
                        transaction.linkedObjectClass = null;
                        transaction.itemId = item.id;
                        transaction.eventId = item.eventId;
                        transaction.organizationId = item.organizationId;
                        transaction.timeCreated = currentTime;

                        Key<Transaction> key = ItemResource.this.datastore.save(transaction);
                        Transaction entity = ItemResource.this.datastore.get(Transaction.class, key.getId());
                        this.entityService.cascadeLastActivity(Arrays.asList(entity), currentTime);
                        ItemResource.this.notificationService.notify(Arrays.asList(entity), "transaction.create");

                        promise.resume(
                            Response.ok(entity).type(MediaType.APPLICATION_JSON).build()
                        );
                    } else {
                        promise.resume(Response.status(Status.BAD_REQUEST)
                            .entity("{\"error\": \"user\"}")
                            .type(MediaType.APPLICATION_JSON).build());
                    }
                }).exceptionally(e -> {
                    promise.resume(e);
                    return null;
                });
            }
        } else {
            promise.resume(Response.status(Status.BAD_REQUEST).build());
        }
    }

    @GET
    @Path("/{id}")
    @Authorized
    @HasPermission(clazz=Item.class, id="id", permission=PermissionService.READ)
    public Response read(@PathObject("id") Item item, @Context JsonNode claims) {
        return Response.ok(item).type(MediaType.APPLICATION_JSON).build();
    }

    @DELETE
    @Path("/{id}")
    @Authorized
    @HasPermission(clazz=Item.class, id="id", permission=PermissionService.WRITE)
    public Response delete(@PathObject("id") Item item, @Context JsonNode claims) {
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
    public Response update(@PathObject("id") Item searchedItem, @Context JsonNode claims, Item item) {
        ResponseBuilder builder;

        boolean isValid = this.itemService.isValid(item);

        if (isValid) {
            Date currentDate = new Date();

            item.id = searchedItem.id;
            item.eventId = searchedItem.eventId;
            item.organizationId = searchedItem.organizationId;
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

            builder = Response.ok(updatedItem).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.BAD_REQUEST);
        }

        return builder.build();
    }
}