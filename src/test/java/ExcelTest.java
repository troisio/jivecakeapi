import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;

import com.auth0.json.mgmt.users.User;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.service.TransactionService;

public class ExcelTest extends DatastoreTest {
    private TransactionService transactionService;

    @Before
    public void before() {
        this.transactionService = new TransactionService(super.datastore);
    }

    @Test
    public void excelWriteDoesNotFailWhenTransacitonDoesNotHaveItem() throws IOException, InvalidFormatException {
        File file = File.createTempFile("excel", ".xlsx");
        file.deleteOnExit();

        Transaction transaction = new Transaction();
        this.datastore.save(transaction);

        Event event = new Event();
        event.userData = new ArrayList<>();

        List<User> users = new ArrayList<>();

        this.transactionService.writeToExcel(
            event,
            users,
            Arrays.asList(transaction),
            file
        );
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

        Event event = new Event();
        event.userData = new ArrayList<>();

        List<User> users = new ArrayList<>();

        this.transactionService.writeToExcel(event, users, Arrays.asList(transaction), file);
    }
}
