package com.jivecake.api.resources;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;
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
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.HasPermission;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.filter.QueryRestrict;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.Auth0Service;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.PermissionService;
import com.jivecake.api.service.TransactionService;

@Path("transaction")
@CORS
@Singleton
public class TransactionResource {
    private final TransactionService transactionService;
    private final NotificationService notificationService;
    private final EventService eventService;
    private final PermissionService permissionService;
    private final EntityService entityService;
    private final Auth0Service auth0Service;
    private final Datastore datastore;
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    public TransactionResource(
        EventService eventService,
        TransactionService transactionService,
        NotificationService notificationService,
        PermissionService permissionService,
        EntityService entityService,
        Auth0Service auth0Service,
        Datastore datastore
    ) {
        this.transactionService = transactionService;
        this.notificationService = notificationService;
        this.eventService = eventService;
        this.permissionService = permissionService;
        this.auth0Service = auth0Service;
        this.entityService = entityService;
        this.datastore = datastore;
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
        @QueryParam("lastTransferTimeGreaterThan") Long lastTransferTimeGreaterThan,
        @QueryParam("lastTransferTimeLessThan") Long lastTransferTimeLessThan,
        @QueryParam("timeCreatedLessThan") Long timeCreatedLessThan,
        @QueryParam("timeCreatedGreaterThan") Long timeCreatedGreaterThan,
        @QueryParam("given_name") String given_name,
        @QueryParam("family_name") String family_name,
        @QueryParam("email") String email,
        @QueryParam("leaf") Boolean leaf,
        @QueryParam("status") Set<Integer> statuses,
        @QueryParam("paymentStatus") Set<Integer> paymentStatuses,
        @QueryParam("text") String text,
        @QueryParam("limit") Integer limit,
        @QueryParam("offset") Integer offset,
        @QueryParam("order") String order,
        @Context JsonNode claims,
        @Suspended AsyncResponse futureResponse
    ) {
        Query<Transaction> query = this.datastore.createQuery(Transaction.class);

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

        if (!paymentStatuses.isEmpty()) {
            query.field("paymentStatus").in(paymentStatuses);
        }

        if (!parentTransactionIds.isEmpty()) {
            query.field("parentTransactionId").in(parentTransactionIds);
        }

        if (!userIds.isEmpty()) {
            query.field("user_id").in(userIds);
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

        if (leaf != null) {
            query.field("leaf").equal(leaf);
        }

        if (lastTransferTimeGreaterThan != null) {
            query.field("lastTransferTime").greaterThan(new Date(lastTransferTimeGreaterThan));
        }

        if (lastTransferTimeLessThan != null) {
            query.field("lastTransferTime").lessThan(new Date(lastTransferTimeLessThan));
        }

        if (timeCreatedGreaterThan != null) {
            query.field("timeCreated").greaterThan(new Date(timeCreatedGreaterThan));
        }

        if (timeCreatedLessThan != null) {
            query.field("timeCreated").lessThan(new Date(timeCreatedLessThan));
        }

        if (order != null) {
            query.order(order);
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

            List<Transaction> transactions = query.asList(options);

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
    @Path("{id}/transfer/{user_id}")
    @HasPermission(id="id", clazz=Transaction.class, permission=PermissionService.WRITE)
    public void transfer(
        @PathObject("id") Transaction transaction,
        @PathParam("user_id") String user_id,
        @Context JsonNode claims,
        @Suspended AsyncResponse promise
    ) {
        boolean statusIsValid = transaction.paymentStatus == TransactionService.PAYMENT_EQUAL &&
            transaction.status == TransactionService.SETTLED;

        if (statusIsValid) {
            Date currentTime = new Date();
            Event event = this.datastore.get(Event.class, transaction.eventId);

            String requester = claims.get("sub").asText();

            boolean hasTransferTimeViolation = false;

            if (transaction.lastTransferTime != null) {
                long timeBetweenTransfer = transaction.lastTransferTime.getTime() - currentTime.getTime();
                hasTransferTimeViolation = event.minimumTimeBetweenTransactionTransfer < timeBetweenTransfer;
            }

            if (!transaction.leaf) {
                promise.resume(
                    Response.status(Status.BAD_REQUEST)
                        .entity("{\"error\": \"leaf\"}")
                        .type(MediaType.APPLICATION_JSON)
                        .build()
                );
            } else if (transaction.user_id == null) {
                promise.resume(
                    Response.status(Status.BAD_REQUEST)
                        .entity("{\"error\": \"transactionuserid\"}")
                        .type(MediaType.APPLICATION_JSON)
                        .build()
                );
            } else if (requester.equals(user_id)) {
                promise.resume(
                    Response.status(Status.BAD_REQUEST)
                        .entity("{\"error\": \"sameuser\"}")
                        .type(MediaType.APPLICATION_JSON)
                        .build()
                );
            } else if (event.status == this.eventService.getInactiveEventStatus()) {
                promise.resume(
                    Response.status(Status.BAD_REQUEST)
                        .entity("{\"error\": \"eventinactive\"}")
                        .type(MediaType.APPLICATION_JSON)
                        .build()
                );
            } else if (hasTransferTimeViolation) {
                String body = String.format("{\"error\": \"minimumTimeBetweenPassTransfer\", data: %d}", event.minimumTimeBetweenTransactionTransfer);
                promise.resume(
                    Response.status(Status.BAD_REQUEST)
                        .entity(body)
                        .type(MediaType.APPLICATION_JSON)
                        .build()
                );
            } else {
                this.auth0Service.queryUsers(String.format("user_id: \"%s\"", user_id), new InvocationCallback<Response>() {
                    @Override
                    public void completed(Response response) {
                        JsonNode[] users;
                        Object entity = response.getEntity();
                        String body = entity instanceof String ? (String)entity : response.readEntity(String.class);

                        IOException exception = null;

                        try {
                            users = TransactionResource.this.mapper.readValue(body, JsonNode[].class);
                        } catch (IOException e) {
                            exception = e;
                            users = null;
                        }

                        if (exception == null) {
                            if (users.length == 0) {
                                promise.resume(Response.status(Status.NOT_FOUND)
                                     .entity("{\"error\": \"user\"}")
                                     .type(MediaType.APPLICATION_JSON)
                                    .build());
                            } else {
                                transaction.email = null;
                                transaction.given_name = null;
                                transaction.family_name = null;
                                transaction.middleName = null;

                                transaction.user_id = user_id;
                                transaction.leaf = true;
                                transaction.lastTransferTime = currentTime;

                                Key<Transaction> key = TransactionResource.this.datastore.save(transaction);
                                TransactionResource.this.datastore.get(Transaction.class, key.getId());

                                TransactionResource.this.entityService.cascadeLastActivity(Arrays.asList(transaction), currentTime);

                                promise.resume(
                                    Response.ok(transaction)
                                        .type(MediaType.APPLICATION_JSON)
                                        .build()
                                );
                            }
                        } else {
                            promise.resume(
                                Response.status(Status.SERVICE_UNAVAILABLE)
                                    .entity(exception)
                                    .build()
                            );
                        }
                    }

                    @Override
                    public void failed(Throwable throwable) {
                        promise.resume(throwable);
                    }
                });
            }
        } else {
            promise.resume(Response.status(Status.BAD_REQUEST).build());
        }
    }

    @GET
    @Path("search")
    @QueryRestrict(hasAny=true, target={"eventId", "itemId"})
    public Response publicSearch(
        @QueryParam("id") List<ObjectId> ids,
        @QueryParam("eventId") List<ObjectId> eventIds,
        @QueryParam("itemId") List<ObjectId> itemIds,
        @QueryParam("status") List<Integer> statuses,
        @QueryParam("paymentStatus") Set<Integer> paymentStatuses,
        @QueryParam("leaf") Boolean leaf,
        @QueryParam("limit") Integer limit,
        @QueryParam("skip") Integer skip
    ) {
        Query<Transaction> query = this.datastore.createQuery(Transaction.class)
            .project("id", true)
            .project("itemId", true)
            .project("eventId", true)
            .project("organizationId", true)
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

        if (!paymentStatuses.isEmpty()) {
            query.field("paymentStatus").in(paymentStatuses);
        }

        if (leaf != null) {
            query.field("leaf").equal(leaf);
        }

        FindOptions options = new FindOptions();

        if (limit != null) {
            options.limit(limit);
        }

        if (skip != null) {
            options.skip(skip);
        }

        Paging<Transaction> entity = new Paging<>(query.asList(options), query.count());
        return Response.ok(entity).type(MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("user")
    @Authorized
    public void searchUsers(
        @QueryParam("id") List<ObjectId> ids,
        @Context JsonNode claims,
        @Suspended AsyncResponse promise
    ) {
        List<Transaction> transactions = this.datastore.createQuery(Transaction.class)
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
    @Path("{id}")
    @Authorized
    @HasPermission(id="id", clazz=Transaction.class, permission=PermissionService.READ)
    public Response read(@PathObject("id") Transaction transaction, @Context JsonNode claims) {
        return Response.ok(transaction).type(MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("excel")
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

            Query<Transaction> query = this.datastore.createQuery(Transaction.class);

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
                                    TransactionResource.this.mapper.readValue(
                                        response.readEntity(String.class),
                                        JsonNode[].class
                                     )
                                );
                            } catch (IOException e) {
                                exception = e;
                            }

                            if (exception == null) {
                                try {
                                    TransactionResource.this.transactionService.writeToExcel(transactions, users, writeFile);
                                    Response result = Response.ok(writeFile)
                                        .type("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                        .header("Content-Disposition", "attachment; filename=transactions.xlsx")
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
    @Path("{id}/revoke")
    @Authorized
    @HasPermission(clazz=Transaction.class, permission=PermissionService.WRITE, id="id")
    public Response revoke(@PathObject("id") Transaction transaction, @Context JsonNode claims) {
        ResponseBuilder builder;

        boolean targetIsCompleted = transaction.status == TransactionService.SETTLED;
        boolean hasChildTransaction = this.datastore.createQuery(Transaction.class)
            .field("parentTransactionId").equal(transaction.id)
            .count() > 0;

        if (hasChildTransaction) {
            builder = Response.status(Status.BAD_REQUEST)
                .entity("{\"error\": \"childtransaction\"}")
                .type(MediaType.APPLICATION_JSON);
        } else {
            if (targetIsCompleted) {
                Date currentTime = new Date();

                Transaction revokedTransaction = new Transaction(transaction);
                revokedTransaction.id = null;
                revokedTransaction.parentTransactionId = transaction.id;
                revokedTransaction.status = TransactionService.USER_REVOKED;
                revokedTransaction.leaf = true;
                revokedTransaction.timeCreated = currentTime;

                transaction.leaf = false;

                Iterable<Key<Transaction>> keys = this.datastore.save(Arrays.asList(revokedTransaction, transaction));
                List<Transaction> transactions = this.datastore.getByKeys(keys);

                this.entityService.cascadeLastActivity(transactions, currentTime);
                this.notificationService.notify(new ArrayList<Object>(transactions), "transaction.revoke");

                builder = Response.ok(transactions.get(0)).type(MediaType.APPLICATION_JSON);
            } else {
                builder = Response.status(Status.BAD_REQUEST)
                    .entity("{\"error\": \"complete\"}")
                    .type(MediaType.APPLICATION_JSON);
            }
        }

        return builder.build();
    }

    @DELETE
    @Path("{id}")
    @Authorized
    @HasPermission(clazz=Transaction.class, id="id", permission=PermissionService.WRITE)
    public Response delete(@PathObject("id") Transaction transaction, @Context JsonNode claims) {
        ResponseBuilder builder;

        boolean isVendorTransaction = this.transactionService.isVendorTransaction(transaction);
        boolean hasChildTransaction = this.datastore.createQuery(Transaction.class)
            .field("parentTransactionId").equal(transaction.id)
            .count() > 0;

        if (isVendorTransaction || hasChildTransaction) {
            builder = Response.status(Status.BAD_REQUEST);
        } else {
            this.datastore.delete(Transaction.class, transaction.id);

            Transaction parentTransaction = this.datastore.get(Transaction.class, transaction.parentTransactionId);

            List<Object> transactions = new ArrayList<>();
            transactions.add(transaction);

            if (parentTransaction != null) {
                parentTransaction.leaf = true;
                this.datastore.save(parentTransaction);

                transactions.add(parentTransaction);
            }

            this.entityService.cascadeLastActivity(transactions, new Date());
            this.notificationService.notify(transactions, "transaction.delete");
            builder = Response.ok(transaction).type(MediaType.APPLICATION_JSON);
        }

        return builder.build();
    }
}