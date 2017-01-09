import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.core.Response;

import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jivecake.api.OAuthConfiguration;
import com.jivecake.api.model.Application;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.resources.TransactionResource;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.Auth0Service;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.IndexedOrganizationNodeService;
import com.jivecake.api.service.ItemService;
import com.jivecake.api.service.MappingService;
import com.jivecake.api.service.OrganizationService;
import com.jivecake.api.service.PermissionService;
import com.jivecake.api.service.TransactionService;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;

public class TransactionTransferTest {
    private TransactionService transactionService;
    private EventService eventService;
    private TransactionResource transactionResource;
    private Datastore datastore;
    private MongoClient client;

    @Before
    public void connect() {
        this.client = new MongoClient(Arrays.asList(new ServerAddress(System.getProperty("db"))));
        this.datastore = new Morphia().createDatastore(this.client, "test");

        MappingService mappingService = new MappingService(this.datastore);
        OrganizationService organizationService = new OrganizationService(this.datastore);

        this.transactionService = new TransactionService(this.datastore);
        this.eventService = new EventService(this.datastore);

        this.transactionResource = new TransactionResource(
            new ItemService(this.datastore),
            this.eventService,
            this.transactionService,
            mappingService,
            organizationService,
            new PermissionService(
                this.datastore,
                mappingService,
                new ApplicationService(new Application()),
                organizationService,
                new IndexedOrganizationNodeService(this.datastore, organizationService)
            ),
            new Auth0Service(new OAuthConfiguration(), Arrays.asList()) {
                @Override
                public Future<Response> queryUsers(String query, InvocationCallback<Response> callback) {
                    Response response = Response.ok().entity("[{\"user_id\": \"auth0|0001\"}]").build();
                    callback.completed(response);
                    return new CompletableFuture<Response>();
                }
            }
        );
    }

    @After
    public void disconnect() {
        this.client.dropDatabase("test");
        this.client.close();
    }

    @Test
    public void transactionTransferPersistsInTransactionHeirarchy() {
        Event event = new Event();
        event.id = new ObjectId();
        event.status = this.eventService.getActiveEventStatus();

        Item item = new Item();
        item.id = new ObjectId();
        item.eventId = event.id;

        Transaction transaction = new Transaction();
        transaction.itemId = item.id;
        transaction.user_id = "auth0|0000";
        transaction.status = this.transactionService.getPaymentCompleteStatus();
        transaction.currency = "USD";
        transaction.amount = 1.00;
        transaction.timeCreated = new Date();

        this.datastore.save(
            Arrays.asList(
                event,
                item,
                transaction
            )
        );

        ObjectNode jsonNode = new ObjectMapper().createObjectNode();
        jsonNode.put("sub", "auth0|0000");

        this.transactionResource.transfer(transaction, "auth0|0001", jsonNode, new FakeAsyncResponse() {
            @Override
            public boolean resume(Object object) {
                TransactionService transactionService = TransactionTransferTest.this.transactionService;
                List<List<Transaction>> forest = transactionService.getTransactionForest(Arrays.asList(transaction));

                Transaction transferredTransaction = forest.get(0).get(1);
                Transaction newCompleteTransaction = forest.get(0).get(2);

                assertEquals(transactionService.getTransferredStatus(), transferredTransaction.status);
                assertEquals(transactionService.getPaymentCompleteStatus(), newCompleteTransaction.status);
                assertEquals("auth0|0001", newCompleteTransaction.user_id);

                return false;
            }
        });
    }
}