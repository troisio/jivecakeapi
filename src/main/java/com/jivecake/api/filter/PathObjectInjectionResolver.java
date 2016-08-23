package com.jivecake.api.filter;

import javax.inject.Singleton;

import org.glassfish.jersey.server.internal.inject.ParamInjectionResolver;

@Singleton
public class PathObjectInjectionResolver extends ParamInjectionResolver<PathObject> {
    public PathObjectInjectionResolver() {
        super(PathObjectValueFactoryProvider.class);
    }
}