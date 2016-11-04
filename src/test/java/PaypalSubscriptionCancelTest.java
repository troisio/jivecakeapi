

import org.junit.Test;

public class PaypalSubscriptionCancelTest {
    @Test
    public void paypalCancelIPNRemovesSubscription() {
        String body = "amount3=1.00&address_status=confirmed&subscr_date=21%3A30%3A14+Apr+06%2C+2016+PDT&payer_id=J62KGRNQYR3VU&address_street=123+Fake&mc_amount3=1.00&charset=windows-1252&address_zip=32907&first_name=JiveCake&reattempt=1&address_country_code=US&address_name=JiveCake&notify_version=3.8&subscr_id=I-DHSN440FBJ0P&custom=57047460c9e77c0043f978d0&payer_status=verified&business=luis%40trois.io&address_country=United+States&address_city=Palm+Bay&verify_sign=AGppoqR599PBQOO6w0VVk5QEdPHuADiwDQ9NUFt9wR34mrLYTzzbnQQG&payer_email=SobiborTreblinka%40gmail.com&payer_business_name=JiveCake&btn_id=113215020&last_name=Banegas&address_state=FL&receiver_email=luis%40trois.io&recurring=1&txn_type=subscr_cancel&item_name=JiveCake+Test+Daily+Billing&mc_currency=USD&residence_country=US&period3=1+D&ipn_track_id=3e68d485ee736";
    }

    @Test
    public void paypalEOTIPNHonored() {
        String body = "txn_type=subscr_eot&subscr_id=I-DHSN440FBJ0P&last_name=Banegas&residence_country=US&item_name=JiveCake+Test+Daily+Billing&mc_currency=USD&business=luis%40trois.io&verify_sign=AJMYqZdkaRb22Tv1pzEiGEmDoDu1AXAqDO6fnBz.oMMd-eMuthfzzD9o&payer_status=verified&payer_email=SobiborTreblinka%40gmail.com&first_name=JiveCake&receiver_email=luis%40trois.io&payer_id=J62KGRNQYR3VU&payer_business_name=JiveCake&btn_id=113215020&custom=57047460c9e77c0043f978d0&charset=windows-1252&notify_version=3.8&ipn_track_id=f22c69faecbe5";
    }
}