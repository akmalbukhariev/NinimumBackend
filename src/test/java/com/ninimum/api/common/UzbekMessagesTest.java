package com.ninimum.api.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Error sentences reach the apps in Uzbek; codes, success messages and Uzbek texts pass unchanged. */
class UzbekMessagesTest {
    ResponseResult r(String code, String msg) { var r = new ResponseResult(); r.setResultCode(code); r.setResultMsg(msg); return r; }
    @Test void englishErrorsBecomeUzbek() {
        assertEquals("Buyurtma topilmadi.", r("101", "Order not found").getResultMsg());
        assertEquals("Bu amal uchun ruxsatingiz yo‘q.", r("403", "Owner only").getResultMsg());
        assertEquals("Kirish ma’lumotlarini tekshirib bo‘lmadi. Qaytadan kiring.", r("300", Result.AUTHENTICATION_ERROR.getMessage()).getResultMsg());
        assertEquals("Sana noto‘g‘ri.", r("101", "Invalid timestamp: 12x").getResultMsg());
    }
    @Test void codesSuccessAndUzbekStay() {
        assertEquals("WAREHOUSE_INVALID_INPUT", r("WAREHOUSE_INVALID_INPUT", "WAREHOUSE_INVALID_INPUT").getResultMsg());
        assertEquals("Success.", r("100", "Success.").getResultMsg());
        assertEquals("Uzr, bu mahsulot qolmagan.", r("101", "Uzr, bu mahsulot qolmagan.").getResultMsg());
        assertNull(r("101", null).getResultMsg());
    }
}
