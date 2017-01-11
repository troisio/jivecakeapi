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
    private final double monthlyAmount = 30.00;
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
        Key<Feature> result = null;

        ObjectId custom;

        try {
            custom = new ObjectId(ipn.custom);
        } catch (IllegalArgumentException e) {
            custom = null;
        }

        PaymentDetail paymentDetails = this.datastore.find(PaymentDetail.class)
            .field("custom").equal(custom)
            .get();

        String amount;

        if ("subscr_signup".equals(ipn.txn_type)) {
            amount = ipn.amount3;
        } else if ("subscr_payment".equals(ipn.txn_type)) {
            amount = ipn.mc_gross;
        } else {
            amount = null;
        }

        if (amount != null) {
            NumberFormatException exception;
            double subscriptionAmount;

            try {
                subscriptionAmount = Double.parseDouble(amount);
                exception = null;
            } catch (NumberFormatException e) {
                exception = e;
                subscriptionAmount = 0;
            }

            if (exception == null) {
                boolean isMonthlyEvent30 = Math.abs(this.monthlyAmount - subscriptionAmount) < 0.01;

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
            }
        }

        return result;
    }

    public int getMonthlyEventSubscriptionType() {
        return 0;
    }
}
