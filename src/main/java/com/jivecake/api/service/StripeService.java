package com.jivecake.api.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import javax.inject.Inject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import org.bson.types.ObjectId;

import com.jivecake.api.APIConfiguration;
import com.jivecake.api.request.StripeAccountCredentials;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.net.RequestOptions;
import com.stripe.net.RequestOptions.RequestOptionsBuilder;

public class StripeService {
    public static final String MONTHLY_ID = "monthly10";
    public static final String MONTHLY_TRIAL_ID = "monthly10trial";
    private final RequestOptions requestOptions;
    private final APIConfiguration configuration;

    @Inject
    public StripeService(
        APIConfiguration configuration
    ) {
        this.configuration = configuration;
        this.requestOptions = new RequestOptionsBuilder()
            .setApiKey(configuration.stripe.secretKey)
            .build();
    }

    public RequestOptions getRequestOptions() {
        return this.requestOptions;
    }

    public Future<StripeAccountCredentials> getAccountCredentials(String code, InvocationCallback<StripeAccountCredentials> callback) {
        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        form.putSingle("grant_type", "authorization_code");
        form.putSingle("code", code);
        form.putSingle("client_secret", this.configuration.stripe.secretKey);

        return ClientBuilder.newClient()
            .target("https://connect.stripe.com/oauth/token")
            .request()
            .buildPost(Entity.form(form))
            .submit(callback);
    }

    public List<Subscription> getCurrentSubscriptions(ObjectId organizationId) throws StripeException {
        List<Subscription> result = new ArrayList<>();

        List<Iterable<Subscription>> subscriptions = this.getActiveOrTrialingSubscriptions();

        for (Iterable<Subscription> iterable: subscriptions) {
            for (Subscription subscription: iterable) {
                if (organizationId.toString().equals(subscription.getMetadata().get("organizationId"))) {
                    result.add(subscription);
                }
            }
        }

        return result;
    }

    public List<Iterable<Subscription>> getActiveOrTrialingSubscriptions() throws StripeException {
        List<Map<String, Object>> queries = new ArrayList<>();

        Map<String, Object> activeTrialSearch = new HashMap<>();
        activeTrialSearch.put("status", "active");
        activeTrialSearch.put("limit", "100");

        Map<String, Object> trialSearch = new HashMap<>();
        trialSearch.put("status", "trialing");
        trialSearch.put("limit", "100");

        queries.add(activeTrialSearch);
        queries.add(trialSearch);

        List<Iterable<Subscription>> result = new ArrayList<>();

        for (Map<String, Object> query: queries) {
            Iterable<Subscription> subscriptions = Subscription.list(query, this.getRequestOptions())
                .autoPagingIterable();

            result.add(subscriptions);
        }

        return result;
    }
}