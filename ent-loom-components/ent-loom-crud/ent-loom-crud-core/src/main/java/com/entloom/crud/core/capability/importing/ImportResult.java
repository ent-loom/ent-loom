package com.entloom.crud.core.capability.importing;

import com.entloom.crud.core.foundation.taskfile.CrudTask;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 导入结果。
 */
public final class ImportResult {
    private final boolean accepted;
    private final boolean async;
    private final CrudTask task;
    private final FileRef errorFile;
    private final int totalRows;
    private final int validRows;
    private final int failedRows;
    private final int insertedRows;
    private final int updatedRows;
    private final List<ImportRowError> rowErrors;

    private ImportResult(Builder builder) {
        this.accepted = builder.accepted;
        this.async = builder.async;
        this.task = builder.task;
        this.errorFile = builder.errorFile;
        this.totalRows = builder.totalRows;
        this.validRows = builder.validRows;
        this.failedRows = builder.failedRows;
        this.insertedRows = builder.insertedRows;
        this.updatedRows = builder.updatedRows;
        this.rowErrors = Collections.unmodifiableList(copyErrors(builder.rowErrors));
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isAccepted() {
        return accepted;
    }

    public boolean isAsync() {
        return async;
    }

    public CrudTask getTask() {
        return task;
    }

    public FileRef getErrorFile() {
        return errorFile;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getValidRows() {
        return validRows;
    }

    public int getFailedRows() {
        return failedRows;
    }

    public int getInsertedRows() {
        return insertedRows;
    }

    public int getUpdatedRows() {
        return updatedRows;
    }

    public List<ImportRowError> getRowErrors() {
        return copyErrors(rowErrors);
    }

    private static List<ImportRowError> copyErrors(List<ImportRowError> source) {
        return source == null ? new ArrayList<ImportRowError>() : new ArrayList<ImportRowError>(source);
    }

    public static final class Builder {
        private boolean accepted = true;
        private boolean async;
        private CrudTask task;
        private FileRef errorFile;
        private int totalRows;
        private int validRows;
        private int failedRows;
        private int insertedRows;
        private int updatedRows;
        private List<ImportRowError> rowErrors = new ArrayList<ImportRowError>();

        public Builder accepted(boolean accepted) {
            this.accepted = accepted;
            return this;
        }

        public Builder async(boolean async) {
            this.async = async;
            return this;
        }

        public Builder task(CrudTask task) {
            this.task = task;
            return this;
        }

        public Builder errorFile(FileRef errorFile) {
            this.errorFile = errorFile;
            return this;
        }

        public Builder totalRows(int totalRows) {
            this.totalRows = totalRows;
            return this;
        }

        public Builder validRows(int validRows) {
            this.validRows = validRows;
            return this;
        }

        public Builder failedRows(int failedRows) {
            this.failedRows = failedRows;
            return this;
        }

        public Builder insertedRows(int insertedRows) {
            this.insertedRows = insertedRows;
            return this;
        }

        public Builder updatedRows(int updatedRows) {
            this.updatedRows = updatedRows;
            return this;
        }

        public Builder rowErrors(List<ImportRowError> rowErrors) {
            this.rowErrors = copyErrors(rowErrors);
            return this;
        }

        public ImportResult build() {
            return new ImportResult(this);
        }
    }
}
