import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

        List<Transaction> transactions = new ArrayList<>();

        for (int index = 0; index < 5; index++) {
            Transaction transaction = new Transaction();
            transaction.linkedId = rootIpn.id;
            transaction.linkedObjectClass = PaypalIPN.class.getSimpleName();

            transactions.add(transaction);
        }

        this.datastore.save(
            item,
            detail,
            rootIpn,
            refundIpn
        );

        this.datastore.save(transactions);

        TransactionService itemTransactionService = new TransactionService(this.datastore);
        PaypalService paypalService = new PaypalService(datastore, null, itemTransactionService);

        paypalService.processRefund(refundIpn);

        List<List<Transaction>> forest = itemTransactionService.getTransactionForest(transactions);

        forest.forEach((lineage) -> {
            Transaction refundTransaction = lineage.get(lineage.size() - 1);

            assertEquals(lineage.size(), 2);
            assertEquals(itemTransactionService.getRefundedStatus(), refundTransaction.status);
        });
    }
}