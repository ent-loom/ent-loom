package com.entloom.crud.core.capability.importing;

import java.util.Locale;
import java.util.Objects;

/**
 * 导入格式描述。
 */
public final class ImportFormatDescriptor {
    private final String format;
    private final String displayName;
    private final String contentType;
    private final String fileExtension;
    private final ImportFileParser parser;
    private final ImportErrorFileWriter errorFileWriter;

    public ImportFormatDescriptor(
        String format,
        String displayName,
        String contentType,
        String fileExtension,
        ImportFileParser parser,
        ImportErrorFileWriter errorFileWriter
    ) {
        this.format = normalizeRequired(format, "format");
        this.displayName = normalizeRequired(displayName, "displayName");
        this.contentType = normalizeRequired(contentType, "contentType");
        this.fileExtension = normalizeRequired(fileExtension, "fileExtension");
        this.parser = Objects.requireNonNull(parser, "parser 不能为空");
        this.errorFileWriter = Objects.requireNonNull(errorFileWriter, "errorFileWriter 不能为空");
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

    public ImportFileParser getParser() {
        return parser;
    }

    public ImportErrorFileWriter getErrorFileWriter() {
        return errorFileWriter;
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
