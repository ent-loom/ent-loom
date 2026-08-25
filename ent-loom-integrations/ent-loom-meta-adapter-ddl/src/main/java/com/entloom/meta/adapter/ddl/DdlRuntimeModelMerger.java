package com.entloom.meta.adapter.ddl;

import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlIndexMetadata;
import com.entloom.ddl.enums.DdlTableSize;
import com.entloom.ddl.enums.GenerationStrategy;
import com.entloom.meta.contract.descriptor.EntEntityDescriptor;
import com.entloom.meta.contract.descriptor.EntFieldConstraintDescriptor;
import com.entloom.meta.contract.descriptor.EntFieldDescriptor;
import com.entloom.meta.contract.descriptor.EntIndexDescriptor;
import com.entloom.meta.contract.descriptor.MetaDescriptorProperties;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCollector;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;
import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.contract.value.SourcedValue;
import com.entloom.meta.enums.EntFieldKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * DDL 目标模型合并器。DDL native 显式属性优先，Meta 只提供通用语义和推断值。
 */
final class DdlRuntimeModelMerger {
    MetaDiagnosticResult<DdlEntityMetadata> merge(
        Class<?> entityClass,
        EntEntityDescriptor meta,
        DdlNativeEntityModel nativeModel
    ) {
        MetaDiagnosticCollector diagnostics = new MetaDiagnosticCollector();
        if (meta == null && nativeModel == null) {
            return MetaDiagnosticResult.of(null, diagnostics.diagnostics());
        }

        Map<String, EntFieldDescriptor> metaFields = indexMetaFields(meta);
        LinkedHashSet<String> fieldNames = new LinkedHashSet<String>();
        fieldNames.addAll(metaFields.keySet());
        if (nativeModel != null) {
            fieldNames.addAll(nativeModel.fields().keySet());
        }

        Map<String, String> columnsByField = new LinkedHashMap<String, String>();
        List<DdlFieldMetadata> fields = new ArrayList<DdlFieldMetadata>();
        for (String fieldName : fieldNames) {
            EntFieldDescriptor metaField = metaFields.get(fieldName);
            DdlNativeFieldModel nativeField = nativeModel == null ? null : nativeModel.fields().get(fieldName);
            DdlFieldMetadata field = mergeField(entityClass, metaField, nativeField, diagnostics);
            if (field != null) {
                fields.add(field);
                columnsByField.put(fieldName, field.columnName());
            }
        }

        List<DdlIndexMetadata> indexes = mergeIndexes(entityClass, meta, nativeModel, columnsByField, diagnostics);
        SourcedValue<String> metaTable = meta == null
            ? null
            : sourced(meta, MetaDescriptorProperties.ENTITY_NAME, meta.entityName());
        SourcedValue<String> nativeTable = nativeModel == null ? null : nativeModel.tableName();
        SourcedValue<String> table = choose(
            "tableName",
            entityClass,
            null,
            nativeTable,
            metaTable,
            SourcedValue.inferred(toSnake(entityClass.getSimpleName())),
            diagnostics
        );
        SourcedValue<String> schema = choose(
            "schema",
            entityClass,
            null,
            nativeModel == null ? null : nativeModel.schema(),
            SourcedValue.defaulted(""),
            diagnostics
        );
        SourcedValue<String> comment = choose(
            "comment",
            entityClass,
            null,
            nativeModel == null ? null : nativeModel.comment(),
            meta == null ? null : sourced(meta, MetaDescriptorProperties.DESCRIPTION, meta.description()),
            SourcedValue.defaulted(""),
            diagnostics
        );
        SourcedValue<DdlTableSize> tableSize = choose(
            "tableSize",
            entityClass,
            null,
            nativeModel == null ? null : nativeModel.tableSize(),
            SourcedValue.defaulted(DdlTableSize.UNSET),
            diagnostics
        );

        try {
            return MetaDiagnosticResult.of(
                new DdlEntityMetadata(
                    entityClass.getName(),
                    valueOrDefault(schema, ""),
                    valueOrDefault(table, toSnake(entityClass.getSimpleName())),
                    valueOrDefault(comment, ""),
                    valueOrDefault(tableSize, DdlTableSize.UNSET),
                    fields,
                    indexes
                ),
                diagnostics.diagnostics()
            );
        } catch (IllegalArgumentException exception) {
            diagnostics.add(MetaDiagnostic.error(MetaDiagnosticCode.UNKNOWN)
                .entityClass(entityClass)
                .property("ddlMetadata")
                .location(entityClass.getName())
                .message("DDL Adapter 无法构造实体元数据: " + exception.getMessage())
                .build());
            return MetaDiagnosticResult.of(null, diagnostics.diagnostics());
        }
    }

