package com.entloom.crud.core.capability.exporting;

import java.util.Locale;
import java.util.Objects;

/**
 * 导出格式描述。
 */
public final class ExportFormatDescriptor {
    private final String format;
    private final String displayName;
    private final String contentType;
    private final String fileExtension;
    private final ExportFileWriter writer;

    public ExportFormatDescriptor(
        String format,
        String displayName,
        String contentType,
        String fileExtension,
        ExportFileWriter writer
    ) {
        this.format = normalizeRequired(format, "format");
        this.displayName = normalizeRequired(displayName, "displayName");
        this.contentType = normalizeRequired(contentType, "contentType");
        this.fileExtension = normalizeRequired(fileExtension, "fileExtension");
        this.writer = Objects.requireNonNull(writer, "writer 不能为空");
    }

    public String getFormat() {
        return format;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public ExportFileWriter getWriter() {
        return writer;
    }

    private static String normalizeRequired(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        String normalized = value.trim();
        if ("format".equals(name) || "fileExtension".equals(name)) {
            return normalized.toLowerCase(Locale.ROOT);
        }
        return normalized;
    }
}
