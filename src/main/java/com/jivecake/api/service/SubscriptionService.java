package com.jivecake.api.service;

import java.util.Calendar;
import java.util.Date;

import javax.inject.Inject;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;

import com.jivecake.api.model.Feature;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.OrganizationFeature;
import com.jivecake.api.model.PaymentDetail;
import com.jivecake.api.model.PaypalIPN;
import com.jivecake.api.model.SubscriptionPaymentDetail;
import com.jivecake.api.resources.PaypalResource;
import com.jivecake.api.serializer.JsonTools;

public class SubscriptionService {
    private final Datastore datastore;
    private final FeatureService featureService;
    private final Logger logger = LogManager.getLogger(PaypalResource.class);
    private final JsonTools jsonTools = new JsonTools();

    @Inject
    public SubscriptionService(
        Datastore datastore,
        FeatureService featureService
    ) {
        this.datastore = datastore;
        this.featureService = featureService;
    }

    public Key<Feature> processSubscription(PaypalIPN ipn) {
        ObjectId custom;

        try {
            custom = new ObjectId(ipn.custom);
        } catch (IllegalArgumentException e) {
            custom = null;
        }

        PaymentDetail paymentDetails = this.datastore.find(PaymentDetail.class)
            .field("custom").equal(custom)
            .get();

        double subscriptionAmount;

        try {
            subscriptionAmount = Double.parseDouble(ipn.mc_gross);
        } catch (NumberFormatException e) {
            subscriptionAmount = -1;
        }

        Key<Feature> result = null;

        if (subscriptionAmount > 0) {
            boolean isMonthlyEvent30 = subscriptionAmount > 29.99 && subscriptionAmount < 30.01;

            if (isMonthlyEvent30) {
                SubscriptionPaymentDetail details = (SubscriptionPaymentDetail)paymentDetails;

                Organization organization = this.datastore.find(Organization.class)
                    .field("id").equal(details.organizationId)
                    .get();

                if (organization == null) {
                    String ipnSerialized = this.jsonTools.pretty(ipn);
                    this.logger.warn(String.format("%n%nSubscriptionIPN: Organization not found for %s%n%n", ipnSerialized));
                } else {
                    OrganizationFeature feature = new OrganizationFeature();
                    feature.organizationId = organization.id;
                    feature.type = this.featureService.getOrganizationEventFeature();
                    feature.timeStart = new Date();

                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(feature.timeStart);
                    calendar.add(Calendar.DATE, 31);

                    feature.timeEnd = calendar.getTime();

                    result = this.featureService.save(feature);
                }
            } else {
                String ipnSerialized = this.jsonTools.pretty(ipn);
                this.logger.warn(String.format("SubscriptionIPN: Subscription id not found for %s", ipnSerialized));
            }
        } else {
            String ipnSerialized = this.jsonTools.pretty(ipn);
            this.logger.warn(String.format("SubscriptionIPN: IPN mc_gross is not parsable to a double%n%n%s", ipnSerialized));
        }

        return result;
    }

    public int getMonthlyEventSubscriptionType() {
        return 0;
    }
}
