package com.jivecake.api.resources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
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

import com.auth0.json.mgmt.users.User;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.GZip;
import com.jivecake.api.filter.HasPermission;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.filter.QueryRestrict;
import com.jivecake.api.model.EntityAsset;
import com.jivecake.api.model.EntityType;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.request.ErrorData;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.Auth0Service;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.PermissionService;
import com.jivecake.api.service.TransactionService;

@Path("transaction")
@CORS
@Singleton
public class TransactionResource {
    private final TransactionService transactionService;
    private final NotificationService notificationService;
    private final PermissionService permissionService;
    private final EntityService entityService;
    private final Auth0Service auth0Service;
    private final Datastore datastore;

    @Inject
    public TransactionResource(
        TransactionService transactionService,
        NotificationService notificationService,
        PermissionService permissionService,
        EntityService entityService,
        Auth0Service auth0Service,
        Datastore datastore
    ) {
        this.transactionService = transactionService;
        this.notificationService = notificationService;
        this.permissionService = permissionService;
        this.entityService = entityService;
        this.auth0Service = auth0Service;
        this.datastore = datastore;
    }

    @GZip
    @GET
    @Authorized
    public Response search(
        @QueryParam("organizationId") List<ObjectId> organizationIds,
        @QueryParam("eventId") List<ObjectId> eventIds,
        @QueryParam("itemId") List<ObjectId> itemIds,
        @QueryParam("user_id") List<String> userIds,
        @QueryParam("id") List<ObjectId> ids,
        @QueryParam("email") String email,
        @QueryParam("leaf") Boolean leaf,
        @QueryParam("status") List<Integer> statuses,
        @QueryParam("limit") Integer limit,
        @QueryParam("offset") Integer offset,
        @QueryParam("order") String order,
        @Context DecodedJWT jwt
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

        if (!userIds.isEmpty()) {
            query.field("user_id").in(userIds);
        }

        if (email != null) {
            query.field("email").startsWithIgnoreCase(email);
        }

        if (leaf != null) {
            query.field("leaf").equal(leaf);
        }

        if (order != null) {
            query.order(order);
        }

        int defaultLimit = 5000;

        FindOptions options = new FindOptions();

        if (limit != null && limit > -1 && limit <= defaultLimit) {
            options.limit(limit);
        } else {
            options.limit(defaultLimit);
        }

        if (offset != null && offset > -1) {
            options.skip(offset);
        }

        List<Transaction> transactions = query.asList(options);

        String userId = jwt.getSubject();

        boolean hasUserPermission = userIds.size() == 1 && userIds.contains(userId);
        boolean hasOrganizationPermission = this.permissionService.has(
            userId,
            transactions,
            PermissionService.READ
        );

        boolean hasPermission = hasUserPermission || hasOrganizationPermission;

        ResponseBuilder builder;

        if (hasPermission) {
            Paging<Transaction> entity = new Paging<>(transactions, query.count());
            builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
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

        if (limit != null && limit > -1) {
            options.limit(limit);
        }

        if (skip != null && skip > -1) {
            options.skip(skip);
        }

        Paging<Transaction> entity = new Paging<>(query.asList(options), query.count());
        return Response.ok(entity).type(MediaType.APPLICATION_JSON).build();
    }

    @GZip
    @GET
    @Path("asset/user")
    @Authorized
    public Response searchAssets(
        @QueryParam("id") List<ObjectId> transactionIds,
        @QueryParam("assetType") int assetType,
        @Context DecodedJWT jwt
    ) {
        List<Transaction> transactions = this.datastore.createQuery(Transaction.class)
            .field("id").in(transactionIds)
            .field("user_id").exists()
            .asList();

        boolean hasPermission = this.permissionService.has(
            jwt.getSubject(),
            transactions,
            PermissionService.READ
        );

        ResponseBuilder builder;

        if (hasPermission) {
            Set<String> userIds = transactions
                .stream()
                .map(transaction -> transaction.user_id)
                .collect(Collectors.toSet());

            List<EntityAsset> assets = this.datastore.createQuery(EntityAsset.class)
                .field("entityId").in(userIds)
                .field("entityType").equal(EntityType.USER)
                .field("assetType").equal(assetType)
                .asList();

            builder = Response.ok(assets).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @GET
    @Path("user")
    @Authorized
    public Response searchUsers(
        @QueryParam("id") List<ObjectId> ids,
        @Context DecodedJWT jwt
    ) throws IOException {
        List<Transaction> transactions = this.datastore.createQuery(Transaction.class)
            .field("id").in(ids)
            .asList();
        Set<String> user_ids = transactions.stream()
            .filter(transaction -> transaction.user_id != null)
            .map(transaction -> transaction.user_id)
            .collect(Collectors.toSet());

        long transactionsNotBelongingToRequester = transactions.stream()
            .filter(transaction -> !jwt.getSubject().equals(transaction.user_id))
            .count();

        boolean hasTransactionPermission = this.permissionService.has(
            jwt.getSubject(),
            transactions,
            PermissionService.READ
        );

        boolean hasPermission = !transactions.isEmpty() &&
            (hasTransactionPermission || transactionsNotBelongingToRequester == 0);

        ResponseBuilder builder;

        if (hasPermission) {
            if (user_ids.isEmpty()) {
                builder = Response.ok(new ArrayList<>(), MediaType.APPLICATION_JSON);
            } else {
                String query = user_ids.stream()
                    .map(id -> String.format("user_id: \"%s\"", id))
                    .collect(Collectors.joining(" OR "));

                User[] entity = this.auth0Service.queryUsers(query);

                builder = Response.ok(entity, MediaType.APPLICATION_JSON);
            }
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @GET
    @Path("{id}")
    @Authorized
    public Response read(
        @PathObject("id") Transaction transaction,
        @Context DecodedJWT jwt
    ) {
        ResponseBuilder builder;

        if (transaction == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            boolean hasPermission = jwt.getSubject().equals(transaction.user_id);
            boolean hasOrganizationPermission = this.permissionService.has(
                jwt.getSubject(),
                Arrays.asList(transaction),
                PermissionService.READ
            );

            if (hasPermission || hasOrganizationPermission) {
                builder = Response.ok(transaction).type(MediaType.APPLICATION_JSON);
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }

    @POST
    @Path("{id}/revoke")
    @Authorized
    @HasPermission(clazz=Transaction.class, permission=PermissionService.WRITE, id="id")
    public Response revoke(@PathObject("id") Transaction transaction) {
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

                Iterable<Key<Transaction>> keys = this.datastore.save(
                    Arrays.asList(revokedTransaction, transaction)
                );
                List<Transaction> transactions = this.datastore.getByKeys(keys);

                this.entityService.cascadeLastActivity(transactions, currentTime);
                this.notificationService.notify(
                    Arrays.asList(revokedTransaction, transaction),
                    "transaction.update"
                );

                builder = Response.ok(transactions.get(0)).type(MediaType.APPLICATION_JSON);
            } else {
                ErrorData entity = new ErrorData();
                entity.error = "complete";
                builder = Response.status(Status.BAD_REQUEST)
                    .entity(entity)
                    .type(MediaType.APPLICATION_JSON);
            }
        }

        return builder.build();
    }

    @DELETE
    @Path("{id}")
    @Authorized
    @HasPermission(clazz=Transaction.class, id="id", permission=PermissionService.WRITE)
    public Response delete(@PathObject("id") Transaction transaction) {
        ResponseBuilder builder;

        boolean isSettled = transaction.status == TransactionService.SETTLED;
        boolean isUserRevoked = transaction.status == TransactionService.USER_REVOKED;
        boolean isVendorTransaction = this.transactionService.isVendorTransaction(transaction);
        boolean hasChildTransaction = this.datastore.createQuery(Transaction.class)
            .field("parentTransactionId").equal(transaction.id)
            .count() > 0;

        boolean canDelete = !hasChildTransaction && (
            isUserRevoked || (isSettled && !isVendorTransaction)
        );

        if (canDelete) {
            this.datastore.delete(Transaction.class, transaction.id);
            this.notificationService.notify(Arrays.asList(transaction), "transaction.delete");

            Transaction parentTransaction = this.datastore.get(Transaction.class, transaction.parentTransactionId);

            List<Object> transactions = new ArrayList<>();
            transactions.add(transaction);

            if (parentTransaction != null) {
                parentTransaction.leaf = true;
                this.datastore.save(parentTransaction);
                this.notificationService.notify(Arrays.asList(parentTransaction), "transaction.update");

                transactions.add(parentTransaction);
            }

            this.entityService.cascadeLastActivity(transactions, new Date());
            builder = Response.ok(transaction).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.BAD_REQUEST);
        }

        return builder.build();
    }
}