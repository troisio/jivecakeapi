package com.jivecake.api.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.media.sse.OutboundEvent;
import org.mongodb.morphia.Datastore;

import com.jivecake.api.model.Permission;
import com.jivecake.api.model.Transaction;

public class NotificationService {
    private final OrganizationService organizationService;
    private final ClientConnectionService clientConnectionService;
    private final Datastore datastore;

    @Inject
    public NotificationService(
        OrganizationService organizationService,
        ClientConnectionService clientConnectionService,
        Datastore datastore
    ) {
        this.organizationService = organizationService;
        this.clientConnectionService = clientConnectionService;
        this.datastore = datastore;
    }

    public void sendEvent(Set<String> userIds, OutboundEvent chunk) {
        this.clientConnectionService.broadcasters
            .stream()
            .filter(broadcaster -> userIds.contains(broadcaster.user_id))
            .forEach((broadcaster) -> broadcaster.broadcaster.broadcast(chunk));
    }

    public void notifyPermissionWrite(Collection<Permission> permissions) {
        permissions.stream()
            .collect(
                Collectors.groupingBy(
                    permission -> permission.user_id
                )
            ).forEach((userId, entity) -> {
                OutboundEvent chunk = new OutboundEvent.Builder()
                    .mediaType(MediaType.APPLICATION_JSON_TYPE)
                    .name("permission.write")
                    .data(entity)
                    .build();

                this.sendEvent(new HashSet<>(Arrays.asList(userId)), chunk);
            });
    }

    public void notifyPermissionDelete(Collection<Permission> permissions) {
        permissions.stream()
            .collect(
                Collectors.groupingBy(
                    permission -> permission.user_id
                )
            ).forEach((userId, entity) -> {
                OutboundEvent chunk = new OutboundEvent.Builder()
                    .mediaType(MediaType.APPLICATION_JSON_TYPE)
                    .name("permission.delete")
                    .data(entity)
                    .build();

                this.sendEvent(new HashSet<>(Arrays.asList(userId)), chunk);
            });
    }

    public void notifyItemTransaction(Transaction transaction) {
        Set<String> userIds = this.datastore.createQuery(Permission.class)
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
            .name("transaction.create")
            .data(Transaction.class, transaction)
            .build();

        this.sendEvent(userIds, notification);
    }
}