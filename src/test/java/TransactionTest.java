import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.util.Date;

import org.bson.types.ObjectId;
import org.junit.Test;

import com.jivecake.api.model.Transaction;

public class TransactionTest {
    @Test
    public void transactionConstructorCopiesAllFields() throws IllegalArgumentException, IllegalAccessException {
        Transaction transaction = new Transaction();
        transaction.id = new ObjectId("58aa4bf324aa9a006b3e818c");
        transaction.parentTransactionId = new ObjectId("28aa4bf324aa9a006b3e818c");
        transaction.itemId = new ObjectId("18aa4bf324aa9a006b3e818c");
        transaction.eventId = new ObjectId("18aa4bf324aa9a006b3e218c");
        transaction.organizationId = new ObjectId("18aa4bf324aa9a006b3c218c");
        transaction.user_id = "user_id";
        transaction.linkedObjectClass = "objectclass";
        transaction.linkedId = "id";
        transaction.status = 1;
        transaction.paymentStatus = 2;
        transaction.quantity = 3;
        transaction.amount = 20.22;
        transaction.given_name = "given name";
        transaction.middleName = "middle name";
        transaction.family_name = "family name";
        transaction.organizationName = "org";
        transaction.currency = "currency";
        transaction.email = "email@email.com";
        transaction.leaf = false;
        transaction.timeCreated = new Date(0);

        Transaction newTransaction = new Transaction(transaction);

        for (Field field: Transaction.class.getFields()) {
            Object newValue = field.get(newTransaction);
            Object originalValue = field.get(transaction);

            assertEquals(newValue, originalValue);
        }
    }
}
