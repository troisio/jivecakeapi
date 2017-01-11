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

        String body = "amount1=0.00&amount3=30.00&address_status=confirmed&subscr_date=07%3A02%3A12+Jan+11%2C+2017+PST&payer_id=4FK8LGP9AUH22&address_street=2020+Leanne+Ct&mc_amount1=0.00&mc_amount3=0.01&charset=windows-1252&address_zip=32792&first_name=David&reattempt=1&address_country_code=US&address_name=David+Chau&notify_version=3.8&subscr_id=I-M79VE7GS3UDH&payer_status=verified&business=luis%40trois.io&address_country=United+States&address_city=Winterpark&verify_sign=AZWo7I4TGPfXBH7LpR-EDFL3.aiWAjRfkQre4rdUseNFabpcvjo0ZXTv&payer_email=chaudavid.dc%40gmail.com&btn_id=128724092&last_name=Chau&address_state=FL&receiver_email=luis%40trois.io&recurring=1&txn_type=subscr_signup&item_name=Test+Monthly+Billing&mc_currency=USD&residence_country=US&period1=1+M&period3=1+M&ipn_track_id=2f49485c5b4b7";
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