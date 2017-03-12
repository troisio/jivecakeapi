package com.jivecake.api.service;

import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;

import org.bson.types.ObjectId;
import org.glassfish.jersey.media.sse.OutboundEvent;

import com.jivecake.api.model.Transaction;

public class NotificationService {
    private final TransactionService transactionService;
    private final OrganizationService organizationService;
    private final PermissionService permissionService;
    private final ClientConnectionService clientConnectionService;

    @Inject
    public NotificationService(
        TransactionService transactionService,
        OrganizationService organizationService,
        PermissionService permissionService,
        ClientConnectionService clientConnectionService
    ) {
        this.transactionService = transactionService;
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

        Set<String> userIds = this.permissionService.query()
            .project("user_id", true)
            .field("objectClass").equal(this.organizationService.getPermissionObjectClass())
            .field("objectId").equal(transaction.organizationId)
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
}