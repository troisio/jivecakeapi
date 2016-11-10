package com.jivecake.api.resources;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
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
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.CriteriaContainerImpl;
import org.mongodb.morphia.query.Query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.PaypalIPN;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.Auth0Service;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.ItemService;
import com.jivecake.api.service.MappingService;
import com.jivecake.api.service.OrganizationService;
import com.jivecake.api.service.PermissionService;
import com.jivecake.api.service.TransactionService;

@Path("/transaction")
@CORS
public class TransactionResource {
    private final TransactionService transactionService;
    private final ItemService itemService;
    private final EventService eventService;
    private final MappingService mappingService;
    private final OrganizationService organizationService;
    private final PermissionService permissionService;
    private final Auth0Service auth0Service;
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    public TransactionResource(
        ItemService itemService,
        EventService eventService,
        TransactionService transactionService,
        MappingService mappingService,
        OrganizationService organizationService,
        PermissionService permissionService,
        Auth0Service auth0Service
    ) {
        this.transactionService = transactionService;
        this.itemService = itemService;
        this.eventService = eventService;
        this.mappingService = mappingService;
        this.organizationService = organizationService;
        this.permissionService = permissionService;
        this.auth0Service = auth0Service;
    }

    @GET
    @Authorized
    public void search(
        @QueryParam("organizationId") List<ObjectId> organizationIds,
        @QueryParam("eventId") List<ObjectId> eventIds,
        @QueryParam("eventName") String eventName,
        @QueryParam("eventStatus") Set<Integer> eventStatuses,
        @QueryParam("itemTimeStartGreaterThan") Long itemTimeStartGreaterThan,
        @QueryParam("itemTimeStartLessThan") Long itemTimeStartLessThan,
        @QueryParam("itemTimeEndGreaterThan") Long itemTimeEndGreaterThan,
        @QueryParam("itemTimeEndLessThan") Long itemTimeEndLessThan,
        @QueryParam("itemId") List<ObjectId> itemIds,
        @QueryParam("user_id") Set<String> userIds,
        @QueryParam("id") List<ObjectId> ids,
        @QueryParam("parentTransactionId") List<ObjectId> parentTransactionIds,
        @QueryParam("timeCreatedLessThan") Long timeCreatedLessThan,
        @QueryParam("timeCreatedGreaterThan") Long timeCreatedGreaterThan,
        @QueryParam("given_name") String given_name,
        @QueryParam("family_name") String family_name,
        @QueryParam("email") String email,
        @QueryParam("amountLessThan") Double amountLessThan,
        @QueryParam("amountGreaterThan") Double amountGreaterThan,
        @QueryParam("leaf") Boolean leaf,
        @QueryParam("status") List<Integer> statuses,
        @QueryParam("text") String text,
        @QueryParam("limit") Integer limit,
        @QueryParam("offset") Integer offset,
        @QueryParam("order") String order,
        @Context JsonNode claims,
        @Suspended AsyncResponse futureResponse
    ) {
        CompletableFuture<JsonNode> userSearchFuture;

        if (text == null) {
            userSearchFuture = new CompletableFuture<>();
            userSearchFuture.complete(null);
        } else {
            userSearchFuture = this.auth0Service.searchEmailOrNames(text);
        }

        userSearchFuture.thenAcceptAsync(auth0Users -> {
            Query<Transaction> query = this.transactionService.query();

            Set<ObjectId> idsFilter = this.mappingService.getItemTransactionIds(organizationIds, eventIds, itemIds);
            idsFilter.addAll(ids);

            boolean hasIdFilter = !ids.isEmpty() ||
                                  !organizationIds.isEmpty() ||
                                  !eventIds.isEmpty() ||
                                  !itemIds.isEmpty();

            if (hasIdFilter) {
                query.field("id").in(idsFilter);
            }

            if (!statuses.isEmpty()) {
                query.field("status").in(statuses);
            }

            if (!parentTransactionIds.isEmpty()) {
                query.field("parentTransactionId").in(parentTransactionIds);
            }

            if (!userIds.isEmpty()) {
                query.field("user_id").in(userIds);
            }

            if (timeCreatedGreaterThan != null) {
                query.field("timeCreated").greaterThan(new Date(timeCreatedGreaterThan));
            }

            if (timeCreatedLessThan != null) {
                query.field("timeCreated").lessThan(new Date(timeCreatedLessThan));
            }

            if (email != null) {
                query.field("email").equal(email);
            }

            if (given_name != null) {
                query.field("given_name").startsWithIgnoreCase(given_name);
            }

            if (family_name != null) {
                query.field("family_name").startsWithIgnoreCase(family_name);
            }

            if (amountGreaterThan != null) {
                query.field("amount").greaterThan(amountGreaterThan);
            }

            if (amountLessThan != null) {
                query.field("amount").lessThan(amountLessThan);
            }

            if (limit != null && limit > -1) {
                query.limit(limit);
            }

            if (offset != null && offset > -1) {
                query.offset(offset);
            }

            if (order != null) {
                query.order(order);
            }

            if (text != null || auth0Users != null) {
                Collection<CriteriaContainerImpl> textSearchCritera = new ArrayList<>();

                if (auth0Users != null) {
                    List<String> auth0UserIds = new ArrayList<>();
                    auth0Users.forEach(node -> auth0UserIds.add(node.get("user_id").asText()));

                    textSearchCritera.add(query.criteria("user_id").in(auth0UserIds));
                }

                if (text != null) {
                    textSearchCritera.add(query.criteria("email").startsWithIgnoreCase(text));
                    textSearchCritera.add(query.criteria("given_name").startsWithIgnoreCase(text));
                    textSearchCritera.add(query.criteria("family_name").startsWithIgnoreCase(text));
                }

                query.and(
                    query.or(textSearchCritera.toArray(new CriteriaContainerImpl[]{}))
                );
            }

            boolean hasItemTimeSearch = itemTimeStartGreaterThan != null ||
                                        itemTimeStartLessThan != null ||
                                        itemTimeEndLessThan != null ||
                                        itemTimeEndGreaterThan != null ||
                                        text != null;
            boolean hasEventSearch = !eventStatuses.isEmpty() ||
                                     eventName != null;

            if (hasItemTimeSearch || hasEventSearch) {
                List<Transaction> transactions = query.asList();

                Set<Object> itemIdsQuery = transactions.stream()
                   .map(transaction -> transaction.itemId)
                   .collect(Collectors.toSet());

                Query<Item> itemQuery = this.itemService.query().field("id").in(itemIdsQuery);

                if (itemTimeStartGreaterThan != null) {
                    itemQuery.field("timeStart").greaterThan(new Date(itemTimeStartGreaterThan));
                }

                if (itemTimeStartLessThan != null) {
                    itemQuery.field("timeStart").lessThan(new Date(itemTimeStartLessThan));
                }

                if (itemTimeEndLessThan != null) {
                    itemQuery.field("timeEnd").lessThan(new Date(itemTimeEndLessThan));
                }

                if (itemTimeEndGreaterThan != null) {
                    itemQuery.field("timeEnd").greaterThan(new Date(itemTimeEndGreaterThan));
                }

                List<Item> queriedItems = itemQuery.asList();
                List<Item> filteredItems;

                if (hasEventSearch) {
                    Query<Event> eventQuery = this.eventService.query();

                    if (!eventStatuses.isEmpty()) {
                        eventQuery.field("status").in(eventStatuses);
                    }

                    if (eventName != null) {
                        eventQuery.field("name").startsWithIgnoreCase(eventName);
                    }

                    Set<ObjectId> filteredEventIds = eventQuery.asList()
                        .stream()
                        .map(event -> event.id)
                        .collect(Collectors.toSet());

                    filteredItems = queriedItems.stream()
                        .filter(item -> filteredEventIds.contains(item.eventId))
                        .collect(Collectors.toList());
                } else {
                    filteredItems = queriedItems;
                }

                List<ObjectId> queriedItemIds = filteredItems.stream()
                    .map(item -> item.id)
                    .collect(Collectors.toList());

                query.field("itemId").in(queriedItemIds);
            }

            if (Objects.equals(leaf, true)) {
                List<Transaction> transactions = query.asList();
                List<List<Transaction>> forest = this.transactionService.getTransactionForest(transactions);

                List<ObjectId> leafIds = forest.stream()
                    .filter(lineage -> lineage.size() == 1)
                    .map(lineage -> lineage.get(0).id)
                    .collect(Collectors.toList());

                query.field("id").in(leafIds);
            }

            String user_id = claims.get("sub").asText();

            List<Transaction> transactions = query.asList();

            boolean hasUserIdPermission = userIds.size() == 1 && userIds.contains(user_id);
            boolean hasPermission = this.permissionService.has(
                user_id,
                transactions,
                this.organizationService.getReadPermission()
            );

            ResponseBuilder builder;

            if (hasUserIdPermission || hasPermission) {
                Paging<Transaction> entity = new Paging<>(transactions, query.countAll());
                builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }

            futureResponse.resume(builder.build());
        }).exceptionally((exception) -> {
            futureResponse.resume(exception);
            return null;
        });
    }

