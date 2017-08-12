package com.jivecake.api.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;

import com.jivecake.api.model.EntityAsset;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.PaymentProfile;
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
    private final Datastore datastore;

    @Inject
    public EventService(Datastore datastore) {
        this.datastore = datastore;
    }

    public String getHash() {
        String result = "";

        for (int index = 0; index < this.maximumHashCharacters; index++) {
            int i = this.random.nextInt(this.alphabet.length());
            result += this.alphabet.charAt(i);
        }

        return result;
    }

    public AggregatedEvent getAggregatedaEventData(
            Event event,
            TransactionService transactionService,
            Date currentTime
        ) {
            List<PaymentProfile> profiles = this.datastore.createQuery(PaymentProfile.class)
                .field("id").equal(event.paymentProfileId)
                .asList();

            List<Transaction> leafTransactions = this.datastore.createQuery(Transaction.class)
                .field("eventId").equal(event.id)
                .field("leaf").equal(true)
                .asList();
            List<Item> items = this.datastore.createQuery(Item.class)
                .field("eventId").equal(event.id)
                .asList();

            Map<ObjectId, List<Transaction>> itemToTransactions = items.stream()
                .collect(Collectors.toMap(item -> item.id, item -> new ArrayList<>()));

            for (Transaction transaction: leafTransactions) {
                itemToTransactions.get(transaction.itemId).add(transaction);
            }

            List<ItemData> itemData = items.stream().map(item -> {
                ItemData result = new ItemData();
                result.item = item;
                result.transactions = itemToTransactions.get(item.id);

                Double amount;

                if (item.countAmounts != null) {
                    long count = result.transactions.stream()
                        .filter(TransactionService.usedForCountFilter)
                        .map(transaction -> transaction.quantity)
                         .reduce(0L, Long::sum);

                    amount = item.getDerivedAmountFromCounts(count);
                } else if (item.timeAmounts != null) {
                    amount = item.getDerivedAmountFromTime(currentTime);
                } else {
                    amount = item.amount;
                }

                result.amount = amount;

                return result;
            }).collect(Collectors.toList());

            List<EntityAsset> assets = this.datastore.createQuery(EntityAsset.class)
                .field("id").equal(event.entityAssetConsentId)
                .asList();

            AggregatedEvent group = new AggregatedEvent();
            group.organization = this.datastore.get(Organization.class, event.organizationId);
            group.event = event;
            group.itemData = itemData;
            group.assets = assets;

            if (profiles.size() == 1) {
                group.profile = profiles.get(0);
            }

            return group;
        }

    public List<ErrorData> getErrorsFromOrderRequest(
        String userId,
        List<EntityQuantity<ObjectId>> entityQuantities,
        AggregatedEvent aggregated
    ) {
        List<ErrorData> errors = new ArrayList<>();

        Map<ObjectId, ItemData> itemToItemData = aggregated.itemData.stream()
            .collect(
                Collectors.toMap(data -> data.item.id, Function.identity())
            );

        boolean eventIsActive = aggregated.event.status == EventService.STATUS_ACTIVE;
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

                if (itemData.item.amount == 0 && userId == null) {
                    userIdViolation = true;
                }

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

        if (!eventIsActive) {
            ErrorData error = new ErrorData();
            error.error = "eventNotActive";
            errors.add(error);
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