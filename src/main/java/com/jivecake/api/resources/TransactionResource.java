package com.jivecake.api.resources;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
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
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.HasPermission;
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
import com.jivecake.api.service.OrganizationService;
import com.jivecake.api.service.PermissionService;
import com.jivecake.api.service.TransactionService;

@Path("/transaction")
@CORS
public class TransactionResource {
    private final TransactionService transactionService;
    private final ItemService itemService;
    private final EventService eventService;
    private final OrganizationService organizationService;
    private final PermissionService permissionService;
    private final Auth0Service auth0Service;
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    public TransactionResource(
        ItemService itemService,
        EventService eventService,
        TransactionService transactionService,
        OrganizationService organizationService,
        PermissionService permissionService,
        Auth0Service auth0Service
    ) {
        this.transactionService = transactionService;
        this.itemService = itemService;
        this.eventService = eventService;
        this.organizationService = organizationService;
        this.permissionService = permissionService;
        this.auth0Service = auth0Service;
    }

    @GET
    @Authorized
    public void search(
        @QueryParam("organizationId") List<ObjectId> organizationIds,
        @QueryParam("eventId") List<ObjectId> eventIds,
        @QueryParam("itemId") List<ObjectId> itemIds,
        @QueryParam("user_id") Set<String> userIds,
        @QueryParam("id") List<ObjectId> ids,
        @QueryParam("parentTransactionId") List<ObjectId> parentTransactionIds,
        @QueryParam("timeCreatedLessThan") Long timeCreatedLessThan,
        @QueryParam("timeCreatedGreaterThan") Long timeCreatedGreaterThan,
        @QueryParam("given_name") String given_name,
        @QueryParam("family_name") String family_name,
        @QueryParam("email") String email,
        @QueryParam("leaf") Boolean leaf,
        @QueryParam("status") List<Integer> statuses,
        @QueryParam("text") String text,
        @QueryParam("limit") Integer limit,
        @QueryParam("offset") Integer offset,
        @QueryParam("order") String order,
        @Context JsonNode claims,
        @Suspended AsyncResponse futureResponse
    ) {
        Query<Transaction> query = this.transactionService.query();

        if (!ids.isEmpty()) {
            query.field("id").in(ids);
        }

        if (!organizationIds.isEmpty()) {
            query.field("organizationId").in(organizationIds);
        }

        if (!eventIds.isEmpty()) {
            query.field("eventId").in(eventIds);
        }

        if (!itemIds.isEmpty()) {
            query.field("itemId").in(itemIds);
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
            query.field("email").startsWithIgnoreCase(email);
        }

        if (given_name != null) {
            query.field("given_name").startsWithIgnoreCase(given_name);
        }

        if (family_name != null) {
            query.field("family_name").startsWithIgnoreCase(family_name);
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

        CompletableFuture<List<Transaction>> textSearchFuture;

        if (text == null) {
            textSearchFuture = new CompletableFuture<>();
            textSearchFuture.complete(null);
        } else {
            textSearchFuture = this.transactionService.searchTransactionsFromText(text, this.auth0Service);
        }

        textSearchFuture.thenAcceptAsync(textTransactions -> {
            if (textTransactions != null) {
                List<ObjectId> textTransactionIds = textTransactions.stream()
                    .map(transaction -> transaction.id)
                    .collect(Collectors.toList());

                query.field("id").in(textTransactionIds);
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

            List<Transaction> transactions = query.asList();

            String user_id = claims.get("sub").asText();

            boolean hasUserIdPermission = userIds.size() == 1 && userIds.contains(user_id);
            boolean hasPermission = this.permissionService.has(user_id, transactions, PermissionService.READ);

            ResponseBuilder builder;

            if (hasUserIdPermission || hasPermission) {
                Paging<Transaction> entity = new Paging<>(transactions, query.count());
                builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }

            futureResponse.resume(builder.build());
        }).exceptionally(exception -> {
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
                        PermissionService.WRITE
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
                                    transfer.eventId = transaction.eventId;
                                    transfer.organizationId = transaction.organizationId;
                                    transfer.user_id = transaction.user_id;
                                    transfer.status = TransactionResource.this.transactionService.getTransferredStatus();
                                    transfer.timeCreated = new Date();

                                    Transaction completed = new Transaction();
                                    completed.itemId = transaction.itemId;
                                    completed.eventId = transaction.eventId;
                                    completed.organizationId = transaction.organizationId;
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
            .project("id", true)
            .project("itemId", true)
            .project("status", true)
            .project("parentTransactionId", true);

        if (!ids.isEmpty()) {
            query.field("id").in(ids);
        }

        if (!eventIds.isEmpty()) {
            query.field("eventId").in(eventIds);
        }

        if (!itemIds.isEmpty()) {
            query.field("itemId").in(itemIds);
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

        Paging<Transaction> entity = new Paging<>(transactions, query.count());
        return Response.ok(entity).type(MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("/user")
    @Authorized
    public void searchUsers(
        @QueryParam("id") List<ObjectId> ids,
        @Context JsonNode claims,
        @Suspended AsyncResponse promise
    ) {
        List<Transaction> transactions;

        if (ids.isEmpty()) {
            transactions = new ArrayList<>();
        } else {
            transactions = this.transactionService.query()
                .field("id").in(ids)
                .asList();
        }

        Set<String> user_ids = transactions.stream()
            .filter(transaction -> transaction.user_id != null)
            .map(transaction -> transaction.user_id)
            .collect(Collectors.toSet());

        String requesterId = claims.get("sub").asText();

        long transactionsNotBelongingToRequester = transactions.stream()
            .filter(transaction -> !requesterId.equals(transaction.user_id))
            .count();

        boolean hasPermission = this.permissionService.has(
            requesterId,
            transactions,
            PermissionService.READ
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
                    PermissionService.READ
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
            String token = authorization.substring("Bearer ".length());
            Map<String, Object> claims = this.auth0Service.getClaimsFromToken(token);

            Query<Transaction> query = this.transactionService.query();

            if (!organizationIds.isEmpty()) {
                query.field("organizationId").in(organizationIds);
            }

            if (!eventIds.isEmpty()) {
                query.field("eventId").in(eventIds);
            }

            if (!itemIds.isEmpty()) {
                query.field("itemId").in(itemIds);
            }

            if (!ids.isEmpty()) {
                query.field("id").in(ids);
            }

            List<Transaction> transactions = query.asList();

            boolean hasPermission = this.permissionService.has((String)claims.get("sub"), transactions, PermissionService.READ);

            if (hasPermission) {
                File file;

                try {
                    file = File.createTempFile("transactions", ".xlsx");
                } catch (IOException e) {
                    promise.resume(e);
                    file = null;
                }

                File writeFile = file;

                if (file != null) {
                    String userQuery = transactions.stream()
                        .filter(transaction -> transaction.user_id != null)
                        .map(transaction -> String.format("user_id: \"%s\"", transaction.user_id))
                        .collect(Collectors.joining(" OR "));

                    this.auth0Service.queryUsers(userQuery, new InvocationCallback<Response>() {
                        @Override
                        public void completed(Response response) {
                            List<JsonNode> users = null;
                            Exception exception = null;

                            try {
                                users = Arrays.asList(
                                    TransactionResource.this.mapper.readValue(response.readEntity(String.class), JsonNode[].class)
                                );
                            } catch (IOException e) {
                                exception = e;
                            }

                            if (exception == null) {
                                try {
                                    TransactionResource.this.transactionService.writeToExcel(transactions, users, writeFile);
                                    Response result = Response.ok(writeFile)
                                        .type("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                        .header("Content-Disposition", "attachment; filename=transactions")
                                        .build();

                                    promise.resume(result);
                                } catch (IOException e) {
                                    promise.resume(e);
                                }
                            } else {
                                promise.resume(Response.serverError().entity(exception).build());
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
                PermissionService.WRITE
            );

            boolean targetIsCompleted = transaction.status == this.transactionService.getPaymentCompleteStatus();
            boolean hasChildTransaction = this.transactionService.query()
                .field("parentTransactionId").equal(transaction.id)
                .count() > 0;

            if (hasChildTransaction) {
                builder = Response.status(Status.BAD_REQUEST).entity("{\"error\": \"childtransaction\"}").type(MediaType.APPLICATION_JSON);
            } else {
                if (targetIsCompleted) {
                    if (hasPermission) {
                        Transaction revokedTransaction = new Transaction();
                        revokedTransaction.given_name = transaction.given_name;
                        revokedTransaction.middleName = transaction.middleName;
                        revokedTransaction.family_name = transaction.family_name;
                        revokedTransaction.itemId = transaction.itemId;
                        revokedTransaction.eventId = transaction.eventId;
                        revokedTransaction.organizationId = transaction.organizationId;
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
    @HasPermission(clazz=Transaction.class, id="id", permission=PermissionService.WRITE)
    public Response delete(@PathObject("id") Transaction transaction, @Context JsonNode claims) {
        ResponseBuilder builder;

        boolean isVendorTransaction = PaypalIPN.class.getSimpleName().equals(transaction.linkedObjectClass);
        boolean hasChildTransaction = this.transactionService.query()
            .field("parentTransactionId").equal(transaction.id)
            .count() > 0;

        if (isVendorTransaction || hasChildTransaction) {
            builder = Response.status(Status.BAD_REQUEST);
        } else {
            Transaction entity = this.transactionService.delete(transaction.id);
            builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
        }

        return builder.build();
    }
}