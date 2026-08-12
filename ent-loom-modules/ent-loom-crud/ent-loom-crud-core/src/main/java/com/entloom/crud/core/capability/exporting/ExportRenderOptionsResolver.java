package com.entloom.crud.core.capability.exporting;

import com.entloom.crud.core.exception.ValidationException;
import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * 导出展示渲染选项解析器。
 */
public class ExportRenderOptionsResolver {
    private static final String UTC = "UTC";
    private final String applicationDefaultTimezone;

    public ExportRenderOptionsResolver() {
        this(null);
    }

    public ExportRenderOptionsResolver(String applicationDefaultTimezone) {
        this.applicationDefaultTimezone = normalize(applicationDefaultTimezone);
    }

    public ExportRenderOptions resolve(ExportSpec spec) {
        String requested = spec == null || spec.getRenderOptions() == null ? null : spec.getRenderOptions().getTimezone();
        String timezone = firstPresent(normalize(requested), applicationDefaultTimezone, UTC);
        validate(timezone, requested != null && !requested.trim().isEmpty());
        return new ExportRenderOptions(timezone);
    }

    private String firstPresent(String first, String second, String fallback) {
        if (first != null) {
            return first;
        }
        return second == null ? fallback : second;
    }

    private void validate(String timezone, boolean requestProvided) {
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            if (requestProvided) {
                throw new ValidationException("导出 renderOptions.timezone 不合法: " + timezone);
            }
            throw new ValidationException("导出默认 timezone 不合法: " + timezone);
        }
    }

    private String normalize(String raw) {
        return raw == null || raw.trim().isEmpty() ? null : raw.trim();
    }
}