    private DdlFieldMetadata mergeField(
        Class<?> entityClass,
        EntFieldDescriptor metaField,
        DdlNativeFieldModel nativeField,
        MetaDiagnosticCollector diagnostics
    ) {
        String fieldName = metaField == null ? nativeField.fieldName() : metaField.fieldName();
        Class<?> javaType = metaField != null && metaField.javaType() != null
            ? metaField.javaType()
            : nativeField.javaType();
        SourcedValue<String> column = choose(
            "columnName", entityClass, fieldName,
            nativeField == null ? null : nativeField.columnName(),
            SourcedValue.inferred(toSnake(fieldName)),
            diagnostics
        );
        SourcedValue<String> definition = choose(
            "columnDefinition", entityClass, fieldName,
            nativeField == null ? null : nativeField.columnDefinition(),
            SourcedValue.unknown(null),
            diagnostics
        );
        SourcedValue<Boolean> nullable = choose(
            "nullable", entityClass, fieldName,
            nativeField == null ? null : nativeField.nullable(),
            metaNullable(metaField),
            SourcedValue.inferred(Boolean.valueOf(!javaType.isPrimitive())),
            diagnostics
        );
        SourcedValue<Boolean> unique = choose(
            "unique", entityClass, fieldName,
            nativeField == null ? null : nativeField.unique(),
            SourcedValue.defaulted(Boolean.FALSE),
            diagnostics
        );
        SourcedValue<Boolean> persisted = choose(
            "persisted", entityClass, fieldName,
            nativeField == null ? null : nativeField.persisted(),
            SourcedValue.defaulted(Boolean.TRUE),
            diagnostics
        );
        SourcedValue<Boolean> primaryKey = choose(
            "primaryKey", entityClass, fieldName,
            nativeField == null ? null : nativeField.primaryKey(),
            metaPrimaryKey(metaField),
            SourcedValue.defaulted(Boolean.FALSE),
            diagnostics
        );
        SourcedValue<Integer> length = choose(
            "length", entityClass, fieldName,
            nativeField == null ? null : nativeField.length(),
            constraintInteger(metaField, "text.maxLength"),
            SourcedValue.defaulted(Integer.valueOf(-1)),
            diagnostics
        );
        SourcedValue<Integer> precision = choose(
            "precision", entityClass, fieldName,
            nativeField == null ? null : nativeField.precision(),
            constraintInteger(metaField, "number.precision"),
            SourcedValue.defaulted(Integer.valueOf(-1)),
            diagnostics
        );
        SourcedValue<Integer> scale = choose(
            "scale", entityClass, fieldName,
            nativeField == null ? null : nativeField.scale(),
            constraintInteger(metaField, "number.scale"),
            SourcedValue.defaulted(Integer.valueOf(-1)),
            diagnostics
        );
        SourcedValue<String> defaultValue = choose(
            "defaultValue", entityClass, fieldName,
            nativeField == null ? null : nativeField.defaultValue(),
            SourcedValue.defaulted(""),
            diagnostics
        );
        SourcedValue<String> comment = choose(
            "comment", entityClass, fieldName,
            nativeField == null ? null : nativeField.comment(),
            metaDescription(metaField),
            SourcedValue.defaulted(""),
            diagnostics
        );
        SourcedValue<String> renameFrom = choose(
            "renameFrom", entityClass, fieldName,
            nativeField == null ? null : nativeField.renameFrom(),
            SourcedValue.defaulted(""),
            diagnostics
        );
        SourcedValue<GenerationStrategy> generation = choose(
            "generationStrategy", entityClass, fieldName,
            nativeField == null ? null : nativeField.generationStrategy(),
            metaGenerationStrategy(metaField),
            SourcedValue.defaulted(GenerationStrategy.UNSET),
            diagnostics
        );
        try {
            return new DdlFieldMetadata(
                fieldName,
                valueOrDefault(column, toSnake(fieldName)),
                javaType,
                valueOrDefault(definition, ""),
                valueOrDefault(nullable, Boolean.valueOf(!javaType.isPrimitive())).booleanValue(),
                valueOrDefault(unique, Boolean.FALSE).booleanValue(),
                valueOrDefault(persisted, Boolean.TRUE).booleanValue(),
                valueOrDefault(primaryKey, Boolean.FALSE).booleanValue(),
                valueOrDefault(length, Integer.valueOf(-1)).intValue(),
                valueOrDefault(precision, Integer.valueOf(-1)).intValue(),
                valueOrDefault(scale, Integer.valueOf(-1)).intValue(),
                valueOrDefault(defaultValue, ""),
                valueOrDefault(comment, ""),
                valueOrDefault(renameFrom, ""),
                valueOrDefault(generation, GenerationStrategy.UNSET)
            );
        } catch (IllegalArgumentException exception) {
            diagnostics.add(MetaDiagnostic.error(MetaDiagnosticCode.UNKNOWN)
                .entityClass(entityClass)
                .field(fieldName)
                .property("field")
                .location(entityClass.getName() + "#" + fieldName)
                .message("DDL Adapter 无法构造字段元数据: " + exception.getMessage())
                .build());
            return null;
        }
    }

