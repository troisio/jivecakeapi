package com.jivecake.api.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;

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

    public Key<Item> save(Item item) {
        Key<Item> result = this.datastore.save(item);
        return result;
    }

    public Item read(ObjectId id) {
        Item result = this.datastore.find(Item.class)
        .field("id").equal(id)
        .get();
        return result;
    }

    public List<Item> read() {
        List<Item> result = this.datastore.find(Item.class).asList();
        return result;
    }

    public Item delete(ObjectId id) {
        Query<Item> deleteQuery = this.datastore.createQuery(Item.class).filter("id", id);
        Item result = this.datastore.findAndDelete(deleteQuery);
        return result;
    }

    public Transaction readTransaction(ObjectId id) {
        Transaction result = this.datastore.find(Transaction.class)
            .field("id")
            .equal(id)
            .get();
        return result;
    }

    public List<Transaction> readTransactions(ObjectId itemId) {
        List<Transaction> result = this.datastore.find(Transaction.class)
            .field("itemId").equal(itemId)
            .asList();
        return result;
    }

    public Query<Item> query() {
        return this.datastore.createQuery(Item.class);
    }

    public List<AggregatedItemGroup> getAggregatedaGroupData(List<Item> items, TransactionService transactionService, Date currentTime) {
        List<ObjectId> itemIds = items.stream().map(item -> item.id).collect(Collectors.toList());
        List<ObjectId> eventIds = items.stream().map(item -> item.eventId).collect(Collectors.toList());

        List<Event> events = this.datastore.createQuery(Event.class)
            .field("id").in(eventIds)
            .asList();

        List<Event> parentEntities = new ArrayList<>();
        parentEntities.addAll(events);

        Map<ObjectId, List<Item>> eventIdToItems = items.stream().collect(Collectors.groupingBy(item -> item.eventId));

        List<Transaction> transactions = transactionService.query()
            .field("itemId").in(itemIds)
            .asList();

        List<List<Transaction>> forest = transactionService.getTransactionForest(transactions);

        List<Transaction> leafTransactions = forest.stream()
            .filter(lineage -> lineage.size() == 1)
            .map(lineage -> lineage.get(0))
            .collect(Collectors.toList());

        Map<ObjectId, List<Transaction>> itemToTransactions = leafTransactions.stream()
            .collect(Collectors.groupingBy(transaction -> transaction.itemId));

        List<AggregatedItemGroup> groups = parentEntities.stream().map(event -> {
            AggregatedItemGroup itemGroup = new AggregatedItemGroup();

            List<Item> parentItems = eventIdToItems.get(event.id);

            List<ItemData> itemData = parentItems.stream().map(item -> {
                ItemData result = new ItemData();
                result.item = item;
                result.transactions = itemToTransactions.containsKey(item.id) ? itemToTransactions.get(item.id) : new ArrayList<>();

                Double amount;

                if (item.countAmounts != null) {
                    long count = result.transactions.stream().filter(
                        transaction -> transaction.status == transactionService.getPaymentCompleteStatus() ||
                        transaction.status == transactionService.getPaymentPendingStatus()
                    ).map(transaction -> transaction.quantity)
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

            itemGroup.parent = event;
            itemGroup.itemData = itemData;

            return itemGroup;
        }).collect(Collectors.toList());

        return groups;
    }

    public int getActiveItemStatus() {
        return  0;
    }
}