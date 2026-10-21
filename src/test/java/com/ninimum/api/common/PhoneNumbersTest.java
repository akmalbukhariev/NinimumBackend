package com.ninimum.api.common;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PhoneNumbersTest {
    @Test void everyUzbekFormBecomesE164() {
        for(String form:List.of("992064790","998992064790","+998992064790","+998 99 206-47-90","(99) 206 47 90","00998992064790"," 998992064790 "))
            assertEquals("+998992064790",PhoneNumbers.normalize(form),form);
    }
    @Test void foreignNumbersKeepTheirCountryCode() {
        assertEquals("+79161234567",PhoneNumbers.normalize("+7 916 123-45-67"));
    }
    @Test void invalidValuesAreRejected() {
        for(String form:new String[]{null,"","abc","12345","+99899206479","99899206479012","merged-12","90123456"})
            assertNull(PhoneNumbers.normalize(form),String.valueOf(form));
    }
    @Test void variantsCoverEveryStoredFormWithE164First() {
        assertEquals(List.of("+998992064790","998992064790","992064790","+998 99 206 47 90"),PhoneNumbers.variants("+998 99 206 47 90"));
        assertEquals(List.of("+998992064790","998992064790","992064790"),PhoneNumbers.variants("992064790"));
        assertEquals(List.of("odd"),PhoneNumbers.variants("odd"));
        assertTrue(PhoneNumbers.variants(" ").isEmpty());
    }
    @Test void smsFormHasNoPlus() {
        assertEquals("998992064790",PhoneNumbers.sms("+998992064790"));
        assertEquals("998992064790",PhoneNumbers.sms("992064790"));
    }
}