    private List<DdlIndexMetadata> mergeIndexes(
        Class<?> entityClass,
        EntEntityDescriptor meta,
        DdlNativeEntityModel nativeModel,
        Map<String, String> columnsByField,
        MetaDiagnosticCollector diagnostics
    ) {
        List<IndexCandidate> metaIndexes = new ArrayList<IndexCandidate>();
        if (meta != null) {
            for (EntIndexDescriptor index : meta.indexes()) {
                List<String> fields = new ArrayList<String>();
                for (String field : index.fields()) {
                    fields.add(columnsByField.containsKey(field) ? columnsByField.get(field) : field);
                }
                metaIndexes.add(new IndexCandidate(
                    sourced(index, MetaDescriptorProperties.INDEX_NAME, index.indexName()),
                    fields,
                    sourced(index, MetaDescriptorProperties.UNIQUE, Boolean.valueOf(index.unique())),
                    "",
                    true
                ));
            }
        }
        List<IndexCandidate> nativeIndexes = new ArrayList<IndexCandidate>();
        if (nativeModel != null) {
            for (DdlNativeIndexModel index : nativeModel.indexes()) {
                nativeIndexes.add(new IndexCandidate(index.name(), index.fields(), index.unique(), index.expression(), false));
            }
        }

        List<IndexCandidate> merged = new ArrayList<IndexCandidate>();
        boolean[] usedNative = new boolean[nativeIndexes.size()];
        for (IndexCandidate metaIndex : metaIndexes) {
            int nativePosition = findNativeIndex(metaIndex, nativeIndexes, usedNative);
            if (nativePosition < 0) {
                merged.add(metaIndex);
                continue;
            }
            IndexCandidate nativeIndex = nativeIndexes.get(nativePosition);
            usedNative[nativePosition] = true;
            warnIndexConflicts(entityClass, metaIndex, nativeIndex, diagnostics);
            merged.add(new IndexCandidate(
                choose("indexName", entityClass, null, nativeIndex.name, metaIndex.name,
                    SourcedValue.defaulted(""), diagnostics),
                nativeIndex.fields,
                choose("unique", entityClass, null, nativeIndex.unique, metaIndex.unique,
                    SourcedValue.defaulted(Boolean.FALSE), diagnostics),
                nativeIndex.expression,
                false
            ));
        }
        for (int i = 0; i < nativeIndexes.size(); i++) {
            if (!usedNative[i]) {
                merged.add(nativeIndexes.get(i));
            }
        }

        List<DdlIndexMetadata> result = new ArrayList<DdlIndexMetadata>();
        for (IndexCandidate index : merged) {
            try {
                result.add(new DdlIndexMetadata(
                    valueOrDefault(index.name, ""),
                    index.fields,
                    valueOrDefault(index.unique, Boolean.FALSE).booleanValue(),
                    index.expression == null ? "" : index.expression
                ));
            } catch (IllegalArgumentException exception) {
                diagnostics.add(MetaDiagnostic.error(MetaDiagnosticCode.UNKNOWN)
                    .entityClass(entityClass)
                    .property("index")
                    .location(entityClass.getName())
                    .message("DDL Adapter 无法构造索引元数据: " + exception.getMessage())
                    .build());
            }
        }
        return result;
    }

