package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlIndexMetadata;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单表结构差异计划。
 *
 * <p>默认只允许新增和有限修改；显式选择全量同步时，计划还包含列、索引删除和主键修改。</p>
 */
public final class DdlSchemaDiff {
    private final boolean tableCommentChanged;
    private final List<DdlFieldMetadata> addedFields;
    private final List<DdlFieldChange> changedFields;
    private final List<DdlIndexMetadata> addedIndexes;
    private final List<String> errors;
    private final List<String> warnings;
    private final List<String> removedColumns;
    private final List<String> removedIndexes;
    private final boolean primaryKeyChanged;

    public DdlSchemaDiff(boolean tableCommentChanged,
                         List<DdlFieldMetadata> addedFields,
                         List<DdlFieldChange> changedFields,
                         List<DdlIndexMetadata> addedIndexes,
                         List<String> errors) {
        this(tableCommentChanged, addedFields, changedFields, addedIndexes, errors,
                Collections.<String>emptyList());
    }

    public DdlSchemaDiff(boolean tableCommentChanged,
                         List<DdlFieldMetadata> addedFields,
                         List<DdlFieldChange> changedFields,
                         List<DdlIndexMetadata> addedIndexes,
                         List<String> errors,
                         List<String> warnings) {
        this(tableCommentChanged, addedFields, changedFields, addedIndexes, errors, warnings,
                Collections.<String>emptyList(), Collections.<String>emptyList(), false);
    }

    public DdlSchemaDiff(boolean tableCommentChanged,
                         List<DdlFieldMetadata> addedFields,
                         List<DdlFieldChange> changedFields,
                         List<DdlIndexMetadata> addedIndexes,
                         List<String> errors,
                         List<String> warnings,
                         List<String> removedColumns,
                         List<String> removedIndexes,
                         boolean primaryKeyChanged) {
        this.tableCommentChanged = tableCommentChanged;
        this.addedFields = immutableCopy(addedFields);
        this.changedFields = immutableCopyChanges(changedFields);
        this.addedIndexes = immutableCopyIndexes(addedIndexes);
        this.errors = immutableCopyStrings(errors);
        this.warnings = immutableCopyStrings(warnings);
        this.removedColumns = immutableCopyStrings(removedColumns);
        this.removedIndexes = immutableCopyStrings(removedIndexes);
        this.primaryKeyChanged = primaryKeyChanged;
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

    public List<String> warnings() {
        return warnings;
    }

    public boolean hasChanges() {
        return tableCommentChanged || !addedFields.isEmpty()
                || !changedFields.isEmpty() || !addedIndexes.isEmpty()
                || !removedColumns.isEmpty() || !removedIndexes.isEmpty() || primaryKeyChanged;
    }

    public List<String> removedColumns() {
        return removedColumns;
    }

    public List<String> removedIndexes() {
        return removedIndexes;
    }

    public boolean primaryKeyChanged() {
        return primaryKeyChanged;
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
