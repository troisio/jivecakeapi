package com.jivecake.api.request;

import java.util.Collection;

public class Paging<A> {
    public final long count;
    public final Collection<A> entity;

    public Paging(Collection<A> entity, long count) {
        this.entity = entity;
        this.count = count;
    }
}