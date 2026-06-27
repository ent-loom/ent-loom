package com.entloom.crud.starter.web.time;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 框架内置时间预设解析器。
 */
public class BuiltinCrudTimePresetResolver implements CrudTimePresetResolver {
    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }

    @Override
    public CrudTimeRange resolve(String preset, ZoneId zoneId) {
        CrudBuiltinTimePreset builtinPreset = CrudBuiltinTimePreset.from(preset);
        if (builtinPreset == null) {
            return null;
        }
        LocalDate today = LocalDate.now(zoneId);
        ZonedDateTime start;
        ZonedDateTime end;
        switch (builtinPreset) {
            case TODAY:
                start = today.atStartOfDay(zoneId);
                end = start.plusDays(1);
                break;
            case YESTERDAY:
                end = today.atStartOfDay(zoneId);
                start = end.minusDays(1);
                break;
            case LAST_7_DAYS:
                end = today.plusDays(1).atStartOfDay(zoneId);
                start = end.minusDays(7);
                break;
            case LAST_30_DAYS:
                end = today.plusDays(1).atStartOfDay(zoneId);
                start = end.minusDays(30);
                break;
            case THIS_MONTH:
                start = today.withDayOfMonth(1).atStartOfDay(zoneId);
                end = start.plusMonths(1);
                break;
            case LAST_MONTH:
                end = today.withDayOfMonth(1).atStartOfDay(zoneId);
                start = end.minusMonths(1);
                break;
            default:
                return null;
        }
        return CrudTimeRange.of(start, end);
    }
}
