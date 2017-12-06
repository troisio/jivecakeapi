package com.jivecake.api.service;

import java.util.ArrayList;
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

import com.jivecake.api.model.Application;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.PaymentProfile;
import com.jivecake.api.model.Permission;
import com.jivecake.api.model.Transaction;

public class PermissionService {
    public static final int ALL = 0;
    public static final int INCLUDE = 1;
    public static final int EXCLUDE = 2;
    public static final int READ = 0;
    public static final int WRITE = 1;
    private final Datastore datastore;
    private final OrganizationService organizationService;

    @Inject
    public PermissionService(
        Datastore datastore,
        ApplicationService applicationService,
        OrganizationService organizationService
    ) {
        this.datastore = datastore;
        this.organizationService = organizationService;
    }

    public boolean has(String sub, Collection<?> entities, int permission) {
        Set<ObjectId> organizationIds = new HashSet<>();
        List<ObjectId> applicationIds = new ArrayList<>();

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
            } else if (entity instanceof Application) {
                applicationIds.add(((Application)entity).id);
            } else {
                throw new IllegalArgumentException(entity.getClass() + " is not a valid class for permissions");
            }
        }

        if (applicationIds.size() > 1) {
            throw new IllegalArgumentException("argument \"entities\" contains more than 1 Application");
        }

        boolean hasApplicationPermission = applicationIds.isEmpty() ||
            this.has(sub, Application.class, permission, applicationIds.get(0));
        boolean hasOrganizationPermissions = organizationIds.isEmpty() ||
            this.hasAllHierarchicalPermission(sub, permission, organizationIds);

        return hasApplicationPermission && hasOrganizationPermissions;
    }

    public boolean has(String userId, Class<?> model, int permission, ObjectId objectId) {
        Query<Permission> query = this.datastore.find(Permission.class);
        query.field("objectClass").equal(model.getSimpleName())
            .field("objectId").equal(objectId)
            .field("user_id").equal(userId)
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
        Query<Permission> query = this.datastore.createQuery(Permission.class);

        CriteriaContainer[] criterium = permissions.stream()
            .map(permission -> {
                return query.and(
                    query.criteria("objectClass").equal(permission.objectClass),
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

        return this.datastore.save(permissions);
    }

    public Query<Permission> getQueryWithPermission(int permission) {
        Query<Permission> query = this.datastore.createQuery(Permission.class);
        query.and(
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
        return query;
    }

    public boolean hasAllHierarchicalPermission(String sub, int permission, Collection<ObjectId> organizationIds) {
        Query<Permission> query = this.datastore.createQuery(Permission.class);

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

        Set<ObjectId> hasOrganizationPermissions = query.asList()
            .stream()
            .map(p -> p.objectId)
            .collect(Collectors.toSet());

        this.datastore.createQuery(Organization.class)
            .field("id").in(hasOrganizationPermissions)
            .asList()
            .forEach(organization -> hasOrganizationPermissions.addAll(organization.children));

        return hasOrganizationPermissions.containsAll(organizationIds);
    }

    public static boolean isValid(Permission permission) {
        boolean validPermissions;

        if (permission.permissions == null) {
            validPermissions = false;
        } else {
            Set<Integer> permissions = new HashSet<>(permission.permissions);
            permissions.remove(PermissionService.WRITE);
            permissions.remove(PermissionService.READ);

            validPermissions = permissions.isEmpty();
        }

        return validPermissions && (permission.include == PermissionService.ALL ||
            permission.include == PermissionService.INCLUDE ||
            permission.include == PermissionService.EXCLUDE);
    }
}