    @POST
    @Authorized
    @Path("/{id}/transfer/{user_id}")
    public void transfer(
        @PathObject("id") Transaction transaction,
        @PathParam("user_id") String user_id,
        @Context JsonNode claims,
        @Suspended AsyncResponse promise
    ) {
        if (transaction == null) {
            promise.resume(Response.status(Status.NOT_FOUND).build());
        } else if (transaction.status != this.transactionService.getPaymentCompleteStatus()) {
            promise.resume(Response.status(Status.BAD_REQUEST).build());
        } else {
            Item item = this.itemService.read(transaction.itemId);
            Event event = this.eventService.read(item.eventId);

            String requester = claims.get("sub").asText();

            boolean hasTransferTimeViolation = false;

            Transaction parentTransaction = this.transactionService.read(transaction.parentTransactionId);

            if (parentTransaction != null && parentTransaction.status == this.transactionService.getTransferredStatus()) {
                hasTransferTimeViolation = event.minimumTimeBetweenTransactionTransfer > new Date().getTime() - parentTransaction.timeCreated.getTime();
            }

            if (requester.equals(user_id)) {
                promise.resume(Response.status(Status.BAD_REQUEST).build());
            } else if (event.status == this.eventService.getInactiveEventStatus()) {
                promise.resume(Response.status(Status.BAD_REQUEST).entity("{\"error\": \"eventInactive\"}").build());
            } else if (hasTransferTimeViolation) {
                String body = String.format("{\"error\": \"minimumTimeBetweenPassTransfer\", data: %d}", event.minimumTimeBetweenTransactionTransfer);
                promise.resume(Response.status(Status.BAD_REQUEST).entity(body).build());
            } else {
                boolean hasPermission = requester.equals(transaction.user_id) ||
                    this.permissionService.has(
                        requester,
                        Arrays.asList(transaction),
                        this.organizationService.getWritePermission()
                    );

                if (hasPermission) {
                    this.auth0Service.queryUsers(String.format("user_id: \"%s\"", user_id), new InvocationCallback<Response>() {
                        @Override
                        public void completed(Response response) {
                            JsonNode[] users;

                            Object entity = response.getEntity();
                            String body = entity instanceof String ? (String)entity : response.readEntity(String.class);

                            try {
                                users = TransactionResource.this.mapper.readValue(body, JsonNode[].class);
                            } catch (IOException e) {
                                users = null;
                                promise.resume(response);
                            }

                            if (users != null) {
                                if (users.length == 0) {
                                    promise.resume(Response.status(Status.NOT_FOUND).build());
                                } else {
                                    Transaction transfer = new Transaction();
                                    transfer.id = new ObjectId();
                                    transfer.parentTransactionId = transaction.id;
                                    transfer.itemId = transaction.itemId;
                                    transfer.user_id = transaction.user_id;
                                    transfer.status = TransactionResource.this.transactionService.getTransferredStatus();
                                    transfer.timeCreated = new Date();

                                    Transaction completed = new Transaction();
                                    completed.itemId = transaction.itemId;
                                    completed.parentTransactionId = transfer.id;
                                    completed.user_id = users[0].get("user_id").asText();
                                    completed.status = TransactionResource.this.transactionService.getPaymentCompleteStatus();
                                    completed.timeCreated = new Date();

                                    TransactionResource.this.transactionService.save(transfer);
                                    TransactionResource.this.transactionService.save(completed);

                                    Transaction transferAfter = TransactionResource.this.transactionService.read(transfer.id);
                                    Transaction completedAfter = TransactionResource.this.transactionService.read(completed.id);

                                    promise.resume(
                                        Response.ok(Arrays.asList(transferAfter, completedAfter)).type(MediaType.APPLICATION_JSON).build()
                                    );
                                }
                            }
                        }

                        @Override
                        public void failed(Throwable throwable) {
                            promise.resume(throwable);
                        }
                    });
                } else {
                    promise.resume(Response.status(Status.UNAUTHORIZED).build());
                }
            }
        }
    }

