package com.jivecake.api.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.threeten.bp.Duration;

import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Cors;
import com.google.cloud.storage.Cors.Origin;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageAnnotatorSettings;
import com.google.cloud.vision.v1.ImageSource;
import com.jivecake.api.APIConfiguration;

public class GoogleCloudPlatformService {
    private final APIConfiguration configuration;

    @Inject
    public GoogleCloudPlatformService(APIConfiguration configuration) {
        this.configuration = configuration;
    }

    public Bucket corsEnableBucket(String bucket) {
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

        Storage storage = StorageOptions.getDefaultInstance().getService();

        BucketInfo info = BucketInfo.newBuilder(bucket)
            .setCors(Arrays.asList(cors))
            .build();

        return storage.update(info);
    }

    public List<AnnotateImageResponse> getAnnotations(Feature.Type featureType, String path) throws IOException {
        List<AnnotateImageRequest> requests = new ArrayList<>();

        ImageAnnotatorSettings.Builder imageAnnotatorSettingsBuilder = ImageAnnotatorSettings.defaultBuilder();
        imageAnnotatorSettingsBuilder.batchAnnotateImagesSettings()
            .getRetrySettingsBuilder()
            .setTotalTimeout(Duration.ofSeconds(30));
        ImageAnnotatorSettings settings = imageAnnotatorSettingsBuilder.build();

        ImageSource imgSource = ImageSource.newBuilder()
            .setGcsImageUri(path)
            .build();
        Image img = Image.newBuilder()
            .setSource(imgSource)
            .build();
        Feature feat = Feature.newBuilder()
            .setType(featureType)
            .build();

        AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
            .addFeatures(feat)
            .setImage(img)
            .build();
        requests.add(request);

        ImageAnnotatorClient client = ImageAnnotatorClient.create(settings);
        BatchAnnotateImagesResponse response = client.batchAnnotateImages(requests);
        return response.getResponsesList();
    }
}
