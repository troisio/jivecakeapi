package com.jivecake.api.service;

import java.util.Date;

import javax.inject.Inject;

import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;

import com.jivecake.api.model.Feature;
import com.mongodb.WriteResult;

public class FeatureService {
    private final Datastore datastore;

    @Inject
    public FeatureService(Datastore datastore) {
        this.datastore = datastore;
    }

    public Query<Feature> getCurrentFeaturesQuery(Date date) {
        Query<Feature> query = this.query();
        query.and(
            query.or(
                query.criteria("timeStart").lessThan(date),
                query.criteria("timeStart").doesNotExist()
             ),
             query.or(
                 query.criteria("timeEnd").greaterThan(date),
                 query.criteria("timeEnd").doesNotExist()
              )
         );

        return query;
    }

    public WriteResult delete(Feature feature) {
        return this.datastore.delete(feature);
    }

    public Query<Feature> query() {
        return this.datastore.createQuery(Feature.class);
    }

    public Key<Feature> save(Feature feature) {
        return this.datastore.save(feature);
    }

    public int getOrganizationEventFeature() {
        return 0;
    }
}