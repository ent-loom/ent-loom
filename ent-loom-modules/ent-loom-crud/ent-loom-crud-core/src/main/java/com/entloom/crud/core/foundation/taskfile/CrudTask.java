package com.entloom.crud.core.foundation.taskfile;

import java.time.Instant;

/**
 * Import / Export 任务摘要。
 */
public final class CrudTask {
    private final String taskId;
    private final CrudTaskStatus status;
    private final CrudTaskContextSnapshot contextSnapshot;
    private final FileRef sourceFile;
    private final FileRef resultFile;
    private final FileRef errorFile;
    private final Integer progress;
    private final String message;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant finishedAt;

    private CrudTask(Builder builder) {
        this.taskId = builder.taskId;
        this.status = builder.status == null ? CrudTaskStatus.PENDING : builder.status;
        this.contextSnapshot = builder.contextSnapshot;
        this.sourceFile = builder.sourceFile;
        this.resultFile = builder.resultFile;
        this.errorFile = builder.errorFile;
        this.progress = builder.progress;
        this.message = builder.message;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
        this.finishedAt = builder.finishedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTaskId() {
        return taskId;
    }

    public CrudTaskStatus getStatus() {
        return status;
    }

    public CrudTaskContextSnapshot getContextSnapshot() {
        return contextSnapshot;
    }

    public FileRef getSourceFile() {
        return sourceFile;
    }

    public FileRef getResultFile() {
        return resultFile;
    }

    public FileRef getErrorFile() {
        return errorFile;
    }

    public Integer getProgress() {
        return progress;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public static final class Builder {
        private String taskId;
        private CrudTaskStatus status;
        private CrudTaskContextSnapshot contextSnapshot;
        private FileRef sourceFile;
        private FileRef resultFile;
        private FileRef errorFile;
        private Integer progress;
        private String message;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant finishedAt;

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder status(CrudTaskStatus status) {
            this.status = status;
            return this;
        }

        public Builder contextSnapshot(CrudTaskContextSnapshot contextSnapshot) {
            this.contextSnapshot = contextSnapshot;
            return this;
        }

        public Builder sourceFile(FileRef sourceFile) {
            this.sourceFile = sourceFile;
            return this;
        }

        public Builder resultFile(FileRef resultFile) {
            this.resultFile = resultFile;
            return this;
        }

        public Builder errorFile(FileRef errorFile) {
            this.errorFile = errorFile;
            return this;
        }

        public Builder progress(Integer progress) {
            this.progress = progress;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder finishedAt(Instant finishedAt) {
            this.finishedAt = finishedAt;
            return this;
        }

        public CrudTask build() {
            return new CrudTask(this);
        }
    }
}
