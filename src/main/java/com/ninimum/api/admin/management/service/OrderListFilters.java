package com.ninimum.api.admin.management.service;

import java.time.*;
import java.util.Set;

/** Calendar days are interpreted in Uzbekistan; database timestamps follow UTC convention. */
public record OrderListFilters(String paymentStatus,String fromUtc,String toUtc) {
    public static OrderListFilters parse(String payment,String from,String to) {
        payment=clean(payment);from=clean(from);to=clean(to);
        if(payment!=null&&!Set.of("PENDING","PAID","FAILED","REFUNDED").contains(payment))
            throw new IllegalArgumentException("Invalid payment status");
        try {
            LocalDate start=from==null?null:LocalDate.parse(from),end=to==null?null:LocalDate.parse(to);
            if(start!=null && end!=null && end.isBefore(start)) throw new IllegalArgumentException("Invalid date range");
            // Keep within the database DATETIME calendar range after UTC conversion.
            if((start!=null&&(start.getYear()<1001||start.getYear()>9998)) || (end!=null&&(end.getYear()<1001||end.getYear()>9998)))
                throw new IllegalArgumentException("Invalid date range");
            return new OrderListFilters(payment,utc(start),utc(end==null?null:end.plusDays(1)));
        } catch(DateTimeException ex) { throw new IllegalArgumentException("Invalid date range",ex); }
    }
    private static String clean(String value) { return value==null||value.isBlank()?null:value.trim(); }
    private static String utc(LocalDate day) {
        return day==null?null:day.atStartOfDay(ZoneId.of("Asia/Tashkent")).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime().toString().replace('T',' ');
    }
}
