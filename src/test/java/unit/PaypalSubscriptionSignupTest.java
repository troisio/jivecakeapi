package unit;

import org.junit.Test;

public class PaypalSubscriptionSignupTest {
    @Test
    public void paypalSubscriptionSignupIpnDoesNotFail() {
        String body = "amount3=1.00&address_status=confirmed&subscr_date=19%3A52%3A48+Apr+14%2C+2016+PDT&payer_id=J62KGRNQYR3VU&address_street=123+Fake&mc_amount3=1.00&charset=windows-1252&address_zip=32907&first_name=Luis&reattempt=1&address_country_code=US&address_name=JiveCake&notify_version=3.8&subscr_id=I-AGK5XHASMVX8&custom=5710574ba7b11b001f12fb19&payer_status=verified&business=luis%40trois.io&address_country=United+States&address_city=Palm+Bay&verify_sign=A0.lgvS9LTcPk8eFM4yko.lXlTZxAVW7tP7Aqt.2qaGUWgYHvvoCM3Qt&payer_email=SobiborTreblinka%40gmail.com&payer_business_name=JiveCake&btn_id=113215020&last_name=Banegas&address_state=FL&receiver_email=luis%40trois.io&recurring=1&txn_type=subscr_signup&item_name=JiveCake+Test+Daily+Billing&mc_currency=USD&item_number=jivecakesubscriptiondailytest&residence_country=US&period3=1+D&ipn_track_id=df33e24c1970d";
    }
}