    private int findNativeIndex(IndexCandidate metaIndex, List<IndexCandidate> nativeIndexes, boolean[] used) {
        for (int i = 0; i < nativeIndexes.size(); i++) {
            if (used[i]) {
                continue;
            }
            IndexCandidate nativeIndex = nativeIndexes.get(i);
            String nativeName = valueOrDefault(nativeIndex.name, "");
            String metaName = valueOrDefault(metaIndex.name, "");
            if (!nativeName.isEmpty() && nativeName.equals(metaName)) {
                return i;
            }
            if (nativeName.isEmpty() || metaName.isEmpty()) {
                if (nativeIndex.fields.equals(metaIndex.fields) && nativeIndex.expression.equals(metaIndex.expression)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void warnIndexConflicts(
        Class<?> entityClass,
        IndexCandidate metaIndex,
        IndexCandidate nativeIndex,
        MetaDiagnosticCollector diagnostics
    ) {
        if (metaIndex.name.explicit() && nativeIndex.name.explicit()
            && !valueOrDefault(metaIndex.name, "").equals(valueOrDefault(nativeIndex.name, ""))) {
            warnConflict("indexName", entityClass, null, nativeIndex.name, metaIndex.name, diagnostics);
        }
        if (metaIndex.unique.explicit() && nativeIndex.unique.explicit()
            && !valueOrDefault(metaIndex.unique, Boolean.FALSE).equals(valueOrDefault(nativeIndex.unique, Boolean.FALSE))) {
            warnConflict("unique", entityClass, null, nativeIndex.unique, metaIndex.unique, diagnostics);
        }
        if (!nativeIndex.fields.equals(metaIndex.fields)) {
            diagnostics.add(MetaDiagnostic.warn(MetaDiagnosticCode.EXPLICIT_VALUE_CONFLICT)
                .entityClass(entityClass)
                .source(MetaValueSource.NATIVE_EXPLICIT)
                .relatedSource(MetaValueSource.META_EXPLICIT)
                .property("fields")
                .location(entityClass.getName())
                .message("DDL 与 Meta 索引字段不同，采用 DDL 字段: " + nativeIndex.fields)
                .build());
        }
    }

    private Map<String, EntFieldDescriptor> indexMetaFields(EntEntityDescriptor meta) {
        Map<String, EntFieldDescriptor> result = new LinkedHashMap<String, EntFieldDescriptor>();
        if (meta == null) {
            return result;
        }
        for (EntFieldDescriptor field : meta.fields()) {
            if (field != null && field.fieldName() != null) {
                result.put(field.fieldName(), field);
            }
        }
        return result;
    }

    private SourcedValue<Boolean> metaNullable(EntFieldDescriptor field) {
        if (field == null) {
            return null;
        }
        if (field.required() != null) {
            return sourced(field, MetaDescriptorProperties.REQUIRED, Boolean.valueOf(!field.required().booleanValue()));
        }
        if (isId(field)) {
            return sourced(field, MetaDescriptorProperties.FIELD_KIND, Boolean.FALSE);
        }
        return SourcedValue.inferred(Boolean.valueOf(!field.javaType().isPrimitive()));
    }

    private SourcedValue<Boolean> metaPrimaryKey(EntFieldDescriptor field) {
        if (field == null) {
            return null;
        }
        return isId(field) ? sourced(field, MetaDescriptorProperties.FIELD_KIND, Boolean.TRUE) : null;
    }

    private SourcedValue<GenerationStrategy> metaGenerationStrategy(EntFieldDescriptor field) {
        if (field == null || !isId(field)) {
            return null;
        }
        String generator = constraintValue(field, "id.generator");
        if ("AUTO".equals(generator)) {
            return sourcedConstraint(field, "id.generator", GenerationStrategy.AUTO_INCREMENT);
        }
        return null;
    }

    private SourcedValue<Integer> constraintInteger(EntFieldDescriptor field, String name) {
        String value = constraintValue(field, name);
        if (value == null) {
            return null;
        }
        try {
            return sourcedConstraint(field, name, Integer.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String constraintValue(EntFieldDescriptor field, String name) {
        if (field == null || field.constraints() == null) {
            return null;
        }
        for (EntFieldConstraintDescriptor constraint : field.constraints()) {
            if (constraint != null && name.equals(constraint.name())) {
                return constraint.value();
            }
        }
        return null;
    }

    private <T> SourcedValue<T> sourcedConstraint(EntFieldDescriptor field, String name, T value) {
        if (field != null) {
            for (EntFieldConstraintDescriptor constraint : field.constraints()) {
                if (constraint != null && name.equals(constraint.name())) {
                    return sourced(constraint, MetaDescriptorProperties.VALUE, value);
                }
            }
        }
        return SourcedValue.metaExplicit(value);
    }

    private SourcedValue<String> metaDescription(EntFieldDescriptor field) {
        if (field == null || field.description() == null || field.description().trim().isEmpty()) {
            return null;
        }
        return sourced(field, MetaDescriptorProperties.DESCRIPTION, field.description().trim());
    }

    private boolean isId(EntFieldDescriptor field) {
        return field != null && EntFieldKind.ID.name().equals(field.fieldKind());
    }

    private void warnConflict(
        String property,
        Class<?> entityClass,
        String field,
        SourcedValue<?> nativeValue,
        SourcedValue<?> metaValue,
        MetaDiagnosticCollector diagnostics
    ) {
        diagnostics.add(MetaDiagnostic.warn(MetaDiagnosticCode.EXPLICIT_VALUE_CONFLICT)
            .entityClass(entityClass)
            .field(field)
            .source(nativeValue.source())
            .relatedSource(metaValue.source())
            .property(property)
            .location(entityClass.getName() + (field == null ? "" : "#" + field))
            .message("DDL 与 Meta 显式值冲突，采用 DDL 值: " + nativeValue.value())
            .build());
    }

    private final <T> SourcedValue<T> choose(
        String property,
        Class<?> entityClass,
        String field,
        SourcedValue<T> first,
        SourcedValue<T> second,
        SourcedValue<T> third,
        MetaDiagnosticCollector diagnostics
    ) {
        List<SourcedValue<T>> values = new ArrayList<SourcedValue<T>>();
        add(values, first);
        add(values, second);
        add(values, third);
        for (int i = 0; i < values.size(); i++) {
            SourcedValue<T> left = values.get(i);
            if (!left.explicit()) {
                continue;
            }
            for (int j = i + 1; j < values.size(); j++) {
                SourcedValue<T> right = values.get(j);
                if (right.explicit() && !same(left.value(), right.value())) {
                    warnConflict(property, entityClass, field, left, right, diagnostics);
                }
            }
        }
        for (SourcedValue<T> value : values) {
            if (value.explicit()) {
                return value;
            }
        }
        for (SourcedValue<T> value : values) {
            if (value.value() != null) {
                return value;
            }
        }
        return SourcedValue.unknown(null);
    }

    private final <T> SourcedValue<T> choose(
        String property,
        Class<?> entityClass,
        String field,
        SourcedValue<T> first,
        SourcedValue<T> second,
        MetaDiagnosticCollector diagnostics
    ) {
        return choose(property, entityClass, field, first, second, null, diagnostics);
    }

    private <T> void add(List<SourcedValue<T>> values, SourcedValue<T> value) {
        if (value != null) {
            values.add(value);
        }
    }

    private <T> boolean same(T left, T right) {
        return left == null ? right == null : left.equals(right);
    }

    private <T> T valueOrDefault(SourcedValue<T> value, T fallback) {
        return value == null || value.value() == null ? fallback : value.value();
    }

    private <T> SourcedValue<T> sourced(Object descriptor, String property, T value) {
        SourcedValue<?> source = descriptor instanceof com.entloom.meta.contract.descriptor.SourcedDescriptor
            ? ((com.entloom.meta.contract.descriptor.SourcedDescriptor) descriptor).sourcedValue(property)
            : null;
        if (source == null) {
            return SourcedValue.metaExplicit(value);
        }
        return SourcedValue.of(value, source.source(), source.state(), source.explicit(), source.ruleId());
    }

    private String toSnake(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isUpperCase(current) && i > 0) {
                result.append('_');
            }
            result.append(Character.toLowerCase(current));
        }
        return result.toString();
    }

    private static final class IndexCandidate {
        private final SourcedValue<String> name;
        private final List<String> fields;
        private final SourcedValue<Boolean> unique;
        private final String expression;
        @SuppressWarnings("unused")
        private final boolean meta;

        private IndexCandidate(SourcedValue<String> name, List<String> fields,
                               SourcedValue<Boolean> unique, String expression, boolean meta) {
            this.name = name;
            this.fields = Collections.unmodifiableList(new ArrayList<String>(fields));
            this.unique = unique;
            this.expression = expression == null ? "" : expression;
            this.meta = meta;
        }
    }
}
