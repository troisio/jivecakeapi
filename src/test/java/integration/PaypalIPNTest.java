package integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.junit.Test;
import org.mongodb.morphia.Datastore;

import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.PaypalService;

public class PaypalIPNTest {
    private final Datastore datasource = null;
    private final NotificationService notificationService = null;
    private final PaypalService paypalService = new PaypalService(datasource, notificationService, null);

    @Test
    public void sandboxIPNReturnsVerified() throws InterruptedException, ExecutionException, UnsupportedEncodingException {
        String body = "residence_country=US&invoice=abc1234&address_city=San+Jose&first_name=John&payer_id=TESTBUYERID01&shipping=3.04&mc_fee=0.44&txn_id=811391525&receiver_email=seller%40paypalsandbox.com&quantity=1&custom=xyz123&payment_date=23%3A38%3A36+30+Oct+2015+PDT&address_country_code=US&address_zip=95131&tax=2.02&item_name=something&address_name=John+Smith&last_name=Smith&receiver_id=seller%40paypalsandbox.com&item_number=AK-1234&verify_sign=AOvNtY9Qhn.s7o4HtFgbygijGUKpAi4.TGmRDpLNpDgXEPnHYOaZ6sEu&address_country=United+States&payment_status=Completed&address_status=confirmed&business=seller%40paypalsandbox.com&payer_email=buyer%40paypalsandbox.com&notify_version=2.1&txn_type=web_accept&test_ipn=1&payer_status=verified&mc_currency=USD&mc_gross=12.34&address_state=CA&mc_gross1=12.34&payment_type=echeck&address_street=123%2C+any+street";
        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();

        for (String keyValue : body.split("&")) {
            String[] parts = keyValue.split("=");
            String key = URLDecoder.decode(parts[0], "UTF-8");
            String value = URLDecoder.decode(parts[1], "UTF-8");

            form.add(key, value);
        }

        Future<Response> future = this.paypalService.isValidIPN(form, this.paypalService.getSandboxURL(), new InvocationCallback<Response>(){
            @Override
            public void completed(Response response) {
            }

            @Override
            public void failed(Throwable throwable) {
                fail();
            }
        });

        Response response = future.get();
        String entity = response.readEntity(String.class);
        assertEquals(this.paypalService.getVerified(), entity);
    }
}