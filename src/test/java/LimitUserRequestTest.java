import static org.junit.Assert.assertEquals;

import java.util.Date;
import java.util.List;

import org.junit.Test;

import com.jivecake.api.filter.HashDateCount;

public class LimitUserRequestTest {
    @Test
    public void sliceReturnsPartialListWhenExceedsSize() {
        HashDateCount count = new HashDateCount();

        for (int index = 0; index < 5; index++) {
            count.add("id", new Date());
        }

        List<Date> dates = count.last("id", 20);

        assertEquals(5, dates.size());
    }

    @Test
    public void sliceReturnsPartialListWhenUnderSize() {
        HashDateCount count = new HashDateCount();

        for (int index = 0; index < 5; index++) {
            count.add("id", new Date());
        }

        List<Date> dates = count.last("id", 3);

        assertEquals(3, dates.size());
    }
}