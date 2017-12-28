package com.jivecake.api.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
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
    private final Datastore datastore;

    @Inject
    public PermissionService(Datastore datastore) {
        this.datastore = datastore;
    }

    public boolean hasRead(String sub, Collection<?> entities) {
        return this.has(sub, entities, true, false);
    }

    public boolean hasWrite(String sub, Collection<?> entities) {
        return this.has(sub, entities, false, true);
    }

    public boolean has(String sub, Collection<?> entities, boolean read, boolean write) {
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
            } else if (entity instanceof Permission) {
                Permission permission = (Permission)entity;

                if ("Organization".equals(permission.objectClass)) {
                    organizationIds.add(permission.objectId);
                } else {
                    throw new IllegalArgumentException(permission + " is not a valid class");
                }
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

        Query<Permission> query = this.datastore.createQuery(Permission.class);
        List<CriteriaContainer> ors = new ArrayList<>();

        for (ObjectId organizationId: organizationIds) {
            List<CriteriaContainer> criterias = new ArrayList<>(Arrays.asList(
                query.criteria("user_id").equal(sub),
                query.criteria("objectId").equal(organizationId),
                query.criteria("objectClass").equal("Organization")
            ));

            if (read) {
                criterias.add(query.criteria("read").equal(true));
            }

            if (write) {
                criterias.add(query.criteria("write").equal(true));
            }

            ors.add(
                query.and(criterias.toArray(new CriteriaContainer[]{}))
            );
        }

        for (ObjectId applicationId: applicationIds) {
            List<CriteriaContainer> criterias = new ArrayList<>(Arrays.asList(
                query.criteria("user_id").equal(sub),
                query.criteria("objectId").equal(applicationId),
                query.criteria("objectClass").equal("Application")
            ));

            if (read) {
                criterias.add(query.criteria("read").equal(true));
            }

            if (write) {
                criterias.add(query.criteria("write").equal(true));
            }

            ors.add(
                query.and(criterias.toArray(new CriteriaContainer[]{}))
            );
        }

        if (!ors.isEmpty()) {
            query.or(ors.toArray(new CriteriaContainer[]{}));
        }

        int entitiesSize = applicationIds.size() + organizationIds.size();
        return query.count() >= entitiesSize;
    }
}