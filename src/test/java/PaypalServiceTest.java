import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import org.junit.Test;
import org.mongodb.morphia.Datastore;

import com.jivecake.api.model.PaypalIPN;
import com.jivecake.api.serializer.JsonTools;
import com.jivecake.api.service.PaypalService;

public class PaypalServiceTest {
    private final Datastore datasource = null;
    private final PaypalService paypalService = new PaypalService(datasource, null, null);

    @Test
    public void subscriptionIPNMapsToIPN() throws UnsupportedEncodingException {
        String body = "amount3=1.00&address_status=confirmed&subscr_date=19%3A34%3A53+Apr+05%2C+2016+PDT&payer_id=J62KGRNQYR3VU&address_street=123+Fake&mc_amount3=1.00&charset=windows-1252&address_zip=32907&first_name=Luis&reattempt=1&address_country_code=US&address_name=JiveCake&notify_version=3.8&subscr_id=I-DHSN440FBJ0P&custom=57047460c9e77c0043f978d0&payer_status=verified&business=luis%40trois.io&address_country=United+States&address_city=Palm+Bay&verify_sign=ATK3XFCzAotZz6Ficv-BdS.QSjuAAMuQnj1QajC0aOg6MLsQ4B.XT9kK&payer_email=SobiborTreblinka%40gmail.com&payer_business_name=JiveCake&btn_id=113215020&last_name=Banegas&address_state=FL&receiver_email=luis%40trois.io&recurring=1&txn_type=subscr_signup&item_name=JiveCake+Test+Daily+Billing&mc_currency=USD&residence_country=US&period3=1+D&ipn_track_id=98c6a89a8afb5";
        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();

        for (String keyValue : body.split("&")) {
            String[] parts = keyValue.split("=");

            String key = URLDecoder.decode(parts[0], "UTF-8");
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], "UTF-8") : null;

            form.add(key, value);
        }

        PaypalIPN ipn = this.paypalService.create(form);
        new JsonTools().pretty(ipn);
    }

    @Test
    public void cartIPNMapsToIPN() throws UnsupportedEncodingException {
        String body = "mc_gross=2.24&protection_eligibility=Eligible&address_status=confirmed&item_number1=56f8b2e6c9e77c004614d4a6&tax=0.00&item_number2=56f8b307c9e77c004614d4a7&payer_id=7F6BV9XY5WCNE&address_street=123+Fake&payment_date=14%3A33%3A42+Mar+28%2C+2016+PDT&payment_status=Pending&charset=windows-1252&address_zip=32792&mc_shipping=0.00&mc_handling=0.00&first_name=Luis&address_country_code=US&address_name=Luis+Banegas+Saybe&notify_version=3.8&custom=%7B%7D&payer_status=unverified&business=luis%40trois.io&address_country=United+States&num_cart_items=2&mc_handling1=0.00&mc_handling2=0.00&address_city=Winter+Park&verify_sign=AWu5LXYeGjbN4MnftcnMN965XXlkAlEp6Ip2YzXnNcdwFbPEku367GyJ&payer_email=SobiborTreblinka%40gmail.com&mc_shipping1=0.00&mc_shipping2=0.00&tax1=0.00&tax2=0.00&txn_id=3X04713683898750N&payment_type=instant&last_name=Banegas+Saybe&address_state=FL&item_name1=Friday+Night+with+Artie+Shaw&receiver_email=luis%40trois.io&item_name2=Saturday+Night+with+Django+Reinhardt&quantity1=1&quantity2=1&receiver_id=J6LQ63LX6CYF8&pending_reason=multi_currency&txn_type=cart&mc_gross_1=1.99&mc_currency=EUR&mc_gross_2=0.25&residence_country=US&receipt_id=3182-9931-5036-3737&transaction_subject=&payment_gross=&ipn_track_id=82524d84a53f6";
        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();

        for (String keyValue : body.split("&")) {
            String[] parts = keyValue.split("=");

            String key = URLDecoder.decode(parts[0], "UTF-8");
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], "UTF-8") : null;

            form.add(key, value);
        }

        this.paypalService.create(form);
    }

    @Test
    public void ipnMapsToIPN() throws UnsupportedEncodingException {
        String body = "residence_country=US&invoice=abc1234&address_city=San+Jose&first_name=John&payer_id=TESTBUYERID01&shipping=3.04&mc_fee=0.44&txn_id=811391525&receiver_email=seller%40paypalsandbox.com&quantity=1&custom=xyz123&payment_date=23%3A38%3A36+30+Oct+2015+PDT&address_country_code=US&address_zip=95131&tax=2.02&item_name=something&address_name=John+Smith&last_name=Smith&receiver_id=seller%40paypalsandbox.com&item_number=AK-1234&verify_sign=AOvNtY9Qhn.s7o4HtFgbygijGUKpAi4.TGmRDpLNpDgXEPnHYOaZ6sEu&address_country=United+States&payment_status=Completed&address_status=confirmed&business=seller%40paypalsandbox.com&payer_email=buyer%40paypalsandbox.com&notify_version=2.1&txn_type=web_accept&test_ipn=1&payer_status=verified&mc_currency=USD&mc_gross=12.34&address_state=CA&mc_gross1=12.34&payment_type=echeck&address_street=123%2C+any+street";
        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();

        for (String keyValue : body.split("&")) {
            String[] parts = keyValue.split("=");

            String key = URLDecoder.decode(parts[0], "UTF-8");
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], "UTF-8") : null;

            form.add(key, value);
        }

        this.paypalService.create(form);
    }
}