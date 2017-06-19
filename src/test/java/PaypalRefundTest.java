import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;

import com.jivecake.api.model.CartPaymentDetails;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.PaymentDetail;
import com.jivecake.api.model.PaypalIPN;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.service.PaypalService;
import com.jivecake.api.service.TransactionService;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;

public class PaypalRefundTest {
    private Datastore datastore;
    private MongoClient client;

    @Before
    public void connect() {
        this.client = new MongoClient(Arrays.asList(new ServerAddress(System.getProperty("db"))));
        this.datastore = new Morphia().createDatastore(client, "test");
    }

    @After
    public void disconnect() {
        this.client.dropDatabase("test");
        this.client.close();
    }

    @Test
    public void paypalIPNRefundAddRefundedChildTransactions() {
        Item item = new Item();
        item.id = new ObjectId();

        PaymentDetail detail = new CartPaymentDetails();
        detail.custom = new ObjectId();

        PaypalIPN rootIpn = new PaypalIPN();
        rootIpn.id = new ObjectId();
        rootIpn.custom = detail.custom.toString();
        rootIpn.txn_id = "1";
        rootIpn.item_number = item.id.toString();

        PaypalIPN refundIpn = new PaypalIPN();
        refundIpn.parent_txn_id = rootIpn.txn_id;
        refundIpn.mc_gross = "-0.99";

        Transaction transaction = new Transaction();
        transaction.linkedId = rootIpn.id;
        transaction.linkedObjectClass = PaypalIPN.class.getSimpleName();

        this.datastore.save(
            Arrays.asList(
                item,
                detail,
                rootIpn,
                refundIpn,
                transaction
            )
        );

        TransactionService transactionService = new TransactionService(this.datastore);
        PaypalService paypalService = new PaypalService(datastore, null, transactionService);

        this.datastore.save(
            paypalService.processRefund(refundIpn)
        );

        long count = this.datastore.createQuery(Transaction.class).count();
        assertEquals(2, count);
    }
}