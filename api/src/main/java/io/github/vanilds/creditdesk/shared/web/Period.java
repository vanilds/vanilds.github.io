package io.github.vanilds.creditdesk.shared.web;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * A search window given as São Paulo calendar dates, both inclusive, turned into a half-open instant range
 * {@code [start, end)}. Used by the proposals grid and the audit log, which have the same rules: defaults to the
 * last 30 days, at most 90 days, so every query stays on a time index.
 */
public record Period(Instant start, Instant endExclusive) {

    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_DAYS = 90;

    public static Period of(LocalDate from, LocalDate to, Clock clock, ZoneId zone) {
        LocalDate end = to != null ? to : LocalDate.now(clock.withZone(zone));
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        if (start.isAfter(end)) {
            throw ApiException.badRequest("invalid-period", "Invalid period", "'from' must be on or before 'to'.");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw ApiException.badRequest("invalid-period", "Invalid period",
                    "The period can be at most %d days.".formatted(MAX_DAYS));
        }
        return new Period(start.atStartOfDay(zone).toInstant(), end.plusDays(1).atStartOfDay(zone).toInstant());
    }
}
