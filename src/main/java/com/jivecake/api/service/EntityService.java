package com.jivecake.api.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.UpdateResults;

import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.PaymentProfile;
import com.jivecake.api.model.Transaction;

public class EntityService {
    private final Datastore datastore;

    @Inject
    public EntityService(Datastore datastore) {
        this.datastore = datastore;
    }

    public List<UpdateResults> cascadeLastActivity(Collection<?> entities, Date date) {
        Set<ObjectId> organizationIds = new HashSet<>();
        Set<ObjectId> eventIds = new HashSet<>();
        Set<ObjectId> itemIds = new HashSet<>();

        for (Object entity: entities) {
            if (entity instanceof Transaction) {
                Transaction transaction = (Transaction)entity;
                organizationIds.add(transaction.organizationId);
                eventIds.add(transaction.eventId);
                itemIds.add(transaction.itemId);
            } else if (entity instanceof Item) {
                Item item = (Item)entity;
                organizationIds.add(item.organizationId);
                eventIds.add(item.eventId);
                itemIds.add(item.id);
            } else if (entity instanceof Event) {
                Event event = (Event)entity;
                organizationIds.add(event.organizationId);
                eventIds.add(event.id);
            } else if (entity instanceof Organization) {
                Organization organization = (Organization)entity;
                organizationIds.add(organization.id);
            } else if (entity instanceof PaymentProfile) {
                PaymentProfile profile = (PaymentProfile)entity;
                organizationIds.add(profile.organizationId);
            } else {
                throw new IllegalArgumentException(entity.getClass() + " is not a valid class for cascadeLastActivity");
            }
        }

        UpdateResults organizationResult = this.datastore.update(
            this.datastore.createQuery(Organization.class)
                .field("id").in(organizationIds),
            this.datastore.createUpdateOperations(Organization.class)
                .set("lastActivity", date)
        );

        UpdateResults eventResult = this.datastore.update(
            this.datastore.createQuery(Event.class)
                .field("id").in(eventIds),
            this.datastore.createUpdateOperations(Event.class)
                .set("lastActivity", date)
        );

        UpdateResults itemResult = this.datastore.update(
            this.datastore.createQuery(Item.class)
                .field("id").in(itemIds),
            this.datastore.createUpdateOperations(Item.class)
                .set("lastActivity", date)
        );

        return Arrays.asList(organizationResult, eventResult, itemResult);
    }
}