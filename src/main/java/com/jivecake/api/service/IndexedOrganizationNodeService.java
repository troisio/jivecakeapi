package com.jivecake.api.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;

import com.jivecake.api.model.IndexedOrganizationNode;
import com.jivecake.api.model.OrganizationNode;

public class IndexedOrganizationNodeService {
    private final OrganizationService organizationService;
    private final Datastore datastore;
    private final Object nodesReadWriteMutex = new Object();

    @Inject
    public IndexedOrganizationNodeService(Datastore datastore, OrganizationService organizationService) {
        this.datastore = datastore;
        this.organizationService = organizationService;
    }

    public Iterable<Key<IndexedOrganizationNode>> writeIndexedOrganizationNodes(ObjectId id) {
        List<IndexedOrganizationNode> nodes = this.getIndexedOrganizationNodes(id);

        Date time = new Date();

        for (IndexedOrganizationNode node: nodes) {
            node.timeCreated = time;
        }

        Iterable<Key<IndexedOrganizationNode>> keys;
        Query<IndexedOrganizationNode> query = this.datastore.createQuery(IndexedOrganizationNode.class);

        synchronized (this.nodesReadWriteMutex) {
           this.datastore.delete(query);
           keys = this.datastore.save(nodes);
        }

        return keys;
    }

    public List<IndexedOrganizationNode> getIndexedOrganizationNodes(ObjectId id) {
        OrganizationNode tree = this.organizationService.getOrganizationTree(id);

        List<IndexedOrganizationNode> result = new ArrayList<>();

        tree.traverse((node) -> {
            List<ObjectId> lineage = node.getLineage().stream()
                .map(subject -> subject.organization.id)
                .collect(Collectors.toList());
            List<ObjectId> ancestors = lineage.subList(0, lineage.size() - 1);

            IndexedOrganizationNode indexedNode = new IndexedOrganizationNode();
            indexedNode.organizationId = node.organization.id;
            indexedNode.parentIds = ancestors;
            indexedNode.childIds = node.getDescendants()
                    .stream()
                    .map(n -> n.organization.id)
                    .collect(Collectors.toSet());

            result.add(indexedNode);
        });

        return result;
    }

    /**
     * Submits a query in a thread safe manner. Namely, this method will not execute
     * during the delete/write portion of the this.writeIndexedOrganizationNodes method
     **/
    public List<IndexedOrganizationNode> threadSafeQuery(Query<IndexedOrganizationNode> query) {
        synchronized (this.nodesReadWriteMutex) {
            return query.asList();
        }
    }

    public Query<IndexedOrganizationNode> query() {
        return this.datastore.createQuery(IndexedOrganizationNode.class);
    }
}