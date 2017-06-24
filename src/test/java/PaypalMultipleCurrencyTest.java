

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;

import com.jivecake.api.model.CartPaymentDetails;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.PaypalIPN;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.service.HttpService;
import com.jivecake.api.service.ItemService;
import com.jivecake.api.service.PaypalService;
import com.jivecake.api.service.TransactionService;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;

public class PaypalMultipleCurrencyTest {
    private PaypalService paypalService;
    private TransactionService transactionService;
    private Datastore datastore;
    private MongoClient client;
    private final HttpService httpService = new HttpService();

    @Before
    public void connect() {
        this.client = new MongoClient(Arrays.asList(new ServerAddress(System.getProperty("db"))));
        this.datastore = new Morphia().createDatastore(this.client, "test");

        this.transactionService = new TransactionService(this.datastore);
        this.paypalService = new PaypalService(this.datastore, null, this.transactionService);
    }

    @After
    public void disconnect() {
        this.client.dropDatabase("test");
        this.client.close();
    }

    @Test
    public void multipleCurrencyPendingOverwrittenByCompletedTransaction() {
        String multiCurrencyPendingBody = "mc_gross=1.67&protection_eligibility=Eligible&address_status=confirmed&item_number1=579b9b7224aa9a0046a222ba&tax=0.00&item_number2=57a938fc24aa9a00471fe13e&payer_id=J62KGRNQYR3VU&address_street=123+Fake&payment_date=19%3A03%3A38+Aug+08%2C+2016+PDT&payment_status=Pending&charset=windows-1252&address_zip=32792&mc_shipping=0.00&mc_handling=0.00&first_name=Luis&address_country_code=US&address_name=Luis+Banegas+Saybe&notify_version=3.8&custom=57a939d924aa9a00471fe13f&payer_status=verified&business=luis%40trois.io&address_country=United+States&num_cart_items=2&mc_handling1=0.00&mc_handling2=0.00&address_city=Winter+Park&verify_sign=ATOwmFugiV5w7zYaJ0M7DizFlZzMAI9KXs1X9ONIG09v3RD0e0eVd9OB&payer_email=SobiborTreblinka%40gmail.com&mc_shipping1=0.00&mc_shipping2=0.00&tax1=0.00&tax2=0.00&txn_id=9W2142817D9850249&payment_type=instant&payer_business_name=JiveCake&last_name=Banegas&address_state=FL&item_name1=Saturday+Night+Party&receiver_email=luis%40trois.io&item_name2=Sunday+Night+Dance&quantity1=1&quantity2=1&receiver_id=J6LQ63LX6CYF8&pending_reason=multi_currency&txn_type=cart&mc_gross_1=0.78&mc_currency=EUR&mc_gross_2=0.89&residence_country=US&transaction_subject=&payment_gross=&ipn_track_id=4d41d7256997e";
        String multiCurrencyComplete = "mc_gross=1.67&settle_amount=1.37&protection_eligibility=Eligible&address_status=confirmed&item_number1=579b9b7224aa9a0046a222ba&tax=0.00&item_number2=57a938fc24aa9a00471fe13e&payer_id=J62KGRNQYR3VU&address_street=123+Fake&payment_date=19%3A03%3A38+Aug+08%2C+2016+PDT&payment_status=Completed&charset=windows-1252&address_zip=32792&mc_shipping=0.00&mc_handling=0.00&first_name=Luis&mc_fee=0.40&address_country_code=US&exchange_rate=1.07874&address_name=Luis+Banegas+Saybe&notify_version=3.8&settle_currency=USD&custom=57a939d924aa9a00471fe13f&payer_status=verified&address_country=United+States&num_cart_items=2&mc_handling1=0.00&mc_handling2=0.00&address_city=Winter+Park&verify_sign=A3VYEEP1tvQNeoxbfr-eCqx0LcHiA9i2G52rm2VWa5KaBa4Fsy.pY98h&payer_email=SobiborTreblinka%40gmail.com&mc_shipping1=0.00&mc_shipping2=0.00&tax1=0.00&tax2=0.00&txn_id=9W2142817D9850249&payment_type=instant&payer_business_name=JiveCake&last_name=Banegas&address_state=FL&item_name1=Saturday+Night+Party&receiver_email=luis%40trois.io&item_name2=Sunday+Night+Dance&payment_fee=&quantity1=1&quantity2=1&receiver_id=J6LQ63LX6CYF8&txn_type=cart&mc_gross_1=0.78&mc_currency=EUR&mc_gross_2=0.89&residence_country=US&transaction_subject=&payment_gross=&ipn_track_id=af5f856b9dd2f";

        Organization organization = new Organization();
        organization.id = new ObjectId();

        Event event = new Event();
        event.id = new ObjectId();
        event.organizationId = organization.id;

        CartPaymentDetails detail = new CartPaymentDetails();
        detail.custom = new ObjectId("57a939d924aa9a00471fe13f");
        detail.user_id = "google-oauth2|106718618055680636061";

        Item secondItem = new Item();
        secondItem.id = new ObjectId("57a938fc24aa9a00471fe13e");
        secondItem.eventId = event.id;
        secondItem.name = "Sunday Night Dance";
        secondItem.amount = 0.89;
        secondItem.status = ItemService.STATUS_ACTIVE;
        secondItem.timeCreated = new Date();

        Item firstItem = new Item();
        firstItem.id = new ObjectId("579b9b7224aa9a0046a222ba");
        firstItem.eventId = event.id;
        firstItem.name = "Saturday Night Party";
        firstItem.amount = 0.78;
        firstItem.status = ItemService.STATUS_ACTIVE;
        firstItem.timeCreated = new Date();

        PaypalIPN multiCurrencyPendingIpn = this.paypalService.create(this.httpService.bodyToMap(multiCurrencyPendingBody));
        PaypalIPN multiCurrencyCompleteIpn = this.paypalService.create(this.httpService.bodyToMap(multiCurrencyComplete));

        this.datastore.save(
            Arrays.asList(
                organization,
                event,
                detail,
                firstItem,
                secondItem,
                multiCurrencyPendingIpn,
                multiCurrencyCompleteIpn
            )
        );

        List<Transaction> pendingTransactions = this.paypalService.processTransactions(multiCurrencyPendingIpn);
        this.datastore.save(pendingTransactions);

        List<Transaction> transactions = this.paypalService.processTransactions(multiCurrencyCompleteIpn);
        assertEquals(2, transactions.size());

        for (Transaction transaction: transactions) {
            assertEquals(TransactionService.SETTLED, transaction.status);
            assertEquals(TransactionService.PAYMENT_EQUAL, transaction.paymentStatus);
        }
    }
}
