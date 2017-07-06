import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.service.TransactionService;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;

public class ExcelTest {
    private TransactionService transactionService;
    private Datastore datastore;
    private MongoClient client;

    @Before
    public void connect() {
        this.client = new MongoClient(Arrays.asList(new ServerAddress(System.getProperty("db"))));
        this.datastore = new Morphia().createDatastore(this.client, "test");

        this.transactionService = new TransactionService(this.datastore);
    }

    @After
    public void disconnect() {
        this.client.dropDatabase("test");
        this.client.close();
    }

    @Test
    public void excelWriteDoesNotFailWhenTransacitonDoesNotHaveItem() throws IOException, InvalidFormatException {
        File file = File.createTempFile("excel", ".xlsx");
        file.deleteOnExit();

        Transaction transaction = new Transaction();
        this.datastore.save(transaction);

        this.transactionService.writeToExcel(Arrays.asList(transaction), new ArrayList<>(),file);
    }

    @Test
    public void excelWriteDoesNotFailWhenTransactionHasUser() throws IOException, InvalidFormatException {
        File file = File.createTempFile("excel", ".xlsx");
        file.deleteOnExit();

        Transaction transaction = new Transaction();
        transaction.user_id = "identity-provider|105223432348009656993";

        this.datastore.save(transaction);

        ObjectNode node = new ObjectMapper().createObjectNode()
            .put("user_id", "identity-provider|105223432348009656993")
            .put("family_name", "family")
            .put("given_name", "given");

        this.transactionService.writeToExcel(Arrays.asList(transaction), Arrays.asList(node), file);
    }

    @Test
    public void excelWriteDoesNotFail() throws IOException, InvalidFormatException {
        File file = File.createTempFile("excel", ".xlsx");
        file.deleteOnExit();

        Item item = new Item();
        item.name = "name";
        item.id = new ObjectId();

        Transaction transaction = new Transaction();
        transaction.id = new ObjectId();
        transaction.itemId = item.id;
        transaction.timeCreated = new Date();
        transaction.user_id = "identity-provider|105223432348009656993";
        transaction.given_name = "Luis";
        transaction.middleName = "Edgardo";
        transaction.family_name = "Johnson";
        transaction.email = "email";
        transaction.currency = "USD";
        transaction.amount = 20.22;

        this.datastore.save(Arrays.asList(item, transaction));

        this.transactionService.writeToExcel(Arrays.asList(transaction), new ArrayList<>(),file);
    }
}
