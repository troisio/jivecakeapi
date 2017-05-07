import java.util.Arrays;

import javax.ws.rs.core.MultivaluedMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;

import com.jivecake.api.service.HttpService;
import com.jivecake.api.service.PaypalService;
import com.jivecake.api.service.TransactionService;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;

public class PaypalSerializationTest {
    private PaypalService paypalService;
    private Datastore datastore;
    private MongoClient client;

    @Before
    public void connect() {
        this.client = new MongoClient(Arrays.asList(new ServerAddress(System.getProperty("db"))));
        this.datastore = new Morphia().createDatastore(this.client, "test");

        TransactionService transactionService = new TransactionService(this.datastore);
        this.paypalService = new PaypalService(this.datastore, null, transactionService);
    }

    @After
    public void disconnect() {
        this.client.dropDatabase("test");
        this.client.close();
    }

    @Test
    public void ipnSerializationDoesNotFail() {
        String ipn = "mc_gross=-8.00&protection_eligibility=Eligible&item_number1=58e8748624aa9a0051cdb4b3&payer_id=4J92BUAANWKBJ&address_street=11726+College+Park+Tr%0D%0AApt+12-D&payment_date=14%3A13%3A18+May+02%2C+2017+PDT&payment_status=Refunded&charset=windows-1252&address_zip=32826&mc_shipping=0.00&mc_handling=0.00&first_name=Allison+May&mc_fee=-0.23&address_country_code=US&address_name=Allison+May+Cruz&notify_version=3.8&reason_code=refund&echeck_time_processed=14%3A13%3A18+May+02%2C+2017+PDT&custom=58ecf9f024aa9a0051cdb4c5&business=BalboaDanceOrlando%40gmail.com&address_country=United+States&mc_handling1=0.00&address_city=Orlando&verify_sign=AuRlNZvMOhdn8iDWY5YoMB9iRTDzAajpTo.VTy.msX.g2DZUo6jTmDNl&payer_email=alliemaycruz%40aol.com&mc_shipping1=0.00&tax1=0.00&parent_txn_id=8DU692523R5951455&txn_id=874520514T712964Y&payment_type=echeck&last_name=Cruz&address_state=FL&item_name1=Dance+Pass&receiver_email=BalboaDanceOrlando%40gmail.com&payment_fee=-0.23&shipping_discount=0.00&quantity1=1&insurance_amount=0.00&receiver_id=YF4XMM8G7ZHYE&discount=0.00&mc_gross_1=13.00&mc_currency=USD&residence_country=US&shipping_method=Default&transaction_subject=&payment_gross=-8.00&ipn_track_id=e92ed2bf4fa94";
        MultivaluedMap<String, String> map =  new HttpService().bodyToMap(ipn);
        this.paypalService.create(map);
    }
}
