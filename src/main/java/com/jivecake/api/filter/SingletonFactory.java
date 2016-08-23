package com.jivecake.api.filter;

import org.glassfish.hk2.api.Factory;

public class SingletonFactory <A> implements Factory<A> {
    private final A a;

    public SingletonFactory(A a) {
        this.a = a;
    }

    @Override
    public A provide() {
        return this.a;
    }

    @Override
    public void dispose(A instance) {
    }
}