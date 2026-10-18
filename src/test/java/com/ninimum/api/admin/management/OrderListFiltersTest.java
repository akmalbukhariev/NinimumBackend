package com.ninimum.api.admin.management;
import com.ninimum.api.admin.management.service.OrderListFilters;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OrderListFiltersTest {
    @Test void localDayUsesInclusiveStartAndExclusiveNextDayInUtc() {
        var f=OrderListFilters.parse(" PAID ","2026-10-16","2026-10-16");
        assertEquals("PAID",f.paymentStatus());assertEquals("2026-10-15 19:00",f.fromUtc());assertEquals("2026-10-16 19:00",f.toUtc());
        assertEquals("2026-12-31 19:00",OrderListFilters.parse(null,null,"2026-12-31").toUtc());
    }
    @Test void emptyAndOneSidedFiltersRemainSupported() {
        assertNull(OrderListFilters.parse(" ","",null).paymentStatus());
        assertNull(OrderListFilters.parse("REFUNDED",null,"2026-10-16").fromUtc());
        assertNull(OrderListFilters.parse(null,"2026-10-16",null).toUtc());
    }
    @Test void rejectsBadPaymentValuesAndDates() {
        assertThrows(IllegalArgumentException.class,()->OrderListFilters.parse("ALL",null,null));
        assertThrows(IllegalArgumentException.class,()->OrderListFilters.parse(null,"2026-02-30",null));
        assertThrows(IllegalArgumentException.class,()->OrderListFilters.parse(null,"2026-10-17","2026-10-16"));
        assertThrows(IllegalArgumentException.class,()->OrderListFilters.parse(null,null,"9999-12-31"));
    }
}
