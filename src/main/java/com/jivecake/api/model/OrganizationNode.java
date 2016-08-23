package com.jivecake.api.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;

import org.bson.types.ObjectId;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class OrganizationNode {
    @JsonIgnore
    public OrganizationNode parent;
    public final Organization organization;
    public final List<OrganizationNode> children = new ArrayList<>();

    public OrganizationNode(Organization organization) {
        this.organization = organization;
    }

    @JsonIgnore
    public Set<OrganizationNode> getDescendants() {
        Set<OrganizationNode> result = new HashSet<>();

        Queue<OrganizationNode> visit = new ArrayDeque<>();
        visit.addAll(this.children);

        while (!visit.isEmpty()) {
            OrganizationNode node = visit.poll();
            visit.addAll(node.children);
            result.add(node);
        }

        return result;
    }

    public void traverse(Consumer<OrganizationNode> consumer) {
        Queue<OrganizationNode> visit = new ArrayDeque<>();
        visit.add(this);

        while (!visit.isEmpty()) {
            OrganizationNode node = visit.poll();
            visit.addAll(node.children);
            consumer.accept(node);
        }
    }

    public OrganizationNode find(ObjectId id) {
        Queue<OrganizationNode> visit = new ArrayDeque<>();
        visit.add(this);

        while (!visit.isEmpty()) {
            OrganizationNode node = visit.poll();

            if (node.organization.id.equals(id)) {
                return node;
            } else {
                visit.addAll(node.children);
            }
        }

        return null;
    }

    @JsonIgnore
    public List<OrganizationNode> getLineage() {
        List<OrganizationNode> result = new ArrayList<>();

        for (OrganizationNode target = this; target != null; target = target.parent) {
            result.add(target);
        }

        Collections.reverse(result);
        return result;
    }
}