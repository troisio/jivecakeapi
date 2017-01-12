import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.Morphia;

import com.jivecake.api.model.Feature;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.OrganizationFeature;
import com.jivecake.api.model.PaypalIPN;
import com.jivecake.api.model.SubscriptionPaymentDetail;
import com.jivecake.api.serializer.JsonTools;
import com.jivecake.api.service.FeatureService;
import com.jivecake.api.service.HttpService;
import com.jivecake.api.service.PaypalService;
import com.jivecake.api.service.SubscriptionService;
import com.jivecake.api.service.TransactionService;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;

public class SubscriptionIPNTest {
    private FeatureService featureService;
    private PaypalService paypalService;
    private SubscriptionService subscriptionService;
    private TransactionService transactionService;
    private Datastore datastore;
    private MongoClient client;
    private final HttpService httpService = new HttpService();

    @Before
    public void connect() {
        this.client = new MongoClient(Arrays.asList(new ServerAddress(System.getProperty("db"))));
        this.datastore = new Morphia().createDatastore(this.client, "test");

        this.featureService = new FeatureService(this.datastore);
        this.subscriptionService = new SubscriptionService(this.datastore, this.featureService);
        this.transactionService = new TransactionService(this.datastore);
        this.paypalService = new PaypalService(this.datastore, null, this.transactionService);
    }

    @After
    public void disconnect() {
        this.client.dropDatabase("test");
        this.client.close();
    }

    @Test
    public void subscriptionIpnWritesOrganizationFeature() {
        Organization organization = new Organization();
        organization.id = new ObjectId();

        SubscriptionPaymentDetail detail = new SubscriptionPaymentDetail();
        detail.custom = new ObjectId();
        detail.organizationId = organization.id;
        detail.timeCreated = new Date();

        String body = "mc_gross=30.00&protection_eligibility=Eligible&address_status=confirmed&payer_id=J62KGRNQYR3VU&address_street=123+Fake&payment_date=19%3A52%3A50+Apr+14%2C+2016+PDT&payment_status=Completed&charset=windows-1252&address_zip=32907&first_name=Luis&mc_fee=0.33&address_country_code=US&address_name=JiveCake&notify_version=3.8&subscr_id=I-AGK5XHASMVX8&payer_status=verified&business=luis%40trois.io&address_country=United+States&address_city=Palm+Bay&verify_sign=AVMnB4GuhH0hVL2g-AdOMvDUcA9zAUg7kdpqptp8Gk6j4BYE8RAVBX54&payer_email=SobiborTreblinka%40gmail.com&txn_id=1GK360366M840293X&payment_type=instant&payer_business_name=JiveCake&btn_id=113215020&last_name=Banegas&address_state=FL&receiver_email=luis%40trois.io&payment_fee=0.33&receiver_id=J6LQ63LX6CYF8&txn_type=subscr_payment&item_name=JiveCake+Test+Daily+Billing&mc_currency=USD&item_number=jivecakesubscriptiondailytest&residence_country=US&transaction_subject=JiveCake+Test+Daily+Billing&payment_gross=1.00&ipn_track_id=df33e24c1970d";
        PaypalIPN ipn = this.paypalService.create(this.httpService.bodyToMap(body));
        ipn.custom = detail.custom.toString();

        this.datastore.save(
            Arrays.asList(
                organization,
                detail,
                ipn
            )
        );

        Key<Feature> key = this.subscriptionService.processSubscription(ipn);

        Calendar timeStart = Calendar.getInstance();
        timeStart.add(Calendar.SECOND, -5);

        Calendar endTime = Calendar.getInstance();
        endTime.add(Calendar.DATE, 31);
        endTime.add(Calendar.SECOND, 5);

        List<OrganizationFeature> features = this.datastore.find(OrganizationFeature.class)
             .field("id").equal(key.getId())
            .field("organizationId").equal(organization.id)
            .field("type").equal(this.featureService.getOrganizationEventFeature())
            .field("timeStart").greaterThan(timeStart.getTime())
            .field("timeEnd").lessThan(endTime.getTime())
            .asList();

        assertEquals(1, features.size());
    }

