package com.ninimum.api.ordersafety;

import java.time.*;
import java.util.Map;

/** Operational timers count open hours in Tashkent, including overnight shifts. */
public final class WorkingHours {
    static final ZoneId ZONE=ZoneId.of("Asia/Tashkent");
    private final int days;
    private final int start;
    private final int end;
    public WorkingHours(Map<String,Object> settings) {
        days=((Number)settings.get("working_days")).intValue();
        start=parse(String.valueOf(settings.get("working_start")),false);
        end=parse(String.valueOf(settings.get("working_end")),true);
        if(days<1 || days>127 || start==end) throw new IllegalArgumentException("Invalid working schedule");
    }
    static int parse(String text,boolean end) {
        if(end && "24:00".equals(text)) return 1440;
        if(!text.matches("[0-2][0-9]:[0-5][0-9]")) throw new IllegalArgumentException("Invalid working time");
        try {var time=LocalTime.parse(text);return time.getHour()*60+time.getMinute();}
        catch(java.time.format.DateTimeParseException ex) {throw new IllegalArgumentException("Invalid working time");}
    }
    public long minutes(Instant from,Instant to) {
        if(!from.isBefore(to)) return 0;
        long seconds=0;
        LocalDate first=from.atZone(ZONE).toLocalDate().minusDays(1);
        LocalDate last=to.atZone(ZONE).toLocalDate();
        for(LocalDate day=first;!day.isAfter(last);day=day.plusDays(1)) {
            if((days & (1<<(day.getDayOfWeek().getValue()-1)))==0) continue;
            Instant open=day.atStartOfDay(ZONE).plusMinutes(start).toInstant();
            Instant close=(end>start ? day.atStartOfDay(ZONE).plusMinutes(end) : day.plusDays(1).atStartOfDay(ZONE).plusMinutes(end)).toInstant();
            Instant a=from.isAfter(open)?from:open,b=to.isBefore(close)?to:close;
            if(a.isBefore(b)) seconds+=Duration.between(a,b).getSeconds();
        }
        return seconds/60;
    }
}
