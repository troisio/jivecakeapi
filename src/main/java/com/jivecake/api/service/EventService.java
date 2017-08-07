package com.jivecake.api.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;

import com.jivecake.api.model.Event;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.request.AggregatedEvent;
import com.jivecake.api.request.EntityQuantity;
import com.jivecake.api.request.ErrorData;
import com.jivecake.api.request.ItemData;

public class EventService {
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_INACTIVE = 0;
    private final Random random = new SecureRandom();
    private final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890_-";
    private final int maximumHashCharacters = 8;

    public String getHash() {
        String result = "";

        for (int index = 0; index < this.maximumHashCharacters; index++) {
            int i = this.random.nextInt(this.alphabet.length());
            result += this.alphabet.charAt(i);
        }

        return result;
    }

    public List<ErrorData> getErrorsFromOrderRequest(String userId, List<EntityQuantity<ObjectId>> entityQuantities, AggregatedEvent aggregated) {
        List<ErrorData> errors = new ArrayList<>();

        Map<ObjectId, ItemData> itemToItemData = aggregated.itemData.stream()
            .collect(
                Collectors.toMap(data -> data.item.id, Function.identity())
            );

        boolean userIdViolation = false;
        boolean itemsAreActive = true;
        boolean allItemExistsInAggregatedEvent = true;
        boolean orderWouldExceedTotalAvailible = false;
        boolean orderWouldViolateUserMaximum = false;
        boolean hasDuplicateItems =  entityQuantities.stream()
            .map(entityQuantity -> entityQuantity.entity)
            .collect(Collectors.toSet())
            .size() != entityQuantities.size();

        for (EntityQuantity<ObjectId> entity: entityQuantities) {
            ItemData itemData = itemToItemData.get(entity.entity);

            if (itemData == null) {
                allItemExistsInAggregatedEvent = false;
            } else {
                itemsAreActive = itemData.item.status == ItemService.STATUS_ACTIVE;

                List<Transaction> countedTransactions = itemData.transactions.stream()
                    .filter(TransactionService.usedForCountFilter)
                    .collect(Collectors.toList());

                if (itemData.item.totalAvailible != null) {
                    long count = countedTransactions.stream()
                        .map(transaction -> transaction.quantity)
                        .reduce(0L, Long::sum);

                    orderWouldExceedTotalAvailible = entity.quantity + count > itemData.item.totalAvailible;
                }

                if (itemData.item.maximumPerUser != null) {
                    if (userId == null) {
                        userIdViolation = true;
                    } else {
                        long count = countedTransactions.stream()
                            .filter(transaction -> userId.equals(transaction.user_id))
                            .map(transaction -> transaction.quantity)
                            .reduce(0L, Long::sum);

                        orderWouldExceedTotalAvailible = entity.quantity + count > itemData.item.maximumPerUser;
                    }
                }
            }
        }

        if (userIdViolation) {
            ErrorData error = new ErrorData();
            error.error = "userId";
            errors.add(error);
        }

        if (!itemsAreActive) {
            ErrorData error = new ErrorData();
            error.error = "itemNotActive";
            errors.add(error);
        }

        if (!allItemExistsInAggregatedEvent) {
            ErrorData error = new ErrorData();
            error.error = "itemNotFound";
            errors.add(error);
        }

        if (orderWouldExceedTotalAvailible) {
            ErrorData error = new ErrorData();
            error.error = "totalAvailible";
            errors.add(error);
        }

        if (orderWouldViolateUserMaximum) {
            ErrorData error = new ErrorData();
            error.error = "maximumPerUser";
            errors.add(error);
        }

        if (hasDuplicateItems) {
            ErrorData error = new ErrorData();
            error.error = "duplicateItems";
            errors.add(error);
        }

        if (entityQuantities.isEmpty()) {
            ErrorData error = new ErrorData();
            error.error = "empty";
            errors.add(error);
        }

        if (aggregated.event.status != EventService.STATUS_ACTIVE) {
            ErrorData error = new ErrorData();
            error.error = "eventNotActive";
            errors.add(error);
        }

        return errors;
    }

    public boolean isValidEvent(Event event) {
        return event.name != null &&
               event.name.length() > 0 &&
               event.name.length() < 500 &&
               (
                   event.status == EventService.STATUS_INACTIVE ||
                   event.status == EventService.STATUS_ACTIVE
               ) &&
               (
                   event.paymentProfileId == null ||
                   TransactionService.CURRENCIES.contains(event.currency)
               );
    }
}