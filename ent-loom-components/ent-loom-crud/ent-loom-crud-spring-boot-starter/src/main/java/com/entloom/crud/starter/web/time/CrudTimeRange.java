package com.entloom.crud.starter.web.time;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * 时间范围（左闭右开）。
 */
public final class CrudTimeRange {
    private final ZonedDateTime start;
    private final ZonedDateTime end;

    private CrudTimeRange(ZonedDateTime start, ZonedDateTime end) {
        this.start = Objects.requireNonNull(start, "start");
        this.end = Objects.requireNonNull(end, "end");
    }

    public static CrudTimeRange of(ZonedDateTime start, ZonedDateTime end) {
        return new CrudTimeRange(start, end);
    }

    public ZonedDateTime getStart() {
        return start;
    }

    public ZonedDateTime getEnd() {
        return end;
    }
}
