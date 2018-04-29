package com.jivecake.api.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

import com.auth0.json.mgmt.users.User;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.FormField;
import com.jivecake.api.model.FormFieldResponse;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.request.AggregatedEvent;
import com.jivecake.api.request.EntityQuantity;

public class FormService {
    private Datastore datastore;

    @Inject
    public FormService(Datastore datastore) {
        this.datastore = datastore;
    }

    public List<FormField> getInvalidResponses(List<FormField> fields, List<FormFieldResponse> responses) {
        Map<ObjectId, List<FormFieldResponse>> formFieldIdToResponse = responses
            .stream()
            .collect(Collectors.groupingBy(response -> response.formFieldId));

        List<FormField> invalidFormFields = fields
            .stream()
            .filter((field) -> {
                List<FormFieldResponse> formFieldResponses = formFieldIdToResponse.get(field.id);

                if (formFieldResponses == null || formFieldResponses.size() != 1) {
                    return true;
                }

                return !ValidationService.isValid(formFieldResponses.get(0), field);
            }
        ).collect(Collectors.toList());

        return invalidFormFields;
    }

    public List<FormField> getRequestedFormFields(
        AggregatedEvent aggregated,
        com.paypal.api.payments.Transaction transaction
    ) {
        Set<ObjectId> orderedItemIds = transaction
            .getItemList()
            .getItems()
            .stream()
            .map(item -> new ObjectId(item.getSku()))
            .collect(Collectors.toSet());

        List<FormField> candidateFields = new ArrayList<>(aggregated.fields);
        candidateFields.addAll(
            aggregated.itemData.stream()
                .map(data -> data.fields)
                .flatMap(List::stream)
                .collect(Collectors.toList())
        );

        return candidateFields.stream()
            .filter(field -> field.active && (field.event != null || orderedItemIds.contains(field.item)))
            .collect(Collectors.toList());
    }

    public List<FormField> getRequestedFormFields(
        AggregatedEvent aggregated,
        List<EntityQuantity<ObjectId>> quantities
    ) {
        Set<ObjectId> orderedItemIds = quantities
            .stream()
            .map(quantity -> quantity.entity)
            .collect(Collectors.toSet());

        List<FormField> candidateFields = new ArrayList<>(aggregated.fields);
        candidateFields.addAll(
            aggregated.itemData.stream()
                .map(data -> data.fields)
                .flatMap(List::stream)
                .collect(Collectors.toList())
        );

        return candidateFields.stream()
            .filter(field -> field.active && (field.event != null || orderedItemIds.contains(field.item)))
            .collect(Collectors.toList());
    }

    public List<Transaction> transactionsWithResponses(
        List<Transaction> transactions,
        List<FormField> fields,
        List<FormFieldResponse> responses
     ) {
        Map<FormField, List<FormFieldResponse>> fieldToResponses = fields
            .stream()
            .collect(Collectors.toMap(Function.identity(), (field) -> responses
                .stream()
                .filter(response -> response.formFieldId.equals(field.id))
                .collect(Collectors.toList())
            ));

        Map<Transaction, List<FormField>> transactionToFields = transactions
            .stream()
            .collect(Collectors.toMap(Function.identity(), (transaction) -> fields
                .stream()
                .filter(field ->
                    transaction.eventId.equals(field.event) ||
                    transaction.itemId.equals(field.item)
                )
                .collect(Collectors.toList())
            ));

        return transactions
            .stream()
            .map((original) -> {
                Transaction transaction = new Transaction(original);
                List<FormField> formFields = transactionToFields.get(original);

                if (formFields == null) {
                    transaction.formFieldResponseIds = new HashSet<>();
                } else {
                    transaction.formFieldResponseIds = formFields
                        .stream()
                        .map(field -> fieldToResponses.get(field))
                        .flatMap(List::stream)
                        .map(response -> response.id)
                        .collect(Collectors.toSet());
                }

                return transaction;
            })
            .collect(Collectors.toList());
     }

    public static void writeDefaultFields(FormFieldResponse response, Event event, Date date) {
        response.id = null;
        response.eventId = event.id;
        response.timeCreated = date;
        response.timeUpdated = null;
    }

    public List<FormFieldResponse> getPreviousEventResponses(User user, Event event) {
        Set<ObjectId> ids = this.datastore.createQuery(Transaction.class)
            .field("eventId").equal(event.id)
            .field("user_id").equal(user.getId())
            .asList()
            .stream()
            .map(transaction -> transaction.formFieldResponseIds)
            .flatMap(Set::stream)
            .collect(Collectors.toSet());

        Map<ObjectId, FormField> idToField = this.datastore.createQuery(FormField.class)
            .field("eventId").equal(event.id)
            .asList()
            .stream()
            .collect(Collectors.toMap(field -> field.id, Function.identity()));

        List<FormFieldResponse> responses =  this.datastore.get(FormFieldResponse.class, ids)
            .asList()
            .stream()
            .filter(response -> {
                if (idToField.containsKey(response.formFieldId)) {
                    FormField field = idToField.get(response.formFieldId);
                    return field.event != null;
                } else {
                    return false;
                }
            })
            .collect(Collectors.toList());

        return responses;
    }

    public List<Transaction> unionEventResponses(Event event, User user) {
        List<Transaction> transactions = this.datastore.createQuery(Transaction.class)
            .field("eventId")
            .equal(event.id)
            .field("user_id")
            .equal(user.getId())
            .asList();

        Set<ObjectId> responseIds = transactions
            .stream()
            .map(transaction -> transaction.formFieldResponseIds)
            .flatMap(Set::stream)
            .collect(Collectors.toSet());

        List<FormFieldResponse> responses = this.datastore.createQuery(FormFieldResponse.class)
            .field("id")
            .in(responseIds)
            .asList();

        List<ObjectId> fieldIds = responses
            .stream()
            .map(response -> response.formFieldId)
            .collect(Collectors.toList());

        Map<ObjectId, FormField> idToField = this.datastore.createQuery(FormField.class)
            .field("id")
            .in(fieldIds)
            .asList()
            .stream()
            .collect(Collectors.toMap(field -> field.id, Function.identity()));

        List<ObjectId> eventResponseIds = responses
            .stream()
            .filter(response -> {
                if (idToField.containsKey(response.formFieldId)) {
                    FormField field = idToField.get(response.formFieldId);
                    return field.event != null;
                }

                return false;
            })
            .map(response -> response.id)
            .collect(Collectors.toList());

        UpdateOperations<Transaction> operations = this.datastore
            .createUpdateOperations(Transaction.class)
            .addToSet("formFieldResponseIds", eventResponseIds);

        Query<Transaction> query = this.datastore.createQuery(Transaction.class)
            .field("eventId").equal(event.id)
            .field("user_id").equal(user.getId());

        this.datastore.update(query, operations);

        return transactions;
    }
}
