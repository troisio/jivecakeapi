package com.jivecake.api.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;

import org.bson.types.ObjectId;
import org.glassfish.jersey.media.sse.OutboundEvent;

import com.jivecake.api.model.Event;
import com.jivecake.api.model.EventBroadcaster;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.PaymentProfile;
import com.jivecake.api.model.Permission;
import com.jivecake.api.model.Transaction;

public class NotificationService {
    private final OrganizationService organizationService;
    private final PermissionService permissionService;
    private final ClientConnectionService clientConnectionService;

    @Inject
    public NotificationService(
        OrganizationService organizationService,
        PermissionService permissionService,
        ClientConnectionService clientConnectionService
    ) {
        this.organizationService = organizationService;
        this.clientConnectionService = clientConnectionService;
        this.permissionService = permissionService;
    }

    public void sendEvent(Set<String> userIds, OutboundEvent chunk) {
        for (String userId: userIds) {
            if (this.clientConnectionService.broadcasters.containsKey(userId)) {
                EventBroadcaster eventBroadcaster = this.clientConnectionService.broadcasters.get(userId);
                eventBroadcaster.broadcaster.broadcast(chunk);
            }
        }
    }

    /*
     * The logic below is naive and not correct.
     * This method will execute correctly only when (after population) organizationIds.size() == 1
     * Otherwise we may be sending the entire entities collection to users who may not
     * have permission to view them
     *
     * Currently, this method is called in no place where that can happen but it ought to
     * be rewritten so that items are seperated into collections by {entity}.organizationId
     * so they can be sent to the correct places
     * */
    public void notify(Collection<Object> entities, String name) {
        Set<ObjectId> organizationIds = new HashSet<>();

        for (Object entity: entities) {
            if (entity instanceof Item) {
                organizationIds.add(((Item)entity).organizationId);
            } else if (entity instanceof Event) {
                organizationIds.add(((Event)entity).organizationId);
            } else if (entity instanceof Permission) {
                Permission permission = (Permission)entity;

                if (permission.objectClass.equals(this.organizationService.getPermissionObjectClass())) {
                    organizationIds.add(permission.objectId);
                } else {
                    throw new IllegalArgumentException(entity + " is not a valid class for notification");
                }
            } else if (entity instanceof Organization) {
                organizationIds.add(((Organization)entity).id);
            } else if (entity instanceof PaymentProfile) {
                organizationIds.add(((PaymentProfile)entity).organizationId);
            } else if (entity instanceof Transaction) {
                organizationIds.add(((Transaction)entity).organizationId);
            } else {
                throw new IllegalArgumentException(entity + " is not a valid class for notification");
            }
        }

        List<Permission> permissions = this.permissionService.getQueryWithPermission(PermissionService.READ)
            .field("objectClass").equal(this.organizationService.getPermissionObjectClass())
            .field("objectId").in(organizationIds)
            .asList();

        permissions.forEach(permission -> {
            OutboundEvent chunk = new OutboundEvent.Builder()
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .name(name)
                .data(entities)
                .build();

            Set<String> userIds = new HashSet<>();
            userIds.add(permission.user_id);

            this.sendEvent(userIds, chunk);
        });
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
}