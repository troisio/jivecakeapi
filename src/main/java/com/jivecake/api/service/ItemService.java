package com.jivecake.api.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
import com.jivecake.api.request.ItemData;

public class ItemService {
    private final Datastore datastore;
    public static final int STATUS_ACTIVE = 0;
    public static final int STATUS_INACTIVE = 1;

    @Inject
    public ItemService(Datastore datastore) {
        this.datastore = datastore;
    }

    public boolean isValid(Item item) {
        boolean hasTimeAndCountViolation = item.timeAmounts != null && !item.timeAmounts.isEmpty() &&
                item.countAmounts != null && !item.countAmounts.isEmpty();

        boolean hasNegativeAmountViolation = item.timeAmounts != null && item.timeAmounts.stream().filter(t -> t.amount < 0).count() > 0 ||
                  item.countAmounts != null && item.countAmounts.stream().filter(t -> t.amount < 0).count() > 0;

        return (item.amount >= 0) &&
            (item.maximumPerUser == null || item.maximumPerUser >= 0) &&
            (item.totalAvailible == null || item.totalAvailible >= 0) &&
            (
                item.status == ItemService.STATUS_ACTIVE ||
                item.status == ItemService.STATUS_INACTIVE
            ) &&
            !hasTimeAndCountViolation &&
            !hasNegativeAmountViolation;
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

    public double[] getAmounts(List<Item> items, Date date, Collection<Transaction> transactions) {
        double[] result = new double[items.size()];

        Map<ObjectId, List<Transaction>> itemToTransactions = items.stream()
            .collect(
                Collectors.toMap(item -> item.id, item -> new ArrayList<>())
            );

        for (Transaction transaction: transactions) {
            if (itemToTransactions.containsKey(transaction.itemId)) {
                itemToTransactions.get(transaction.itemId).add(transaction);
            }
        }

        for (int index = 0; index < items.size(); index++) {
            Item item = items.get(index);

            double amount = item.getDerivedAmount(
                itemToTransactions.get(item.id).size(),
                date
            );

            result[index] = amount;
        }

        return result;
    }
}