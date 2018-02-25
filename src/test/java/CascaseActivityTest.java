import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Date;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;

import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.service.EntityService;

public class CascaseActivityTest extends DatastoreTest {
    private EntityService entityService;

    @Before
    public void before() {
        this.entityService = new EntityService(super.datastore);
    }

    @Test
    public void cascaseActivityWritesTransactionToParents() {
        Date currentTime = new Date();

        Transaction transaction = new Transaction();
        transaction.id = new ObjectId();
        transaction.itemId = new ObjectId();
        transaction.eventId = new ObjectId();
        transaction.organizationId = new ObjectId();

        Item item = new Item();
        item.id = transaction.itemId;

        Event event = new Event();
        event.id = transaction.eventId;

        Organization organization = new Organization();
        organization.id = transaction.organizationId;

        this.datastore.save(Arrays.asList(transaction, item, event, organization));

        this.entityService.cascadeLastActivity(Arrays.asList(transaction), currentTime);

        Organization searchedOrganization = this.datastore.get(Organization.class, transaction.organizationId);
        assertNotNull(searchedOrganization.lastActivity);

        Event searchedEvent = this.datastore.get(Event.class, transaction.eventId);
        assertNotNull(searchedEvent.lastActivity);

        Item searchedItem = this.datastore.get(Item.class, transaction.itemId);
        assertNotNull(searchedItem.lastActivity);
    }
}
