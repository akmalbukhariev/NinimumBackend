package com.ninimum.api.message.controller;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TempPasswordLimitTest {
    @Test void secondRequestWithinAMinuteIsRefusedPerPhone() {
        var limit = new MessageController.TempPasswordLimit();
        assertTrue(limit.tryAcquire("+998901234567"));
        assertFalse(limit.tryAcquire("+998901234567"));
        assertTrue(limit.tryAcquire("+998907654321"));
    }
}
