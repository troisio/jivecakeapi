package com.jivecake.api.service;

import java.util.stream.Collectors;

import javax.inject.Inject;

import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.mapping.MapperOptions;

import com.jivecake.api.APIConfiguration;
import com.jivecake.api.model.Application;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;

public class ApplicationService {
    public static final int LIMIT_DEFAULT = 100;
    private final Application application;

    @Inject
    public ApplicationService(Application application) {
        this.application = application;
    }

    public Application read() {
        return this.application;
    }

    public static MongoClient getClient(APIConfiguration configuration) {
        return new MongoClient(configuration.databases
            .stream()
            .map(url -> new ServerAddress(url))
            .collect(Collectors.toList())
        );
    }

    public static Morphia getMorphia(MongoClient client) {
        Morphia morphia = new Morphia();
        morphia.mapPackage("com.jivecake.api.model");
        MapperOptions options = morphia.getMapper().getOptions();
        options.setStoreEmpties(true);

        return morphia;
    }

    public static Datastore getDatastore(Morphia morphia, MongoClient client, String name) {
        Datastore datastore = morphia.createDatastore(client, name);
        datastore.ensureIndexes();
        return datastore;
    }
}