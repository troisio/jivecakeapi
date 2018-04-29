package com.jivecake.api.resources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;

import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.client.mgmt.filter.UserFilter;
import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.users.User;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.HasPermission;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.filter.ValidEntity;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.FormField;
import com.jivecake.api.model.FormFieldResponse;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.request.AggregatedEvent;
import com.jivecake.api.request.EntityQuantity;
import com.jivecake.api.request.ErrorData;
import com.jivecake.api.request.TransactionResponse;
import com.jivecake.api.service.Auth0Service;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.FormService;
import com.jivecake.api.service.ItemService;
import com.jivecake.api.service.NotificationService;
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
    private final FormService formService;

    @Inject
    public ItemResource(
        Auth0Service auth0Service,
        EventService eventService,
        TransactionService transactionService,
        NotificationService notificationService,
        EntityService entityService,
        Datastore datastore,
        FormService formService
    ) {
        this.auth0Service = auth0Service;
        this.eventService = eventService;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
        this.entityService = entityService;
        this.datastore = datastore;
        this.formService = formService;
    }

    @POST
    @Authorized
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{id}/purchase")
    public Response purchase(
        @PathObject("id") Item item,
        @Context DecodedJWT jwt,
        @ValidEntity TransactionResponse entity
    ) throws Auth0Exception, InterruptedException, ExecutionException {
        if (item == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        if (item.amount != 0) {
            return Response.status(Status.BAD_REQUEST).build();
        }

        Event event = this.datastore.get(Event.class, item.eventId);

        List<Transaction> countedTransactions = this.transactionService
            .getTransactionQueryForCounting()
            .field("itemId").equal(item.id)
            .asList();

        List<Transaction> transactionsForUser = countedTransactions
            .stream()
            .filter(subject -> jwt.getSubject().equals(subject.user_id))
            .collect(Collectors.toList());

        boolean maximumPerUserViolation = item.maximumPerUser != null &&
            transactionsForUser.size() > item.maximumPerUser + entity.transaction.quantity;
        boolean maximumReached = item.totalAvailible != null &&
            countedTransactions.size() > item.totalAvailible + entity.transaction.quantity;

        boolean activeViolation = item.status != ItemService.STATUS_ACTIVE ||
            event.status != EventService.STATUS_ACTIVE;

        if (event.currency == null) {
            ErrorData errorData = new ErrorData();
            errorData.error = "currency";
            return Response.status(Status.BAD_REQUEST)
                .entity(errorData)
                .type(MediaType.APPLICATION_JSON)
                .build();
        }

        if (activeViolation) {
            ErrorData errorData = new ErrorData();
            errorData.error = "active";
            return Response.status(Status.BAD_REQUEST)
                .entity(errorData)
                .type(MediaType.APPLICATION_JSON)
                .build();
        }

        if (maximumPerUserViolation) {
            ErrorData errorData = new ErrorData();
            errorData.error = "userlimit";
            return Response.status(Status.BAD_REQUEST)
                .entity(errorData)
                .type(MediaType.APPLICATION_JSON)
                .build();
        }

        if (maximumReached) {
            ErrorData errorData = new ErrorData();
            errorData.error = "limit";
            return Response.status(Status.BAD_REQUEST)
                .entity(errorData)
                .type(MediaType.APPLICATION_JSON)
                .build();
        }

        Date date = new Date();

        AggregatedEvent aggregated = this.eventService.getAggregatedaEventData(
            event,
            this.transactionService,
            date
        );

        EntityQuantity<ObjectId> entityQuantity = new EntityQuantity<>();
        entityQuantity.entity = item.id;
        entityQuantity.quantity = entity.transaction.quantity;

        List<FormField> candidateFields = this.formService.getRequestedFormFields(
            aggregated,
            Arrays.asList(entityQuantity)
        );

        ManagementAPI api = this.auth0Service.getManagementApi();
        User user = jwt == null ? null : api
            .users()
            .get(jwt.getSubject(), new UserFilter())
            .execute();

        List<FormFieldResponse> previousUserEventResponses = user == null ?
            new ArrayList<>() :
            this.formService.getPreviousEventResponses(user, event);

        Set<ObjectId> previousUserEventResponseIds = previousUserEventResponses
            .stream()
            .map(response -> response.formFieldId)
            .collect(Collectors.toSet());

        List<FormField> fieldsToValidate = candidateFields.stream()
            .filter(field -> !previousUserEventResponseIds.contains(field.id))
            .collect(Collectors.toList());

        List<FormFieldResponse> previousResponsesWithPayloadResponses = new ArrayList<>();
        previousResponsesWithPayloadResponses.addAll(previousUserEventResponses);
        previousResponsesWithPayloadResponses.addAll(entity.responses);

        List<FormField> invalidFields = this.formService.getInvalidResponses(
            fieldsToValidate,
            previousResponsesWithPayloadResponses
        );

        if (invalidFields.size() > 0) {
            ErrorData errorData = new ErrorData();
            errorData.error = "formField";
            errorData.data = invalidFields;
            return Response.status(Status.BAD_REQUEST)
                .entity(errorData)
                .type(MediaType.APPLICATION_JSON)
                .build();
        }

        for (FormFieldResponse response: entity.responses) {
            FormService.writeDefaultFields(
                response,
                event,
                date
            );
        }

        Iterable<Key<FormFieldResponse>> keys = this.datastore.save(entity.responses);
        List<FormFieldResponse> savedResponses = this.datastore.getByKeys(FormFieldResponse.class, keys);

        Organization organization = this.datastore.get(Organization.class, event.organizationId);

        Transaction userTransaction = new Transaction();
        userTransaction.user_id = jwt.getSubject();
        userTransaction.quantity = entity.transaction.quantity;
        userTransaction.status = TransactionService.SETTLED;
        userTransaction.paymentStatus = TransactionService.PAYMENT_EQUAL;
        userTransaction.itemId = item.id;
        userTransaction.eventId = event.id;
        userTransaction.organizationId = organization.id;
        userTransaction.currency = event.currency;
        userTransaction.amount = 0;
        userTransaction.leaf = true;
        userTransaction.timeCreated = date;

        Transaction userTransactionWithResponses = this.formService.transactionsWithResponses(
            Arrays.asList(userTransaction),
            candidateFields,
            previousResponsesWithPayloadResponses
        ).get(0);

        this.datastore.save(userTransactionWithResponses);

        List<Transaction> unionedTransactions = this.formService.unionEventResponses(
            event,
            user
        );
        this.notificationService.notify(
            new ArrayList<>(unionedTransactions),
            "transaction.update"
        );

        Event updatedEvent = this.eventService.assignNumberToUserSafely(
            jwt.getSubject(),
            event
        ).get();

        this.notificationService.notify(
            Arrays.asList(updatedEvent),
            "event.update"
        );

        this.notificationService.notify(
            Arrays.asList(userTransactionWithResponses),
            "transaction.create"
        );

        this.notificationService.notify(
            new ArrayList<>(savedResponses),
            "formFieldResponse.create"
        );

        List<Object> lastActivities = new ArrayList<>(savedResponses);
        lastActivities.add(userTransactionWithResponses);

        this.entityService.cascadeLastActivity(
            lastActivities,
            date
        );

        return Response.ok(userTransactionWithResponses, MediaType.APPLICATION_JSON).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    @Path("/{id}/transaction")
    @HasPermission(id="id", clazz=Item.class, write=true)
    public Response createTransaction(
        @PathObject("id") Item item,
        @Context DecodedJWT jwt,
        @ValidEntity TransactionResponse entity
    ) {
        Transaction transaction = entity.transaction;

        if (transaction.amount < 0) {
            return Response.status(Status.BAD_REQUEST).build();
        }

        if (transaction.user_id != null) {
            return Response.status(Status.BAD_REQUEST).build();
        }

        Date currentTime = new Date();
        Event event = this.datastore.get(Event.class, item.eventId);

        AggregatedEvent aggregated = this.eventService.getAggregatedaEventData(
            event,
            this.transactionService,
            currentTime
        );

        EntityQuantity<ObjectId> entityQuantity = new EntityQuantity<>();
        entityQuantity.entity = item.id;
        entityQuantity.quantity = entity.transaction.quantity;

        List<FormField> fields = this.formService.getRequestedFormFields(
            aggregated,
            Arrays.asList(entityQuantity)
        );

        List<FormField> invalidFields = this.formService.getInvalidResponses(
            fields,
            entity.responses
        );

        if (invalidFields.size() > 0) {
            ErrorData errorData = new ErrorData();
            errorData.error = "formField";
            errorData.data = invalidFields;
            return Response.status(Status.BAD_REQUEST)
                .entity(errorData)
                .type(MediaType.APPLICATION_JSON)
                .build();
        }

        ResponseBuilder builder;

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

            builder = Response
                .status(Status.BAD_REQUEST)
                .entity(error)
                .type(MediaType.APPLICATION_JSON);
        } else {
            for (FormFieldResponse response: entity.responses) {
                FormService.writeDefaultFields(
                    response,
                    event,
                    currentTime
                );
            }

            Iterable<Key<FormFieldResponse>> formFieldKeys = this.datastore.save(entity.responses);

            transaction.leaf = true;
            transaction.linkedId = null;
            transaction.linkedObjectClass = null;
            transaction.itemId = item.id;
            transaction.eventId = item.eventId;
            transaction.organizationId = item.organizationId;
            transaction.status = TransactionService.PAYMENT_EQUAL;
            transaction.paymentStatus = TransactionService.SETTLED;
            transaction.timeCreated = currentTime;

            Transaction transactionWithResponse = this.formService.transactionsWithResponses(
                Arrays.asList(transaction),
                fields,
                entity.responses
            ).get(0);

            Key<Transaction> transactionKey = this.datastore.save(transactionWithResponse);
            Transaction newTransaction = this.datastore.get(
                Transaction.class,
                transactionKey.getId()
            );

            List<FormFieldResponse> savedFormFieldResponses = this.datastore.get(
                FormFieldResponse.class,
                formFieldKeys
            ).asList();

            List<Object> cascadedEntities = new ArrayList<>();
            cascadedEntities.add(newTransaction);
            cascadedEntities.addAll(savedFormFieldResponses);

            List<Object> results = this.entityService.cascadeLastActivity(
                cascadedEntities,
                currentTime
            );

            this.notificationService.notify(
                Arrays.asList(newTransaction),
                "transaction.create"
            );

            this.notificationService.notify(
                new ArrayList<>(savedFormFieldResponses),
                "formFieldResponse.create"
            );

            this.notificationService.notify(
                results.stream()
                    .filter(object -> object instanceof Item)
                    .collect(Collectors.toList()),
                "item.update"
            );

           this.notificationService.notify(
                results.stream()
                    .filter(object -> object instanceof Event)
                    .collect(Collectors.toList()),
                "event.update"
            );

            this.notificationService.notify(
                results.stream()
                    .filter(object -> object instanceof Organization)
                    .collect(Collectors.toList()),
                "organization.update"
            );

            builder = Response.ok(newTransaction, MediaType.APPLICATION_JSON);
        }

        return builder.build();
    }

    @GET
    @Path("/{id}")
    @Authorized
    @HasPermission(clazz=Item.class, id="id", read=true)
    public Response read(@PathObject("id") Item item) {
        return Response.ok(item).type(MediaType.APPLICATION_JSON).build();
    }

    @DELETE
    @Path("/{id}")
    @Authorized
    @HasPermission(clazz=Item.class, id="id", write=true)
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
    @HasPermission(clazz=Item.class, id="id", write=true)
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

    @POST
    @Authorized
    @Path("{id}/field")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @HasPermission(clazz=Item.class, id="id", write=true)
    public Response createFormField(
        @PathObject(value = "id") Item item,
        @ValidEntity FormField field
    ) {
        long count = this.datastore.createQuery(FormField.class)
            .field("eventId").equal(item.eventId)
            .field("item").equal(item.eventId)
            .count();

        if (count > 5) {
            return Response.status(Status.BAD_REQUEST).build();
        }

        field.id = null;
        field.eventId = item.eventId;
        field.event = null;
        field.item = item.id;
        field.timeUpdated = null;
        field.timeCreated = new Date();

        Key<FormField> key = this.datastore.save(field);
        FormField after = this.datastore.getByKey(FormField.class, key);

        this.notificationService.notify(Arrays.asList(after), "formField.created");
        this.entityService.cascadeLastActivity(Arrays.asList(after), new Date());

        return Response.ok(after, MediaType.APPLICATION_JSON).build();
    }
}