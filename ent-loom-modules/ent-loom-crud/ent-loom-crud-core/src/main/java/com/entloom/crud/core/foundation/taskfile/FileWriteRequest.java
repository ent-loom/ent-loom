package com.entloom.crud.core.foundation.taskfile;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件写入请求。
 */
public final class FileWriteRequest {
    private final String fileName;
    private final String contentType;
    private final byte[] content;
    private final Map<String, Object> attributes;

    private FileWriteRequest(Builder builder) {
        this.fileName = builder.fileName;
        this.contentType = builder.contentType;
        this.content = copyContent(builder.content);
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

    public byte[] getContent() {
        return copyContent(content);
    }

    public Map<String, Object> getAttributes() {
        return copyAttributes(attributes);
    }

    private static byte[] copyContent(byte[] source) {
        return source == null ? null : Arrays.copyOf(source, source.length);
    }

    private static Map<String, Object> copyAttributes(Map<String, Object> source) {
        return source == null ? new HashMap<String, Object>() : new HashMap<String, Object>(source);
    }

    public static final class Builder {
        private String fileName;
        private String contentType;
        private byte[] content;
        private Map<String, Object> attributes = new HashMap<String, Object>();

        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder content(byte[] content) {
            this.content = copyContent(content);
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = copyAttributes(attributes);
            return this;
        }

        public FileWriteRequest build() {
            return new FileWriteRequest(this);
        }
    }
}