    @Test
    public void paypalSubscriptionHonoredWhenInTrialIPN() {
        Organization organization = new Organization();
        organization.id = new ObjectId();

        SubscriptionPaymentDetail detail = new SubscriptionPaymentDetail();
        detail.custom = new ObjectId();
        detail.organizationId = organization.id;
        detail.timeCreated = new Date();

        String body = "txn_type=subscr_signup&subscr_id=I-L8NB5HF54FT3&last_name=last&residence_country=US&mc_currency=USD&item_name=Monthly+Event+Subscription&amount1=0.00&business=luis%40trois.io&amount3=30.00&recurring=1&verify_sign=AASzyL5CrabrvXMpM6GA4eeXaiKXAXWaZbNy73a08N4bAshE35-vAJDO&payer_status=verified&payer_email=email%40gmail.com&first_name=David&receiver_email=luis%40trois.io&payer_id=4FK8LGP9AUH22&reattempt=1&item_number=monthlyevent30&subscr_date=18%3A57%3A42+Jan+11%2C+2017+PST&btn_id=118211211&charset=windows-1252&notify_version=3.8&period1=1+M&mc_amount1=0.00&period3=1+M&mc_amount3=30.00&ipn_track_id=66fa47fc606fb";
        PaypalIPN ipn = this.paypalService.create(this.httpService.bodyToMap(body));
        ipn.custom = detail.custom.toString();
System.out.println(new JsonTools().pretty(ipn));
        this.datastore.save(
            Arrays.asList(
                organization,
                detail,
                ipn
            )
        );

        Key<Feature> key = this.subscriptionService.processSubscription(ipn);

        Calendar timeStart = Calendar.getInstance();
        timeStart.add(Calendar.SECOND, -5);

        Calendar endTime = Calendar.getInstance();
        endTime.add(Calendar.DATE, 31);
        endTime.add(Calendar.SECOND, 5);

        List<OrganizationFeature> features = this.datastore.find(OrganizationFeature.class)
             .field("id").equal(key.getId())
            .field("organizationId").equal(organization.id)
            .field("type").equal(this.featureService.getOrganizationEventFeature())
            .field("timeStart").greaterThan(timeStart.getTime())
            .field("timeEnd").lessThan(endTime.getTime())
            .asList();

        assertEquals(1, features.size());
    }

    @Test
    public void paypalSubscriptionSignupIpnDoesNotFail() {
        String body = "amount3=1.00&address_status=confirmed&subscr_date=19%3A52%3A48+Apr+14%2C+2016+PDT&payer_id=J62KGRNQYR3VU&address_street=123+Fake&mc_amount3=1.00&charset=windows-1252&address_zip=32907&first_name=Luis&reattempt=1&address_country_code=US&address_name=JiveCake&notify_version=3.8&subscr_id=I-AGK5XHASMVX8&custom=5710574ba7b11b001f12fb19&payer_status=verified&business=luis%40trois.io&address_country=United+States&address_city=Palm+Bay&verify_sign=A0.lgvS9LTcPk8eFM4yko.lXlTZxAVW7tP7Aqt.2qaGUWgYHvvoCM3Qt&payer_email=SobiborTreblinka%40gmail.com&payer_business_name=JiveCake&btn_id=113215020&last_name=Banegas&address_state=FL&receiver_email=luis%40trois.io&recurring=1&txn_type=subscr_signup&item_name=JiveCake+Test+Daily+Billing&mc_currency=USD&item_number=jivecakesubscriptiondailytest&residence_country=US&period3=1+D&ipn_track_id=df33e24c1970d";
        this.paypalService.create(this.httpService.bodyToMap(body));
    }
}