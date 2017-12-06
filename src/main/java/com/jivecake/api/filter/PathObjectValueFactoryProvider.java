package com.jivecake.api.filter;

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import org.bson.types.ObjectId;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.jersey.server.internal.inject.AbstractContainerRequestValueFactory;
import org.glassfish.jersey.server.internal.inject.AbstractValueFactoryProvider;
import org.glassfish.jersey.server.internal.inject.MultivaluedParameterExtractorProvider;
import org.glassfish.jersey.server.model.Parameter;
import org.mongodb.morphia.Datastore;

public class PathObjectValueFactoryProvider extends AbstractValueFactoryProvider {
    private final Datastore datastore;

    @Inject
    public PathObjectValueFactoryProvider(MultivaluedParameterExtractorProvider extractorProvider, ServiceLocator injector, Datastore datastore) {
        super(extractorProvider, injector, Parameter.Source.UNKNOWN);
        this.datastore = datastore;
    }

    @Override
    protected Factory<?> createValueFactory(Parameter parameter) {
        AbstractContainerRequestValueFactory<Object> result;

        if (parameter.isAnnotationPresent(PathObject.class)) {
            PathObject pathObject = parameter.getAnnotation(PathObject.class);
            Class<?> collection = pathObject.collection();
            Class<?> clazz;

            if (collection.equals(Object.class)) {
                clazz = parameter.getRawType();
            } else {
                clazz = collection;
            }

            result = new AbstractContainerRequestValueFactory<Object>() {
                @Context
                private UriInfo info;

                @Override
                public Object provide() {
                    Object result = null;

                    List<String> value = this.info.getPathParameters(true).get(pathObject.value());

                    if (!value.isEmpty()) {
                        ObjectId id;

                        try {
                            id = new ObjectId(value.get(0));
                        } catch (IllegalArgumentException e) {
                            id = null;
                        }

                        if (id != null) {
                            result = PathObjectValueFactoryProvider.this.datastore.get(clazz, id);
                        }
                    }

                    return result;
                }
            };
        } else {
            result = null;
        }

        return result;
    }
}