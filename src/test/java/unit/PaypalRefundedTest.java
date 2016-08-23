package unit;

import static org.junit.Assert.assertNotNull;

import javax.ws.rs.core.MultivaluedMap;

import org.junit.Test;

import com.jivecake.api.model.PaypalIPN;
import com.jivecake.api.service.HttpService;
import com.jivecake.api.service.PaypalService;

public class PaypalRefundedTest {
    private final PaypalService paypalService = new PaypalService(null, null, null);

    @Test
    public void refundIPNBodySerializesToIpn() {
        String body = "mc_gross=-4.00&protection_eligibility=Eligible&item_number1=5753256724aa9a0046f768b5&payer_id=J62KGRNQYR3VU&address_street=123+Fake&payment_date=13%3A01%3A37+Jun+04%2C+2016+PDT&payment_status=Refunded&charset=windows-1252&address_zip=32792&mc_shipping=0.00&mc_handling=0.00&first_name=Luis&mc_fee=-0.12&address_country_code=US&address_name=Luis+Banegas+Saybe&notify_version=3.8&reason_code=refund&custom=575332cc24aa9a0046f768d9&business=BalboaDanceOrlando%40gmail.com&address_country=United+States&mc_handling1=0.00&address_city=Winter+Park&verify_sign=AS0GLEgdXabTtYM-rAOEWXYt2SkzAK3bcVzZ37ygmqmWc6jGAF14NT3J&payer_email=SobiborTreblinka%40gmail.com&mc_shipping1=0.00&tax1=0.00&parent_txn_id=0PG081479X0722836&txn_id=20H97822T06380225&payment_type=instant&payer_business_name=JiveCake&last_name=Banegas&address_state=FL&item_name1=Roseboom+Trio+%40+Orlando+Balboa&receiver_email=BalboaDanceOrlando%40gmail.com&payment_fee=-0.12&quantity1=1&receiver_id=YF4XMM8G7ZHYE&mc_gross_1=4.00&mc_currency=USD&residence_country=US&transaction_subject=575332cc24aa9a0046f768d9&payment_gross=-4.00&ipn_track_id=885fd9b8de776";

        MultivaluedMap<String, String> form = new HttpService().bodyToMap(body);
        PaypalIPN ipn = this.paypalService.create(form);
        assertNotNull(ipn);
    }
}