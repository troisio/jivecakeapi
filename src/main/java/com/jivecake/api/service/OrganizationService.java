package com.jivecake.api.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.UpdateOperations;

import com.jivecake.api.model.Organization;

public class OrganizationService {
    private final Datastore datastore;

    @Inject
    public OrganizationService(Datastore datastore) {
        this.datastore = datastore;
    }

    public Organization getRootOrganization() {
        return this.datastore.createQuery(Organization.class).field("parentId").equal(null).get();
    }

    public String getPermissionObjectClass() {
        return Organization.class.getSimpleName();
    }

    public static boolean isValid(Organization organization) {
        return organization != null &&
            organization.email != null &&
            organization.email.contains("@") &&
            organization.name != null &&
            organization.name.length() > 0 &&
            organization.name.length() <= 100;
    }

    public void reindex() {
        Map<Organization, List<Organization>> organizationToDescendants = this.getDescendants();

        organizationToDescendants.forEach((organization, children) -> {
            List<ObjectId> childIds = children.stream()
                .map(org -> org.id)
                .collect(Collectors.toList());

            UpdateOperations<Organization> operations = this.datastore
                .createUpdateOperations(Organization.class)
                .set("children", childIds);
            this.datastore.update(organization, operations);
        });
    }

    public Map<Organization, List<Organization>> getDescendants() {
        List<Organization> organizations = this.datastore.createQuery(Organization.class)
            .asList();

        Map<ObjectId, Organization> organizationToId = organizations.stream()
            .collect(Collectors.toMap(organization -> organization.id, Function.identity()));

        Map<Organization, List<Organization>> organizationToDescendants = organizations.stream()
            .collect(Collectors.toMap(Function.identity(), organization -> new ArrayList<>()));

        for (Organization organization: organizations) {
            for (Organization pointer = organizationToId.get(organization.parentId); pointer != null; pointer = organizationToId.get(pointer.parentId)) {
                organizationToDescendants.get(pointer).add(organization);
            }
        }

        return organizationToDescendants;
    }
}