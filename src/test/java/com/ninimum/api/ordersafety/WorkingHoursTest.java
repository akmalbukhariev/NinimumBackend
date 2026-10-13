package com.ninimum.api.ordersafety;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class WorkingHoursTest {
    WorkingHours hours(int days,String start,String end){return new WorkingHours(Map.of("working_days",days,"working_start",start,"working_end",end));}
    @Test void closedNightDoesNotCount(){assertEquals(60,hours(127,"09:00","18:00").minutes(Instant.parse("2026-10-08T12:00:00Z"),Instant.parse("2026-10-09T04:00:00Z")));}
    @Test void weekendsDoNotCount(){assertEquals(120,hours(31,"09:00","18:00").minutes(Instant.parse("2026-10-09T12:00:00Z"),Instant.parse("2026-10-12T05:00:00Z")));}
    @Test void overnightShiftBelongsToOpeningDay(){assertEquals(180,hours(16,"22:00","06:00").minutes(Instant.parse("2026-10-09T18:00:00Z"),Instant.parse("2026-10-09T21:00:00Z")));}
    @Test void allDayPreservesOrdinaryElapsedTime(){assertEquals(1500,hours(127,"00:00","24:00").minutes(Instant.parse("2026-10-09T00:00:00Z"),Instant.parse("2026-10-10T01:00:00Z")));}
    @Test void futureTimestampCannotTriggerTimer(){assertEquals(0,hours(127,"00:00","24:00").minutes(Instant.parse("2026-10-10T01:00:00Z"),Instant.parse("2026-10-09T00:00:00Z")));}
    @Test void noWorkingDayAndInvalidTimesRejected(){assertThrows(IllegalArgumentException.class,()->hours(0,"09:00","18:00"));assertThrows(RuntimeException.class,()->hours(127,"25:00","18:00"));assertThrows(IllegalArgumentException.class,()->hours(127,"09:00","09:00"));}
}
