package com.entloom.crud.core.capability.exporting;

/**
 * 导出展示渲染选项。
 */
public final class ExportRenderOptions {
    private final String timezone;

    public ExportRenderOptions(String timezone) {
        this.timezone = timezone;
    }

    public String getTimezone() {
        return timezone;
    }
}
