package com.entloom.crud.core.foundation.taskfile;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件引用，不暴露具体存储实现。
 */
public final class FileRef {
    private final String fileId;
    private final String fileName;
    private final String contentType;
    private final Long size;
    private final CrudFileStorageType storageType;
    private final String storageKey;
    private final Instant expiresAt;
    private final Map<String, Object> attributes;

    private FileRef(Builder builder) {
        this.fileId = builder.fileId;
        this.fileName = builder.fileName;
        this.contentType = builder.contentType;
        this.size = builder.size;
        this.storageType = builder.storageType;
        this.storageKey = builder.storageKey;
        this.expiresAt = builder.expiresAt;
        this.attributes = Collections.unmodifiableMap(copyAttributes(builder.attributes));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSize() {
        return size;
    }

    public CrudFileStorageType getStorageType() {
        return storageType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Map<String, Object> getAttributes() {
        return copyAttributes(attributes);
    }

    private static Map<String, Object> copyAttributes(Map<String, Object> source) {
        return source == null ? new HashMap<String, Object>() : new HashMap<String, Object>(source);
    }

    public static final class Builder {
        private String fileId;
        private String fileName;
        private String contentType;
        private Long size;
        private CrudFileStorageType storageType = CrudFileStorageType.EXTERNAL;
        private String storageKey;
        private Instant expiresAt;
        private Map<String, Object> attributes = new HashMap<String, Object>();

        public Builder fileId(String fileId) {
            this.fileId = fileId;
            return this;
        }

        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder size(Long size) {
            this.size = size;
            return this;
        }

        public Builder storageType(CrudFileStorageType storageType) {
            this.storageType = storageType == null ? CrudFileStorageType.EXTERNAL : storageType;
            return this;
        }

        public Builder storageKey(String storageKey) {
            this.storageKey = storageKey;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = copyAttributes(attributes);
            return this;
        }

        public FileRef build() {
            return new FileRef(this);
        }
    }
}
