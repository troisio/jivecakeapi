package com.jivecake.api.service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Cors;
import com.google.cloud.storage.Cors.Origin;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.jivecake.api.APIConfiguration;

public class GoogleCloudPlatformService {
    private final APIConfiguration configuration;

    @Inject
    public GoogleCloudPlatformService(APIConfiguration configuration) {
        this.configuration = configuration;
    }

    public Bucket corsEnableBucket(Storage storage, String bucket) {
        List<Origin> origins = this.configuration.corsOrigins
            .stream()
            .map(origin -> Origin.of(origin))
            .collect(Collectors.toList());

        List<String> headers = Arrays.asList();
        List<HttpMethod> methods = Arrays.asList(HttpMethod.GET);

        Cors cors = Cors.newBuilder()
            .setMaxAgeSeconds(100)
            .setOrigins(origins)
            .setResponseHeaders(headers)
            .setMethods(methods)
            .build();

        BucketInfo info = BucketInfo.newBuilder(bucket)
            .setCors(Arrays.asList(cors))
            .build();

        return storage.update(info);
    }
}
