package com.jivecake.api.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;

import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.request.AggregatedItemGroup;
import com.jivecake.api.request.ItemData;

public class ItemService {
    private final Datastore datastore;

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
                item.status == this.getActiveItemStatus() ||
                item.status == this.getInactiveItemStatus()
            ) &&
            !hasTimeAndCountViolation &&
            !hasNegativeAmountViolation;
    }

    public AggregatedItemGroup getAggregatedaGroupData(
        Event event,
        TransactionService transactionService,
        Date currentTime
    ) {
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
                    .filter(transactionService.usedForCountFilter)
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

        AggregatedItemGroup group = new AggregatedItemGroup();
        group.event = event;
        group.itemData = itemData;
        return group;
    }

    public int getActiveItemStatus() {
        return  0;
    }

    public int getInactiveItemStatus() {
        return  1;
    }
}