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

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.CriteriaContainer;
import org.mongodb.morphia.query.Query;

import com.jivecake.api.model.Event;
import com.jivecake.api.model.IndexedOrganizationNode;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.Permission;
import com.jivecake.api.model.Transaction;
import com.mongodb.WriteResult;

public class PermissionService {
    private final Datastore datastore;
    private final MappingService mappingService;
    private final Map<String, Set<String>> permissions = new HashMap<>();
    private final OrganizationService organizationService;
    private final IndexedOrganizationNodeService indexedOrganizationNodeService;

    @Inject
    public PermissionService(
        Datastore datastore,
        MappingService mappingService,
        ApplicationService applicationService,
        OrganizationService organizationService,
        IndexedOrganizationNodeService indexedOrganizationNodeService
    ) {
        this.datastore = datastore;
        this.mappingService = mappingService;
        this.permissions.put(applicationService.getPermissionObjectClass(), applicationService.getPermissions());
        this.permissions.put(organizationService.getPermissionObjectClass(), organizationService.getPermissions());
        this.organizationService = organizationService;
        this.indexedOrganizationNodeService = indexedOrganizationNodeService;
    }

    public Map<String, Set<String>> getPermissionsByObjectClass() {
        return this.permissions;
    }

    public boolean has(String user_id, Collection<?> entities, String permission) {
        Set<ObjectId> transactionIds = new HashSet<>();
        Set<ObjectId> itemIds = new HashSet<>();
        Set<ObjectId> eventIds = new HashSet<>();
        Set<ObjectId> organizationIds = new HashSet<>();

        for (Object entity: entities) {
            if (entity instanceof Transaction) {
                ObjectId id = ((Transaction)entity).id;
                transactionIds.add(id);
            } else if (entity instanceof Item) {
                ObjectId id = ((Item)entity).id;
                itemIds.add(id);
            } else if (entity instanceof Event) {
                ObjectId id = ((Event)entity).id;
                eventIds.add(id);
            } else if (entity instanceof Organization) {
                ObjectId id = ((Organization)entity).id;
                organizationIds.add(id);
            } else {
                return false;
            }
        }

        Set<ObjectId> aggregatedOrganizationIds = this.mappingService.getOrganizationIds(transactionIds, itemIds, eventIds);
        aggregatedOrganizationIds.addAll(organizationIds);

        return this.hasAllHierarchicalPermission(user_id, permission, aggregatedOrganizationIds);
    }

    public boolean has(String user_id, Class<?> model, String permission, ObjectId objectId) {
        Query<Permission> query = this.datastore.find(Permission.class);
        query.field("objectClass").equal(model.getSimpleName())
            .field("objectId").equal(objectId)
            .field("user_id").equal(user_id)
            .and(
                 query.or(
                     query.criteria("include").equal(this.getIncludeAllPermission()),
                     query.and(
                         query.criteria("include").equal(this.getIncludePermision()),
                         query.criteria("permissions").equal(permission)
                     ),
                     query.and(
                         query.criteria("include").equal(this.getExcludePermission()),
                         query.criteria("permissions").notEqual(permission)
                     )
                 )
             );

        List<Permission> result = query.asList();
        return !result.isEmpty();
    }

    public boolean hasAny(String user_id, Class<?> model, String permission, Collection<ObjectId> objectIds) {
        return !this.search(user_id, model, permission, objectIds).isEmpty();
    }

    public List<Permission> search(String user_id, Class<?> model, String permission, Collection<ObjectId> ids) {
        Query<Permission> query = this.query();
        query.field("objectClass").equal(model.getSimpleName())
            .field("objectId").in(ids)
            .field("user_id").equal(user_id)
            .and(
                query.or(
                     query.criteria("include").equal(this.getIncludeAllPermission()),
                     query.and(
                         query.criteria("include").equal(this.getIncludePermision()),
                         query.criteria("permissions").equal(permission)
                     ),
                     query.and(
                         query.criteria("include").equal(this.getExcludePermission()),
                         query.criteria("permissions").notEqual(permission)
                     )
                 )
             );

        return query.asList();
    }

    public boolean hasAnyHierarchicalPermission(ObjectId organizationId, String user_id, String permission) {
        boolean result;
        Set<String> organizationPermissions = this.permissions.get(this.organizationService.getPermissionObjectClass());

        if (organizationPermissions.contains(permission)) {
            Query<IndexedOrganizationNode> query = this.indexedOrganizationNodeService.query()
                .field("organizationId").equal(organizationId);

            List<IndexedOrganizationNode> nodes = this.indexedOrganizationNodeService.threadSafeQuery(query);
            IndexedOrganizationNode node = nodes.get(0);

            List<ObjectId> lineage = new ArrayList<>(node.parentIds);
            lineage.add(organizationId);

            result = this.hasAny(
                user_id,
                Organization.class,
                permission,
                lineage
            );
        } else {
            result = false;
        }

        return result;
    }

    public List<Permission> read() {
        List<Permission> result = this.datastore.find(Permission.class).asList();
        return result;
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

    public boolean hasAllHierarchicalPermission(String user_id, String permission, Set<ObjectId> organizationIds) {
        Query<Permission> query = this.query();
        query.field("user_id").equal(user_id)
            .field("objectClass").equal(this.organizationService.getPermissionObjectClass())
            .and(
                query.or(
                     query.criteria("include").equal(this.getIncludeAllPermission()),
                     query.and(
                         query.criteria("include").equal(this.getIncludePermision()),
                         query.criteria("permissions").equal(permission)
                     ),
                     query.and(
                         query.criteria("include").equal(this.getExcludePermission()),
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

    public int getIncludeAllPermission() {
        return 0;
    }

    public int getIncludePermision() {
        return 1;
    }

    public int getExcludePermission() {
        return 2;
    }
}