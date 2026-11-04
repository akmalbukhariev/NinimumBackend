package com.ninimum.api.message;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SmsCodesTest {
    final List<String> sent = new ArrayList<>();
    final SmsCodes codes = new SmsCodes((phone, login) -> { sent.add(phone + (login ? " login" : " new")); return "4821"; });

    @Test void codeIsCheckedOnTheServerAndUsedOnce() throws Exception {
        codes.send("998901112233", false);
        assertEquals(List.of("+998901112233 new"), sent, "any number form, stored as one");
        assertFalse(codes.check("+998901112233", "1111"));
        assertTrue(codes.check("901112233", "4821"), "the popup check keeps the code");
        assertTrue(codes.consume("+998901112233", " 4821 "));
        assertFalse(codes.consume("+998901112233", "4821"), "used once");
    }
    @Test void fiveWrongTriesEndTheCode() throws Exception {
        codes.send("+998901112233", true);
        for (int i = 0; i < 5; i++) assertFalse(codes.check("+998901112233", "0000"));
        assertFalse(codes.check("+998901112233", "4821"), "no guessing after 5 tries");
    }
    @Test void sendingIsLimited() throws Exception {
        long t = 1_000_000L;
        codes.send("+998901112233", false, t);
        assertEquals("SMS_TOO_SOON", assertThrows(SmsCodes.Limited.class, () -> codes.send("+998901112233", false, t + 30_000)).getMessage());
        for (int i = 1; i < 5; i++) codes.send("+998901112233", false, t + i * 61_000L);
        assertEquals("SMS_DAILY_LIMIT", assertThrows(SmsCodes.Limited.class, () -> codes.send("+998901112233", false, t + 10 * 61_000L)).getMessage());
        codes.send("+998901112244", false, t + 10 * 61_000L);
        assertEquals(6, sent.size(), "another number is not blocked");
        assertThrows(IllegalArgumentException.class, () -> codes.send("deleted-2", false));
    }
}
