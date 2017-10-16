package com.jivecake.api.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;

import org.bson.types.ObjectId;
import org.glassfish.jersey.media.sse.OutboundEvent;

import com.jivecake.api.model.EntityAsset;
import com.jivecake.api.model.EntityType;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.EventBroadcaster;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.OrganizationInvitation;
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

    public void sendEvent(String userId, OutboundEvent chunk) {
        if (this.clientConnectionService.broadcasters.containsKey(userId)) {
            EventBroadcaster eventBroadcaster = this.clientConnectionService.broadcasters.get(userId);
            eventBroadcaster.broadcaster.broadcast(chunk);
        }
    }

    public void notify(Collection<Object> entities, String name) {
        Map<ObjectId, List<Object>> organizationToEntities = new HashMap<>();
        Map<String, List<Object>> userToEntities = new HashMap<>();

        for (Object entity: entities) {
            ObjectId organizationId = null;
            List<String> userIds = new ArrayList<>();

            if (entity instanceof Item) {
                organizationId = ((Item)entity).organizationId;
            } else if (entity instanceof Event) {
                organizationId = ((Event)entity).organizationId;
            } else if (entity instanceof Permission) {
                Permission permission = (Permission)entity;

                userIds.add(permission.user_id);

                if (permission.objectClass.equals(this.organizationService.getPermissionObjectClass())) {
                    organizationId = permission.objectId;
                } else {
                    throw new IllegalArgumentException(entity + " is not a valid class for notification");
                }
            } else if (entity instanceof Organization) {
                organizationId = ((Organization)entity).id;
            } else if (entity instanceof PaymentProfile) {
                organizationId = ((PaymentProfile)entity).organizationId;
            } else if (entity instanceof Transaction) {
                Transaction transaction = (Transaction)entity;
                organizationId = transaction.organizationId;
                userIds.add(transaction.user_id);
            } else if (entity instanceof EntityAsset) {
                EntityAsset asset = (EntityAsset)entity;

                if (asset.entityType == EntityType.USER) {
                    userIds.add(asset.entityId);
                    /*
                     * Additionally, we ought to figure out how to notify an organization with
                     * an active Event which has a transaction associated with this user
                     */
                } else if (asset.entityType == EntityType.ORGANIZATION) {
                    organizationId = new ObjectId(asset.entityId);
                } else {
                    throw new IllegalArgumentException(entity + " is not a valid class for notification");
                }
            } else if (entity instanceof OrganizationInvitation) {
                OrganizationInvitation invitation = (OrganizationInvitation)entity;

                organizationId = invitation.organizationId;
                userIds.addAll(invitation.userIds);
            } else {
                throw new IllegalArgumentException(entity + " is not a valid class for notification");
            }

            if (organizationId != null) {
                if (!organizationToEntities.containsKey(organizationId)) {
                    organizationToEntities.put(organizationId, new ArrayList<>());
                }

                organizationToEntities.get(organizationId).add(entity);
            }

            for (String userId : userIds) {
                if (!userToEntities.containsKey(userId)) {
                    userToEntities.put(userId, new ArrayList<>());
                }

                userToEntities.get(userId).add(entity);
            }
        }

        Map<ObjectId, List<Permission>> permissions = this.permissionService.getQueryWithPermission(PermissionService.READ)
            .field("objectClass").equal(this.organizationService.getPermissionObjectClass())
            .field("objectId").in(organizationToEntities.keySet())
            .asList()
            .stream()
            .collect(Collectors.groupingBy(permission -> permission.objectId));

        Map<String, Set<Object>> userToObjects = new HashMap<>();

        organizationToEntities.forEach((organizationId, objects) -> {
            permissions.get(organizationId)
                .stream()
                .map(permission -> permission.user_id)
                .forEach(userId -> {
                    if (!userToObjects.containsKey(userId)) {
                        userToObjects.put(userId, new HashSet<>());
                    }

                    userToObjects.get(userId).addAll(objects);
                });
        });

        userToEntities.forEach((userId, objects) -> {
            if (!userToObjects.containsKey(userId)) {
                userToObjects.put(userId, new HashSet<>());
            }

            userToObjects.get(userId).addAll(objects);
        });

        userToObjects.forEach((userId, objects) -> {
            OutboundEvent chunk = new OutboundEvent.Builder()
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .name(name)
                .data(objects)
                .build();

            this.sendEvent(userId, chunk);
        });
    }
}