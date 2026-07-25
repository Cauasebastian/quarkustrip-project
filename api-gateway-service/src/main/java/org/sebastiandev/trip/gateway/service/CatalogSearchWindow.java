package org.sebastiandev.trip.gateway.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@ApplicationScoped
public class CatalogSearchWindow {
    private final Clock clock;

    public CatalogSearchWindow() {
        this(Clock.systemUTC());
    }

    CatalogSearchWindow(Clock clock) {
        this.clock = clock;
    }

    public Stay stay(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null && checkOut == null) {
            LocalDate start = LocalDate.now(clock).plusDays(1);
            return new Stay(start, start.plusDays(3), true);
        }
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new BadRequestException("checkIn and checkOut must define a valid [checkIn, checkOut) interval");
        }
        return new Stay(checkIn, checkOut, false);
    }

    public Usage usage(OffsetDateTime startsAt, OffsetDateTime endsAt) {
        if (startsAt == null && endsAt == null) {
            OffsetDateTime start = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC)
                    .plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
            return new Usage(start, start.plusDays(3), true);
        }
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new BadRequestException("startsAt and endsAt must define a valid [startsAt, endsAt) interval");
        }
        return new Usage(startsAt, endsAt, false);
    }

    public record Stay(LocalDate checkIn, LocalDate checkOut, boolean defaultPeriod) {
    }

    public record Usage(OffsetDateTime startsAt, OffsetDateTime endsAt, boolean defaultPeriod) {
    }
}
