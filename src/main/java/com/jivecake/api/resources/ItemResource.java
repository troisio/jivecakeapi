package com.jivecake.api.resources;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.inject.Inject;
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
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Criteria;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;

import com.fasterxml.jackson.databind.JsonNode;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.request.AggregatedItemGroup;
import com.jivecake.api.request.ItemData;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.Auth0Service;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.ItemService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.OrganizationService;
import com.jivecake.api.service.PaymentProfileService;
import com.jivecake.api.service.PermissionService;
import com.jivecake.api.service.TransactionService;

@Path("/item")
@CORS
public class ItemResource {
    private final Auth0Service auth0Service;
    private final ItemService itemService;
    private final EventService eventService;
    private final OrganizationService organizationService;
    private final PermissionService permissionService;
    private final TransactionService transactionService;
    private final NotificationService notificationService;

    @Inject
    public ItemResource(
        Auth0Service auth0Service,
        ItemService itemService,
        EventService eventService,
        PaymentProfileService paymentProfileService,
        OrganizationService organizationService,
        PermissionService permissionService,
        TransactionService transactionService,
        NotificationService notificationService
    ) {
        this.auth0Service = auth0Service;
        this.itemService = itemService;
        this.eventService = eventService;
        this.organizationService = organizationService;
        this.permissionService = permissionService;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
    }

    @GET
    @Path("/aggregated")
    public Response getAggregatedItemData(
        @QueryParam("id") List<ObjectId> ids,
        @QueryParam("eventId") List<ObjectId> eventIds,
        @QueryParam("organizationId") List<ObjectId> organizationIds,
        @Context JsonNode node
    ) {
        ResponseBuilder builder;

        if (ids.isEmpty() && eventIds.isEmpty() && organizationIds.isEmpty()) {
            builder = Response.status(Status.BAD_REQUEST);
        } else {
            Query<Item> query = this.itemService.query().disableValidation();

            if (!ids.isEmpty()) {
                query.field("id").in(ids);
            }

            if (!eventIds.isEmpty()) {
                query.field("eventId").in(eventIds);
            }

            if (!organizationIds.isEmpty()) {
                query.field("organizationId").in(organizationIds);
            }

            List<Item> items = query.asList();
            List<AggregatedItemGroup> groups = this.itemService.getAggregatedaGroupData(items, this.transactionService, new Date());

            groups = groups.stream()
                .filter(group -> group.parent.status == this.eventService.getActiveEventStatus())
                .collect(Collectors.toList());

            for (AggregatedItemGroup group: groups) {
                group.itemData = group.itemData.stream()
                    .filter(itemData -> itemData.item.status == this.itemService.getActiveItemStatus())
                    .collect(Collectors.toList());
            }

            if (node != null) {
                String user_id = node.get("sub").asText();

                for (AggregatedItemGroup group : groups) {
                    for (ItemData itemDatum: group.itemData) {
                        for (Transaction transaction: itemDatum.transactions) {
                            if (!user_id.equals(transaction.user_id)) {
                                transaction.user_id = null;
                            }
                        }
                    }
                }
            }

            builder = Response.ok(groups).type(MediaType.APPLICATION_JSON);
        }

        return builder.build();
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
        } else if (item.amount == null) {
            Event event = this.eventService.read(item.eventId);

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
                Transaction userTransaction = new Transaction();
                userTransaction.user_id = claims.get("sub").asText();
                userTransaction.quantity = transaction.quantity;
                userTransaction.status = this.transactionService.getPaymentCompleteStatus();
                userTransaction.itemId = item.id;
                userTransaction.currency = event.currency;
                userTransaction.amount = 0;
                userTransaction.timeCreated = new Date();

                Key<Transaction> key = this.transactionService.save(userTransaction);

                this.notificationService.notifyItemTransaction((ObjectId)key.getId());

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
        Query<Item> query = this.itemService.query().disableValidation();

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

        Paging<Item> entity = new Paging<>(query.asList(), query.count());
        ResponseBuilder builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
        return builder.build();
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

        Query<Item> query = this.itemService.query().disableValidation();

        if (!organizationIds.isEmpty()) {
            Set<ObjectId> organizationEventIds = this.eventService.query()
                .field("organizationId").in(organizationIds)
                .asList()
                .stream()
                .map(event -> event.id)
                .collect(Collectors.toSet());

            Criteria organizationIdCriteria = query.criteria("organizationId").in(organizationIds);
            Criteria eventIdCriteria = query.criteria("eventId").in(organizationEventIds);

            query.or(organizationIdCriteria, eventIdCriteria);
        }

        if (!ids.isEmpty()) {
            query.field("id").in(ids);
        }

        if (!statuses.isEmpty()) {
            query.field("status").in(statuses);
        }

        if (!eventIds.isEmpty()) {
            query.field("eventId").in(eventIds);
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

        List<Item> items = query.asList();

        boolean hasPermission = this.permissionService.has(
            claims.get("sub").asText(),
            items,
            this.organizationService.getReadPermission()
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
    public void createTransaction(
        @PathObject("id") Item item,
        @Context JsonNode claims,
        Transaction transaction,
        @Suspended AsyncResponse promise
    ) {
        if (item == null) {
            promise.resume(Response.status(Status.NOT_FOUND).build());
        } else {
            Event event = this.eventService.read(item.eventId);
            Organization organization = this.organizationService.read(event.organizationId);

            boolean hasPermission = this.permissionService.has(
                claims.get("sub").asText(),
                Arrays.asList(organization),
                this.organizationService.getWritePermission()
            );

            if (!this.transactionService.isValidTransaction(transaction)) {
                promise.resume(Response.status(Status.BAD_REQUEST).build());
            } else if (hasPermission) {
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
                    Transaction parentTransaction = this.transactionService.read(transaction.parentTransactionId);

                    if (parentTransaction == null) {
                        hasParentTransactionPermissionViolation = !this.permissionService.has(
                            claims.get("sub").asText(),
                            Arrays.asList(parentTransaction),
                            this.organizationService.getWritePermission()
                        );
                    } else {
                        hasParentTransactionPermissionViolation = true;
                    }
                }

                boolean hasStatusViolation = !this.transactionService.statuses.contains(transaction.status);

                if (hasStatusViolation) {
                    promise.resume(Response.status(Status.BAD_REQUEST).build());
                } else if (hasParentTransactionPermissionViolation) {
                    promise.resume(Response.status(Status.UNAUTHORIZED).build());
                } else if (eventActiveViolation) {
                    promise.resume(Response.status(Status.BAD_REQUEST).entity("{\"error\": \"inactive\"}").type(MediaType.APPLICATION_JSON).build());
                } else if (totalAvailibleViolation) {
                    promise.resume(
                        Response.status(Status.BAD_REQUEST).entity("{\"error\": \"totalAvailible\"}").type(MediaType.APPLICATION_JSON).build()
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
                            transaction.status = ItemResource.this.transactionService.getPaymentCompleteStatus();
                            transaction.linkedId = null;
                            transaction.linkedObjectClass = null;
                            transaction.itemId = item.id;
                            transaction.currency = event.currency;
                            transaction.timeCreated = new Date();

                            Key<Transaction> key = ItemResource.this.transactionService.save(transaction);

                            ItemResource.this.notificationService.notifyItemTransaction((ObjectId)key.getId());

                            Transaction entity = ItemResource.this.transactionService.read((ObjectId)key.getId());
                            promise.resume(
                                Response.ok(entity).type(MediaType.APPLICATION_JSON).build()
                            );
                        } else {
                            promise.resume(Response.status(Status.BAD_REQUEST).build());
                        }
                    });
                }
            } else {
                promise.resume(Response.status(Status.UNAUTHORIZED).build());
            }
        }
    }

