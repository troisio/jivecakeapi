package com.jivecake.api.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;

import javax.inject.Inject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import org.bson.types.ObjectId;

import com.jivecake.api.StripeConfiguration;
import com.jivecake.api.request.StripeAccountCredentials;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.net.RequestOptions;
import com.stripe.net.RequestOptions.RequestOptionsBuilder;

public class StripeService {
    private final RequestOptions requestOptions;
    private final StripeConfiguration configuration;

    @Inject
    public StripeService(StripeConfiguration configuration) {
        this.configuration = configuration;
        this.requestOptions = new RequestOptionsBuilder()
            .setApiKey(configuration.secretKey)
            .build();
    }

    public RequestOptions getRequestOptions() {
        return this.requestOptions;
    }

    public String getMonthly10PlanId() {
        return "monthly10";
    }

    public Future<StripeAccountCredentials> getAccountCredentials(String code, InvocationCallback<StripeAccountCredentials> callback) {
        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        form.putSingle("grant_type", "authorization_code");
        form.putSingle("code", code);
        form.putSingle("client_secret", this.configuration.secretKey);

        return ClientBuilder.newClient()
            .target("https://connect.stripe.com/oauth/token")
            .request()
            .buildPost(Entity.form(form))
            .submit(callback);
    }

    public List<Subscription> getCurrentSubscriptions(ObjectId organizationId) throws StripeException {
        Map<String, Object> activeSearch = new HashMap<>();
        activeSearch.put("plan", this.getMonthly10PlanId());
        activeSearch.put("status", "active");
        activeSearch.put("limit", "100");

        Map<String, Object> trialSearch = new HashMap<>();
        trialSearch.put("plan", this.getMonthly10PlanId());
        trialSearch.put("status", "trialing");
        trialSearch.put("limit", "100");

        Iterable<Subscription> active = Subscription.list(activeSearch, this.getRequestOptions())
            .autoPagingIterable();
        Iterable<Subscription> trialing = Subscription.list(trialSearch, this.getRequestOptions())
            .autoPagingIterable();

        List<Subscription> result = new ArrayList<>();

        for (Subscription subscription: active) {
            if (Objects.equals(subscription.getMetadata().get("organizationId"), organizationId.toString())) {
                result.add(subscription);
            }
        }

        for (Subscription subscription: trialing) {
            if (Objects.equals(subscription.getMetadata().get("organizationId"), organizationId.toString())) {
                result.add(subscription);
            }
        }

        return result;
    }
}