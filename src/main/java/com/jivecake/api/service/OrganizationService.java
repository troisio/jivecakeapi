package com.jivecake.api.service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.OrganizationNode;

@Singleton
public class OrganizationService {
    private final Set<String> permissions = new HashSet<>(Arrays.asList(
        this.getWritePermission(),
        this.getReadPermission()
    ));
    private final Datastore datastore;

    @Inject
    public OrganizationService(Datastore datastore) {
        this.datastore = datastore;
    }

    public Key<Organization> create(Organization organization) {
        Key<Organization> result = this.datastore.save(organization);
        return result;
    }

    public Organization getRootOrganization() {
        return this.datastore.createQuery(Organization.class).field("parentId").equal(null).get();
    }

    public Query<Organization> query() {
        return this.datastore.createQuery(Organization.class);
    }

    public Organization read(ObjectId id) {
        Organization result = this.datastore.find(Organization.class)
            .field("id").equal(id)
            .get();
        return result;
    }

    public Organization delete(ObjectId id) {
        Query<Organization> deleteQuery = this.datastore.createQuery(Organization.class).filter("id", id);
        Organization result = this.datastore.findAndDelete(deleteQuery);
        return result;
    }

    public Set<String> getPermissions() {
        return this.permissions;
    }

    public String getPermissionObjectClass() {
        return Organization.class.getSimpleName();
    }

    public OrganizationNode getOrganizationTree(ObjectId id) {
        OrganizationNode result;
        List<Organization> organizations = this.query().asList();

        Optional<Organization> optionalRoot = organizations.stream()
            .filter(org -> org.id.equals(id))
            .findFirst();

        if (optionalRoot.isPresent()) {
            Map<ObjectId, OrganizationNode> idToNode = organizations.stream()
                .collect(Collectors.toMap(organization -> organization.id, organization -> new OrganizationNode(organization)));

            for (Organization organization: organizations) {
                OrganizationNode parent = idToNode.get(organization.parentId);

                if (parent != null) {
                    OrganizationNode self = idToNode.get(organization.id);
                    self.parent = parent;
                    parent.children.add(self);
                }
            }

            result = idToNode.get(id);
        } else {
            result = null;
        }

        return result;
    }

    public String getWritePermission() {
        return "WRITE";
    }

    public String getReadPermission() {
        return "READ";
    }

    public Key<Organization> save(Organization organization) {
        return this.datastore.save(organization);
    }
}