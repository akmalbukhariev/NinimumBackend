package com.ninimum.api.payment.service;

import com.ninimum.api.dto.payme.PaymeResponse;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Payme shows our error message to the payer, so it goes out as {"ru","uz","en"} with clear text. */
class PaymeMessagesTest {
    @Test void errorsCarryUzbekRussianAndEnglish() {
        var m = (Map<?,?>) PaymeResponse.error(-31008, "Product is out of stock or quantity is insufficient", "account.order_id", 1).getError().getMessage();
        assertEquals(java.util.List.of("ru", "uz", "en"), java.util.List.copyOf(m.keySet()));
        assertTrue(String.valueOf(m.get("uz")).contains("Pul yechilmadi"));
        var sys = (Map<?,?>) PaymeResponse.error(-32400, "System error", null, 1).getError().getMessage();
        assertTrue(String.valueOf(sys.get("uz")).startsWith("To‘lovni tekshirishda"));
        var unknown = (Map<?,?>) PaymeResponse.error(-31099, "Something new", null, 1).getError().getMessage();
        assertEquals("Something new", unknown.get("en"));
    }
}