    @GET
    @Path("/search")
    public Response publicSearch(
        @QueryParam("id") List<ObjectId> ids,
        @QueryParam("eventId") List<ObjectId> eventIds,
        @QueryParam("itemId") List<ObjectId> itemIds,
        @QueryParam("status") List<Integer> statuses,
        @QueryParam("leaf") Boolean leaf
    ) {
        Query<Transaction> query = this.transactionService.query()
            .retrievedFields(true, "id", "itemId", "status", "parentTransactionId");

        Set<ObjectId> idsFilter = this.mappingService.getItemTransactionIds(new HashSet<>(), eventIds, itemIds);
        idsFilter.addAll(ids);

        if (!eventIds.isEmpty() || !itemIds.isEmpty() || !ids.isEmpty()) {
            query.field("id").in(idsFilter);
        }

        if (!statuses.isEmpty()) {
            query.field("status").in(statuses);
        }

        List<Transaction> transactions = query.asList();

        if (leaf != null && leaf) {
            List<List<Transaction>> forest = this.transactionService.getTransactionForest(transactions);

            transactions = forest.stream()
                .filter(lineage -> lineage.size() == 1)
                .map(lineage -> lineage.get(0))
                .collect(Collectors.toList());
        }

        Paging<Transaction> entity = new Paging<>(transactions, query.countAll());
        ResponseBuilder builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);

