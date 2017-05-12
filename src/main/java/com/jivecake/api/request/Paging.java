package com.jivecake.api.request;

import java.util.Collection;

public class Paging<A> {
    public final Collection<A> entity;
    public final long count;

    public Paging(Collection<A> entity, long count) {
        this.entity = entity;
        this.count = count;
    }
}