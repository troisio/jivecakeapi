package com.jivecake.api.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.Query;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Transaction;

@Singleton
public class MappingService {
    private final Datastore datastore;

    @Inject
    public MappingService(Datastore datastore) {
        this.datastore = datastore;
    }

    public Set<ObjectId> getItemTransactionIds(
        Collection<ObjectId> organizationIds,
        Collection<ObjectId> eventIds,
        Collection<ObjectId> itemIds
    ) {
        List<ObjectId> aggregatedEventIds = this.datastore.createQuery(Event.class)
            .field("organizationId").in(organizationIds)
            .asList()
            .stream()
            .map(event -> event.id)
            .collect(Collectors.toList());

        aggregatedEventIds.addAll(eventIds);

        Query<Item> itemQuery = this.datastore.createQuery(Item.class).disableValidation();

        itemQuery.or(
             itemQuery.criteria("eventId").in(aggregatedEventIds),
             itemQuery.criteria("organizationId").in(organizationIds)
        );

        List<ObjectId> aggregatedItemIds = itemQuery.asList()
            .stream()
            .map(item -> item.id)
            .collect(Collectors.toList());

        aggregatedItemIds.addAll(itemIds);

        Set<ObjectId> result = this.datastore.createQuery(Transaction.class)
            .field("itemId").in(aggregatedItemIds)
            .asList()
            .stream()
            .map(transaction -> transaction.id)
            .collect(Collectors.toSet());

        return result;
    }

    public Set<ObjectId> getOrganizationIds(
        Collection<ObjectId> itemTransactionIds,
        Collection<ObjectId> itemIds,
        Collection<ObjectId> eventIds
    ) {
        Set<ObjectId> aggregatedItemIds;

        if (itemTransactionIds.isEmpty()) {
            aggregatedItemIds = new HashSet<>();
        } else {
            aggregatedItemIds = this.datastore.createQuery(Transaction.class)
                .retrievedFields(true, "itemId")
                .field("id").in(itemTransactionIds)
                .asList()
                .stream()
                .map(transaction -> transaction.itemId)
                .collect(Collectors.toSet());
        }

        aggregatedItemIds.addAll(itemIds);

        Set<ObjectId> aggregatedEventIds = new HashSet<>();
        Set<ObjectId> aggregatedOrganizationIds = new HashSet<>();

        if (!aggregatedItemIds.isEmpty()) {
            this.datastore.createQuery(Item.class)
                .field("id").in(aggregatedItemIds)
                .asList()
                .stream()
                .forEach(item -> aggregatedEventIds.add(item.eventId));
        }

        aggregatedEventIds.addAll(eventIds);

        if (!aggregatedEventIds.isEmpty()) {
            Set<ObjectId> searchedOrganizationIds = this.datastore.createQuery(Event.class)
                .retrievedFields(true, "organizationId")
                .field("id").in(aggregatedEventIds)
                .asList()
                .stream()
                .map(event -> event.organizationId)
                .collect(Collectors.toSet());

            aggregatedOrganizationIds.addAll(searchedOrganizationIds);
        }

        return aggregatedOrganizationIds;
    }
}