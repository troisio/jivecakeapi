package com.jivecake.api.filter;

import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.core.Configuration;
import javax.ws.rs.ext.Provider;

import org.glassfish.jersey.server.model.ModelProcessor;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceModel;

@Provider
public class OptionsProcessor implements ModelProcessor {
    private static Resource.Builder iterate(Resource resource, Resource.Builder parent) {
        Set<String> methods = resource.getAllMethods()
            .stream()
            .map(method -> method.getHttpMethod())
            .collect(Collectors.toSet());

        Resource.Builder builder = Resource.builder().path(resource.getPath());
        builder.addMethod("OPTIONS").handledBy(new OptionsInflector(methods));

        for (Resource child: resource.getChildResources()) {
            Resource.Builder childBuilder = iterate(child, builder);
            builder.addChildResource(childBuilder.build());
        }

        return builder;
    }

    @Override
    public ResourceModel processResourceModel(ResourceModel model, Configuration configuration) {
        ResourceModel.Builder builder = new ResourceModel.Builder(model, true);

        for (Resource resource: model.getRootResources()) {
            Resource.Builder resourceBuilder = iterate(resource, Resource.builder());
            builder.addResource(resourceBuilder.build());
        }

        return builder.build();
    }

    @Override
    public ResourceModel processSubResource(ResourceModel model, Configuration configuration) {
        return model;
    }
}