        return builder.build();
    }

    @GET
    @Path("/user")
    @Authorized
    public void searchUsers(
        @QueryParam("id") List<ObjectId> ids,
        @Context JsonNode claims,
        @Suspended AsyncResponse promise
    ) {
        List<Transaction> transactions = this.transactionService.query()
            .field("id").in(ids)
            .asList();

        Set<String> user_ids = transactions.stream()
            .filter(transaction -> transaction.user_id != null)
            .map(transaction -> transaction.user_id)
            .collect(Collectors.toSet());

        String requesterId = claims.get("sub").asText();

        long transactionsNotBelongingToRequester = transactions.stream()
            .filter(transaction -> !requesterId.equals(transaction.user_id))
            .count();

        boolean hasPermission = this.permissionService.has(
            claims.get("sub").asText(),
            transactions,
            this.organizationService.getReadPermission()
        );

        if (!transactions.isEmpty() && (hasPermission || transactionsNotBelongingToRequester == 0)) {
            if (user_ids.isEmpty()) {
                promise.resume(Response.ok().entity(new ArrayList<>()).type(MediaType.APPLICATION_JSON).build());
            } else {
                String query = user_ids.stream()
                    .map(id -> String.format("user_id: \"%s\"", id))
                    .collect(Collectors.joining(" OR "));

                this.auth0Service.queryUsers(query, new InvocationCallback<Response>() {
                    @Override
                    public void completed(Response response) {
                        promise.resume(response);
                    }

                    @Override
                    public void failed(Throwable throwable) {
                        promise.resume(throwable);
                    }
                });
            }
        } else {
            promise.resume(Response.status(Status.UNAUTHORIZED).build());
        }
    }

    @GET
    @Path("/{id}")
    @Authorized
    public Response read(@PathObject("id") Transaction transaction, @Context JsonNode claims) {
        ResponseBuilder builder;
        String userId = claims.get("sub").asText();

        if (transaction == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            boolean hasPermission;

            if (userId.equals(transaction.user_id)) {
                hasPermission = true;
            } else {
                hasPermission = this.permissionService.has(
                    userId,
                    Arrays.asList(transaction.id),
                    this.organizationService.getReadPermission()
                );
            }

            if (hasPermission) {
                builder = Response.ok(transaction).type(MediaType.APPLICATION_JSON);
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }

    @GET
    @Path("/excel")
    public void downloadExcel(
        @QueryParam("organizationId") List<ObjectId> organizationIds,
        @QueryParam("eventId") List<ObjectId> eventIds,
        @QueryParam("itemId") List<ObjectId> itemIds,
        @QueryParam("id") List<ObjectId> ids,
        @QueryParam("Authorization") String authorization,
        @Suspended AsyncResponse promise
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            promise.resume(Response.status(Status.BAD_REQUEST).build());
        } else {
            Set<ObjectId> aggregatedOrganizationIds = this.mappingService.getOrganizationIds(ids, itemIds, eventIds);
            aggregatedOrganizationIds.addAll(aggregatedOrganizationIds);

            String token = authorization.substring("Bearer ".length());
            Map<String, Object> claims = this.auth0Service.getClaimsFromToken(token);

            boolean hasPermission = this.permissionService.hasAllHierarchicalPermission(
                (String)claims.get("sub"),
                this.organizationService.getReadPermission(),
                aggregatedOrganizationIds
            );

            if (hasPermission) {
                File file;

                try {
                    file = File.createTempFile("ItemTransaction-" + new Date().getTime(), ".xlsx");
                } catch (IOException e) {
                    promise.resume(e);
                    file = null;
                }

                File writeFile = file;

                if (file != null) {
                    Set<ObjectId> idsFilter = this.mappingService.getItemTransactionIds(organizationIds, eventIds, itemIds);
                    idsFilter.addAll(ids);

                    List<Transaction> transactions = this.transactionService.query()
                        .field("id").in(idsFilter)
                        .asList();

                    String userQuery = transactions.stream()
                        .filter(transaction -> transaction.user_id != null)
                        .map(transaction -> String.format("user_id: \"%s\"", transaction.user_id))
                        .collect(Collectors.joining(" OR "));

                    this.auth0Service.queryUsers(userQuery, new InvocationCallback<Response>() {
                        @Override
                        public void completed(Response response) {
                            List<JsonNode> users;

                            try {
                                users = Arrays.asList(
                                    TransactionResource.this.mapper.readValue(response.readEntity(String.class), JsonNode[].class)
                                );
                            } catch (IOException e) {
                                users = null;
                                promise.resume(e);
                            }

                            if (users != null) {
                                try {
                                    TransactionResource.this.transactionService.writeToExcel(transactions, users, writeFile);
                                    Response result = Response.ok(writeFile)
                                        .type("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                        .header("Content-Disposition", String.format("attachment; filename=%s", writeFile.getName()))
                                        .build();

                                    promise.resume(result);
                                } catch (IOException e) {
                                    promise.resume(e);
                                }
                            }
                        }

                        @Override
                        public void failed(Throwable throwable) {
                            promise.resume(throwable);
                        }
                    });

                }
            } else {
                promise.resume(Response.status(Status.UNAUTHORIZED).build());
            }
        }
    }

    @POST
    @Path("/{id}/revoke")
    @Authorized
    public Response revoke(@PathObject("id") Transaction transaction, @Context JsonNode claims) {
        ResponseBuilder builder;

        if (transaction == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            Item item = this.itemService.read(transaction.itemId);
            Event event = this.eventService.read(item.eventId);
            Organization organization = this.organizationService.read(event.organizationId);

            boolean hasPermission = this.permissionService.has(
                claims.get("sub").asText(),
                Arrays.asList(organization),
                this.organizationService.getWritePermission()
            );

            boolean targetIsCompleted = transaction.status == this.transactionService.getPaymentCompleteStatus();
            boolean hasChildTransaction = this.transactionService.query()
                .field("parentTransactionId").equal(transaction.id)
                .countAll() > 0;

            if (hasChildTransaction) {
                builder = Response.status(Status.BAD_REQUEST).entity("{\"error\": \"childtransaction\"}").type(MediaType.APPLICATION_JSON);
            } else {
                if (targetIsCompleted) {
                    if (hasPermission) {
                        Transaction revokedTransaction = new Transaction();
                        revokedTransaction.itemId = transaction.itemId;
                        revokedTransaction.status = this.transactionService.getRevokedStatus();
                        revokedTransaction.user_id = transaction.user_id;
                        revokedTransaction.parentTransactionId = transaction.id;
                        revokedTransaction.timeCreated = new Date();

                        Key<Transaction> key = this.transactionService.save(revokedTransaction);
                        Transaction entity = this.transactionService.read((ObjectId)key.getId());

                        builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
                    } else {
                        builder = Response.status(Status.UNAUTHORIZED);
                    }
                } else {
                    builder = Response.status(Status.BAD_REQUEST);
                }
            }
        }

        return builder.build();
    }

    @DELETE
    @Path("/{id}")
    @Authorized
    public Response delete(@PathObject("id") Transaction transaction, @Context JsonNode claims) {
        ResponseBuilder builder;

        if (transaction == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            Item item = this.itemService.read(transaction.itemId);
            Event event = this.eventService.read(item.eventId);
            Organization organization = this.organizationService.read(event.organizationId);

            boolean hasPermission = this.permissionService.hasAnyHierarchicalPermission(
                organization.id,
                claims.get("sub").asText(),
                this.organizationService.getWritePermission()
            );

            if (hasPermission) {
                boolean isVendorTransaction = PaypalIPN.class.getSimpleName().equals(transaction.linkedObjectClass);
                boolean hasChildTransaction = this.transactionService.query()
                    .field("parentTransactionId").equal(transaction.id)
                    .countAll() > 0;

                if (isVendorTransaction || hasChildTransaction) {
                    builder = Response.status(Status.BAD_REQUEST);
                } else {
                    Transaction entity = this.transactionService.delete(transaction.id);
                    builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
                }
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }
}