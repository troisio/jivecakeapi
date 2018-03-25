import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;

import com.auth0.json.mgmt.users.User;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.FormField;
import com.jivecake.api.model.FormFieldResponse;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.service.ExcelService;
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

    @Test
    public void canWriteFormResponses() throws IOException {
        Item item = new Item();
        item.id = new ObjectId();

        Event event = new Event();
        event.id = new ObjectId();

        FormField field = new FormField();
        field.id = new ObjectId();
        field.eventId = event.id;
        field.item = item.id;

        FormFieldResponse formFieldResponse = new FormFieldResponse();
        formFieldResponse.id = new ObjectId();
        formFieldResponse.formFieldId = field.id;
        formFieldResponse.longValue = 12L;

        Transaction transaction = new Transaction();
        transaction.id = new ObjectId();
        transaction.formFieldResponseIds = new HashSet<>(Arrays.asList(formFieldResponse.id));

        User user = new User();
        user.setId("auth0|1234");
        user.setGivenName("given name");
        user.setFamilyName("familyName");

        File file = File.createTempFile("excel", ".xlsx");
        file.deleteOnExit();

        ExcelService.writeResponsesToExcel(
            file,
            Arrays.asList(event),
            Arrays.asList(item),
            Arrays.asList(formFieldResponse),
            Arrays.asList(field),
            Arrays.asList(transaction),
            Arrays.asList(user)
        );
    }
}
