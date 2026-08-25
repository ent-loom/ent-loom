package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlIndexMetadata;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单表结构差异计划。
 *
 * <p>该计划只暴露 E3 允许自动执行的新增和有限修改；删除、主键变化、索引重建
 * 等危险变化保存在 {@link #errors()} 中，由引擎阻止执行。</p>
 */
public final class DdlSchemaDiff {
    private final boolean tableCommentChanged;
    private final List<DdlFieldMetadata> addedFields;
    private final List<DdlFieldChange> changedFields;
    private final List<DdlIndexMetadata> addedIndexes;
    private final List<String> errors;

    public DdlSchemaDiff(boolean tableCommentChanged,
                         List<DdlFieldMetadata> addedFields,
                         List<DdlFieldChange> changedFields,
                         List<DdlIndexMetadata> addedIndexes,
                         List<String> errors) {
        this.tableCommentChanged = tableCommentChanged;
        this.addedFields = immutableCopy(addedFields);
        this.changedFields = immutableCopyChanges(changedFields);
        this.addedIndexes = immutableCopyIndexes(addedIndexes);
        this.errors = immutableCopyStrings(errors);
    }

    public boolean tableCommentChanged() {
        return tableCommentChanged;
    }

    public List<DdlFieldMetadata> addedFields() {
        return addedFields;
    }

    public List<DdlFieldChange> changedFields() {
        return changedFields;
    }

    public List<DdlIndexMetadata> addedIndexes() {
        return addedIndexes;
    }

    public List<String> errors() {
        return errors;
    }

    public boolean hasChanges() {
        return tableCommentChanged || !addedFields.isEmpty()
                || !changedFields.isEmpty() || !addedIndexes.isEmpty();
    }

    private static List<DdlFieldMetadata> immutableCopy(List<DdlFieldMetadata> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<DdlFieldMetadata>(source));
    }

    private static List<DdlFieldChange> immutableCopyChanges(List<DdlFieldChange> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<DdlFieldChange>(source));
    }

    private static List<DdlIndexMetadata> immutableCopyIndexes(List<DdlIndexMetadata> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<DdlIndexMetadata>(source));
    }

    private static List<String> immutableCopyStrings(List<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(source));
    }
}
