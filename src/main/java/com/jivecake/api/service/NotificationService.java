package com.jivecake.api.service;

import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.core.MediaType;

import org.bson.types.ObjectId;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.mongodb.morphia.Datastore;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.OrganizationFeature;
import com.jivecake.api.model.Transaction;

@Singleton
public class NotificationService {
    private final Datastore datastore;
    private final TransactionService transactionService;
    private final ItemService itemService;
    private final OrganizationService organizationService;
    private final PermissionService permissionService;
    private final ClientConnectionService clientConnectionService;

    @Inject
    public NotificationService(
        Datastore datastore,
        TransactionService transactionService,
        ItemService itemService,
        OrganizationService organizationService,
        PermissionService permissionService,
        ClientConnectionService clientConnectionService
    ) {
        this.datastore = datastore;
        this.transactionService = transactionService;
        this.itemService = itemService;
        this.organizationService = organizationService;
        this.permissionService = permissionService;
        this.clientConnectionService = clientConnectionService;
    }

    public void sendEvent(Set<String> user_ids, OutboundEvent chunk) {
        this.clientConnectionService.getBroadcasters()
            .stream()
            .filter(broadcaster -> user_ids.contains(broadcaster.user_id))
            .forEach((broadcaster) -> {
                broadcaster.broadcaster.broadcast(chunk);
             });
    }

    public void notifyItemTransaction(ObjectId id) {
        Transaction transaction = this.transactionService.read(id);
        Item item = this.itemService.read(transaction.itemId);
        Event event = this.datastore.find(Event.class).field("id").equal(item.eventId).get();
        Organization organization = this.organizationService.read(event.organizationId);

        Set<String> userIds = this.permissionService.query()
            .retrievedFields(true, "user_id")
            .field("objectClass").equal(this.organizationService.getPermissionObjectClass())
            .field("objectId").equal(organization.id)
            .asList()
            .stream()
            .map(permission -> permission.user_id)
            .collect(Collectors.toSet());

        if (transaction.user_id != null) {
            userIds.add(transaction.user_id);
        }

        OutboundEvent notification = new OutboundEvent.Builder()
            .mediaType(MediaType.APPLICATION_JSON_TYPE)
            .name(this.transactionService.getItemTransactionCreatedEventName())
            .data(Transaction.class, transaction)
            .build();

        this.sendEvent(userIds, notification);
    }

    public void notifyNewSubscription(OrganizationFeature feature) {
    }
}