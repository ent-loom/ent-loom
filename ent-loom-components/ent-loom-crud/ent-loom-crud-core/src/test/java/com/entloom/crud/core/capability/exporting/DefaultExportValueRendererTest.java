package com.entloom.crud.core.capability.exporting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultExportValueRendererTest {
    @Test
    void rendererHonorsDictionaryAndFieldFormats() {
        DefaultExportValueRenderer renderer = new DefaultExportValueRenderer((context, value) -> {
            if ("order_status".equals(context.getFieldMeta().getDictionaryCode()) && "PAID".equals(String.valueOf(value))) {
                return "已支付";
            }
            return null;
        });
        List<ExportColumn> columns = Arrays.asList(
            column("status", String.class, null, "order_status"),
            column("enabled", Boolean.class, "启用/停用", null),
            column("createdAt", Instant.class, "yyyy/MM/dd HH:mm", null),
            column("localCreatedAt", LocalDateTime.class, "yyyy-MM-dd", null),
            column("amount", BigDecimal.class, "#,##0.00", null),
            column("serialNo", BigDecimal.class, "text", null)
        );
        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("status", "PAID");
        raw.put("enabled", Integer.valueOf(1));
        raw.put("createdAt", Instant.parse("2026-05-02T00:00:00Z"));
        raw.put("localCreatedAt", LocalDateTime.of(2026, 5, 2, 9, 8, 7));
        raw.put("amount", new BigDecimal("12345.6"));
        raw.put("serialNo", new BigDecimal("1E+10"));

        Map<String, Object> row = renderer.renderRow(columns, raw, new ExportRenderOptions("Asia/Shanghai"));

        assertEquals("已支付", row.get("status"));
        assertEquals("启用", row.get("enabled"));
        assertEquals("2026/05/02 08:00", row.get("createdAt"));
        assertEquals("2026-05-02", row.get("localCreatedAt"));
        assertEquals("12,345.60", row.get("amount"));
        assertEquals("10000000000", row.get("serialNo"));
    }

    @Test
    void dictionaryMissFallsBackToDefaultRendering() {
        DefaultExportValueRenderer renderer = new DefaultExportValueRenderer((context, value) -> null);
        List<ExportColumn> columns = Arrays.asList(column("enabled", Boolean.class, null, "enabled_status"));
        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("enabled", "0");

        Map<String, Object> row = renderer.renderRow(columns, raw, new ExportRenderOptions("UTC"));

        assertEquals("否", row.get("enabled"));
    }

    private ExportColumn column(String field, Class<?> javaType, String format, String dictionaryCode) {
        EntityFieldMeta fieldMeta = new EntityFieldMeta(
            field,
            javaType,
            field,
            true,
            false,
            true,
            true,
            true,
            true,
            field,
            format,
            dictionaryCode,
            null
        );
        return new ExportColumn(field, field, field, fieldMeta, format);
    }
}
