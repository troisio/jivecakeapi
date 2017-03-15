package com.jivecake.api.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.CriteriaContainer;
import org.mongodb.morphia.query.Query;

import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.PaymentProfile;
import com.jivecake.api.model.Permission;
import com.jivecake.api.model.Transaction;
import com.mongodb.WriteResult;

public class PermissionService {
    public static final int ALL = 0;
    public static final int INCLUDE = 1;
    public static final int EXCLUDE = 2;
    public static final int READ = 0;
    public static final int WRITE = 1;
    private final Datastore datastore;
    private final OrganizationService organizationService;
    private final IndexedOrganizationNodeService indexedOrganizationNodeService;

    @Inject
    public PermissionService(
        Datastore datastore,
        ApplicationService applicationService,
        OrganizationService organizationService,
        IndexedOrganizationNodeService indexedOrganizationNodeService
    ) {
        this.datastore = datastore;
        this.organizationService = organizationService;
        this.indexedOrganizationNodeService = indexedOrganizationNodeService;
    }

    public boolean has(String sub, Collection<?> entities, int permission) {
        Set<ObjectId> organizationIds = new HashSet<>();

        for (Object entity: entities) {
            if (entity instanceof Transaction) {
                organizationIds.add(((Transaction)entity).organizationId);
            } else if (entity instanceof Item) {
                organizationIds.add(((Item)entity).organizationId);
            } else if (entity instanceof Event) {
                organizationIds.add(((Event)entity).organizationId);
            } else if (entity instanceof Organization) {
                organizationIds.add(((Organization)entity).id);
            } else if (entity instanceof PaymentProfile) {
                organizationIds.add(((PaymentProfile)entity).organizationId);
            } else {
                return false;
            }
        }

        return this.hasAllHierarchicalPermission(sub, permission, organizationIds);
    }

    public boolean has(String user_id, Class<?> model, int permission, ObjectId objectId) {
        Query<Permission> query = this.datastore.find(Permission.class);
        query.field("objectClass").equal(model.getSimpleName())
            .field("objectId").equal(objectId)
            .field("user_id").equal(user_id)
            .and(
                 query.or(
                     query.criteria("include").equal(PermissionService.ALL),
                     query.and(
                         query.criteria("include").equal(PermissionService.INCLUDE),
                         query.criteria("permissions").equal(permission)
                     ),
                     query.and(
                         query.criteria("include").equal(PermissionService.EXCLUDE),
                         query.criteria("permissions").notEqual(permission)
                     )
                 )
             );

        List<Permission> result = query.asList();
        return !result.isEmpty();
    }

    public Iterable<Key<Permission>> write(Collection<Permission> permissions) {
        Query<Permission> query = this.query();

        CriteriaContainer[] criterium = permissions.stream()
            .map(permission -> {
                return query.and(
                    query.criteria("objectId").equal(permission.objectId),
                    query.criteria("user_id").equal(permission.user_id)
                );
            })
            .collect(Collectors.toList())
            .toArray(new CriteriaContainer[]{});

        query.or(criterium);

        if (criterium.length > 0) {
            this.datastore.delete(query);
        }

       Iterable<Key<Permission>> result = this.datastore.save(permissions);
       return result;
    }

    public Query<Permission> query() {
        return this.datastore.createQuery(Permission.class);
    }

    public boolean hasAllHierarchicalPermission(String sub, int permission, Set<ObjectId> organizationIds) {
        Query<Permission> query = this.query();
        query.field("user_id").equal(sub)
            .field("objectClass").equal(this.organizationService.getPermissionObjectClass())
            .and(
                query.or(
                     query.criteria("include").equal(PermissionService.ALL),
                     query.and(
                         query.criteria("include").equal(PermissionService.INCLUDE),
                         query.criteria("permissions").equal(permission)
                     ),
                     query.and(
                         query.criteria("include").equal(PermissionService.EXCLUDE),
                         query.criteria("permissions").notEqual(permission)
                     )
                 )
             );

        /*
         * Though the below logic is correct, more filtering needs to be
         * done on 'organizationIdsWithPermission' in a performant manner.
         * This set may also include vertices which are descendants of
         * other vertices in this collection. In this set, we only wish to obtain
         * vertices where no vertex is a descendant of another vertex
         */
        Set<ObjectId> organizationIdsWithPermission = query.asList()
            .stream()
            .map(p -> p.objectId)
            .collect(Collectors.toSet());

        Set<ObjectId> userHasPermissions = this.indexedOrganizationNodeService.query()
            .field("organizationId").in(organizationIdsWithPermission)
            .asList()
            .stream()
            .map(node -> node.childIds)
            .flatMap(Set::stream)
            .collect(Collectors.toSet());

        Set<ObjectId> treePermissions = new HashSet<>();
        treePermissions.addAll(organizationIdsWithPermission);
        treePermissions.addAll(userHasPermissions);

        boolean result = treePermissions.containsAll(organizationIds);
        return result;
    }

    public WriteResult delete(Query<Permission> query) {
        WriteResult result = this.datastore.delete(query);
        return result;
    }
}