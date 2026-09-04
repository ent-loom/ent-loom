package com.entloom.crud.core.foundation.taskfile;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件流式写入请求。
 */
public final class FileStreamWriteRequest {
    private final String fileName;
    private final String contentType;
    private final InputStream inputStream;
    private final Long size;
    private final Map<String, Object> attributes;

    private FileStreamWriteRequest(Builder builder) {
        this.fileName = builder.fileName;
        this.contentType = builder.contentType;
        this.inputStream = builder.inputStream;
        if (builder.size != null && builder.size.longValue() < 0L) {
            throw new IllegalArgumentException("size 不能小于 0");
        }
        this.size = builder.size;
        this.attributes = Collections.unmodifiableMap(copyAttributes(builder.attributes));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public Long getSize() {
        return size;
    }

    public Map<String, Object> getAttributes() {
        return copyAttributes(attributes);
    }

    private static Map<String, Object> copyAttributes(Map<String, Object> source) {
        return source == null ? new HashMap<String, Object>() : new HashMap<String, Object>(source);
    }

    public static final class Builder {
        private String fileName;
        private String contentType;
        private InputStream inputStream;
        private Long size;
        private Map<String, Object> attributes = new HashMap<String, Object>();

        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder inputStream(InputStream inputStream) {
            this.inputStream = inputStream;
            return this;
        }

        public Builder size(Long size) {
            this.size = size;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = copyAttributes(attributes);
            return this;
        }

        public FileStreamWriteRequest build() {
            return new FileStreamWriteRequest(this);
        }
    }
}
