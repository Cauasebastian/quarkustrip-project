package org.sebastiandev.trip.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.BadRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CatalogSearchWindowTest {
    private final CatalogSearchWindow windows = new CatalogSearchWindow(
            Clock.fixed(Instant.parse("2026-07-25T01:30:00Z"), ZoneOffset.UTC));

    @Test
    void defaultsHotelSearchToTheNextThreeDays() {
        CatalogSearchWindow.Stay stay = windows.stay(null, null);

        assertEquals(LocalDate.parse("2026-07-26"), stay.checkIn());
        assertEquals(LocalDate.parse("2026-07-29"), stay.checkOut());
        assertTrue(stay.defaultPeriod());
    }

    @Test
    void preservesAnExplicitHotelPeriod() {
        CatalogSearchWindow.Stay stay = windows.stay(
                LocalDate.parse("2026-08-02"), LocalDate.parse("2026-08-05"));

        assertEquals(LocalDate.parse("2026-08-02"), stay.checkIn());
        assertEquals(LocalDate.parse("2026-08-05"), stay.checkOut());
        assertFalse(stay.defaultPeriod());
    }

    @Test
    void defaultsTransportSearchToThreeDaysStartingTomorrowMorning() {
        CatalogSearchWindow.Usage usage = windows.usage(null, null);

        assertEquals(OffsetDateTime.parse("2026-07-26T09:00:00Z"), usage.startsAt());
        assertEquals(OffsetDateTime.parse("2026-07-29T09:00:00Z"), usage.endsAt());
        assertTrue(usage.defaultPeriod());
    }

    @Test
    void rejectsPartialOrInvertedPeriods() {
        assertThrows(BadRequestException.class,
                () -> windows.stay(LocalDate.parse("2026-08-02"), null));
        assertThrows(BadRequestException.class,
                () -> windows.usage(OffsetDateTime.parse("2026-08-02T10:00:00Z"),
                        OffsetDateTime.parse("2026-08-02T09:00:00Z")));
    }
}