    @GET
    @Path("/{id}")
    @Authorized
    public Response read(@PathObject("id") Item item, @Context JsonNode claims) {
        ResponseBuilder builder;

        if (item == null) {
            builder = Response.status(Status.UNAUTHORIZED);
        } else {
            Event event = this.eventService.read(item.eventId);
            Organization organization = this.organizationService.read(event.organizationId);

            boolean hasPermission = this.permissionService.hasAnyHierarchicalPermission(
                organization.id,
                claims.get("sub").asText(),
                this.organizationService.getReadPermission()
            );

            if (hasPermission) {
                builder = Response.ok(item).type(MediaType.APPLICATION_JSON);
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }

    @DELETE
    @Path("/{id}")
    @Authorized
    public Response delete(@PathObject("id") Item item, @Context JsonNode claims) {
        ResponseBuilder builder;

        if (item == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            Event event = this.eventService.read(item.eventId);
            Organization organization = this.organizationService.read(event.organizationId);

            boolean hasPermission = this.permissionService.hasAnyHierarchicalPermission(
                organization.id,
                claims.get("sub").asText(),
                this.organizationService.getWritePermission()
            );

            if (hasPermission) {
                long transactionCount = this.transactionService.query().field("itemId").equal(item.id).count();

                if (transactionCount == 0) {
                    Item deletedItem = this.itemService.delete(item.id);
                    builder = Response.ok(deletedItem).type(MediaType.APPLICATION_JSON);
                } else {
                    builder = Response.status(Status.BAD_REQUEST).entity("{\"error\": \"transaction\"}").type(MediaType.APPLICATION_JSON);
                }
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    @Authorized
    public Response update(@PathObject("id") Item searchedItem, @Context JsonNode claims, Item item) {
        ResponseBuilder builder;

        if (searchedItem == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            Event event = this.eventService.read(item.eventId);
            Organization organization = this.organizationService.read(event.organizationId);

            boolean hasPermission = this.permissionService.hasAnyHierarchicalPermission(
                organization.id,
                claims.get("sub").asText(),
                this.organizationService.getWritePermission()
            );

            if (hasPermission) {
                boolean hasTimeAndCountViolation = item.timeAmounts != null && !item.timeAmounts.isEmpty() &&
                                                   item.countAmounts != null && !item.countAmounts.isEmpty();

                boolean hasNegativeAmountViolation = item.timeAmounts != null && item.timeAmounts.stream().filter(t -> t.amount < 0).count() > 0 ||
                                                     item.countAmounts != null && item.countAmounts.stream().filter(t -> t.amount < 0).count() > 0;

                if (hasTimeAndCountViolation || hasNegativeAmountViolation) {
                    builder = Response.status(Status.BAD_REQUEST);
                } else {
                    item.id = searchedItem.id;
                    item.timeCreated = searchedItem.timeCreated;
                    item.timeUpdated = new Date();
                    item.eventId = searchedItem.eventId;

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

                    Key<Item> key = this.itemService.save(item);

                    Item entity = this.itemService.read((ObjectId)key.getId());
                    builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
                }
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }
}