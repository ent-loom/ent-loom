package com.entloom.crud.core.capability.exporting;

import com.entloom.crud.core.foundation.taskfile.CrudTask;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 导出结果。
 */
public final class ExportResult {
    private final boolean accepted;
    private final boolean async;
    private final CrudTask task;
    private final FileRef file;
    private final int totalRows;
    private final List<ExportColumn> columns;
    private final List<Map<String, Object>> previewRows;

    private ExportResult(Builder builder) {
        this.accepted = builder.accepted;
        this.async = builder.async;
        this.task = builder.task;
        this.file = builder.file;
        this.totalRows = builder.totalRows;
        this.columns = Collections.unmodifiableList(copyColumns(builder.columns));
        this.previewRows = Collections.unmodifiableList(copyRows(builder.previewRows));
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

    public FileRef getFile() {
        return file;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public List<ExportColumn> getColumns() {
        return copyColumns(columns);
    }

    public List<Map<String, Object>> getPreviewRows() {
        return copyRows(previewRows);
    }

    private static List<Map<String, Object>> copyRows(List<Map<String, Object>> source) {
        List<Map<String, Object>> target = new ArrayList<Map<String, Object>>();
        if (source == null) {
            return target;
        }
        for (Map<String, Object> row : source) {
            target.add(row == null ? null : new java.util.LinkedHashMap<String, Object>(row));
        }
        return target;
    }

    private static List<ExportColumn> copyColumns(List<ExportColumn> source) {
        return source == null ? new ArrayList<ExportColumn>() : new ArrayList<ExportColumn>(source);
    }

    public static final class Builder {
        private boolean accepted = true;
        private boolean async;
        private CrudTask task;
        private FileRef file;
        private int totalRows;
        private List<ExportColumn> columns = new ArrayList<ExportColumn>();
        private List<Map<String, Object>> previewRows = new ArrayList<Map<String, Object>>();

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

        public Builder file(FileRef file) {
            this.file = file;
            return this;
        }

        public Builder totalRows(int totalRows) {
            this.totalRows = totalRows;
            return this;
        }

        public Builder columns(List<ExportColumn> columns) {
            this.columns = copyColumns(columns);
            return this;
        }

        public Builder previewRows(List<Map<String, Object>> previewRows) {
            this.previewRows = copyRows(previewRows);
            return this;
        }

        public ExportResult build() {
            return new ExportResult(this);
        }
    }
}
