package unit;

import javax.ws.rs.core.MultivaluedMap;

import org.junit.Test;

import com.jivecake.api.service.HttpService;
import com.jivecake.api.service.PaypalService;

public class PaypalMulticurrencyTest {
    private final PaypalService paypalService = new PaypalService(null, null, null);

    @Test
    public void multicurrencyPendingIPNSerializes() {
        String body = "mc_gross=1.67&protection_eligibility=Eligible&address_status=confirmed&item_number1=579b9b7224aa9a0046a222ba&tax=0.00&item_number2=57a938fc24aa9a00471fe13e&payer_id=J62KGRNQYR3VU&address_street=2020+Leanne+Ct&payment_date=19%3A03%3A38+Aug+08%2C+2016+PDT&payment_status=Pending&charset=windows-1252&address_zip=32792&mc_shipping=0.00&mc_handling=0.00&first_name=Luis&address_country_code=US&address_name=Luis+Banegas+Saybe&notify_version=3.8&custom=57a939d924aa9a00471fe13f&payer_status=verified&business=luis%40trois.io&address_country=United+States&num_cart_items=2&mc_handling1=0.00&mc_handling2=0.00&address_city=Winter+Park&verify_sign=ATOwmFugiV5w7zYaJ0M7DizFlZzMAI9KXs1X9ONIG09v3RD0e0eVd9OB&payer_email=SobiborTreblinka%40gmail.com&mc_shipping1=0.00&mc_shipping2=0.00&tax1=0.00&tax2=0.00&txn_id=9W2142817D9850249&payment_type=instant&payer_business_name=JiveCake&last_name=Banegas&address_state=FL&item_name1=Saturday+Night+Party&receiver_email=luis%40trois.io&item_name2=Sunday+Night+Dance&quantity1=1&quantity2=1&receiver_id=J6LQ63LX6CYF8&pending_reason=multi_currency&txn_type=cart&mc_gross_1=0.78&mc_currency=EUR&mc_gross_2=0.89&residence_country=US&transaction_subject=&payment_gross=&ipn_track_id=4d41d7256997e";
        MultivaluedMap<String, String> form = new HttpService().bodyToMap(body);
        this.paypalService.create(form);
    }

    @Test
    public void postMulticurrencyIPNSerializes() {
        String body = "mc_gross=1.67&settle_amount=1.37&protection_eligibility=Eligible&address_status=confirmed&item_number1=579b9b7224aa9a0046a222ba&tax=0.00&item_number2=57a938fc24aa9a00471fe13e&payer_id=J62KGRNQYR3VU&address_street=123+Fake&payment_date=19%3A03%3A38+Aug+08%2C+2016+PDT&payment_status=Completed&charset=windows-1252&address_zip=32792&mc_shipping=0.00&mc_handling=0.00&first_name=Luis&mc_fee=0.40&address_country_code=US&exchange_rate=1.07874&address_name=Luis+Banegas+Saybe&notify_version=3.8&settle_currency=USD&custom=57a939d924aa9a00471fe13f&payer_status=verified&address_country=United+States&num_cart_items=2&mc_handling1=0.00&mc_handling2=0.00&address_city=Winter+Park&verify_sign=A3VYEEP1tvQNeoxbfr-eCqx0LcHiA9i2G52rm2VWa5KaBa4Fsy.pY98h&payer_email=SobiborTreblinka%40gmail.com&mc_shipping1=0.00&mc_shipping2=0.00&tax1=0.00&tax2=0.00&txn_id=9W2142817D9850249&payment_type=instant&payer_business_name=JiveCake&last_name=Banegas&address_state=FL&item_name1=Saturday+Night+Party&receiver_email=luis%40trois.io&item_name2=Sunday+Night+Dance&payment_fee=&quantity1=1&quantity2=1&receiver_id=J6LQ63LX6CYF8&txn_type=cart&mc_gross_1=0.78&mc_currency=EUR&mc_gross_2=0.89&residence_country=US&transaction_subject=&payment_gross=&ipn_track_id=af5f856b9dd2f";

        MultivaluedMap<String, String> form = new HttpService().bodyToMap(body);
        this.paypalService.create(form);
    }
}