package com.entloom.meta.adapter.ddl;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.ddl.annotations.EntDbEntity;
import com.entloom.ddl.annotations.EntDbField;
import com.entloom.ddl.annotations.EntDbIndex;
import com.entloom.ddl.enums.DdlTableSize;
import com.entloom.ddl.enums.GenerationStrategy;
import com.entloom.ddl.enums.IndexType;
import com.entloom.ddl.enums.NamingStrategy;
import com.entloom.ddl.enums.UniqueScope;
import com.entloom.ddl.enums.SqlType;
import com.entloom.ddl.enums.WritePolicy;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCollector;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.contract.value.SourcedValue;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读取 DDL 原生注解；该解析器不依赖 Spring，供 Meta DDL Adapter 使用。
 */
final class DdlNativeAnnotationParser {
    DdlNativeEntityModel parse(Class<?> entityClass, MetaDiagnosticCollector diagnostics) {
        EntDbEntity entity = entityClass.getAnnotation(EntDbEntity.class);
        if (entity == null) {
            return null;
        }
        Map<String, DdlNativeFieldModel> fields = new LinkedHashMap<String, DdlNativeFieldModel>();
        for (Field field : allFields(entityClass)) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())
                || field.isSynthetic()) {
                continue;
            }
            fields.put(field.getName(), toField(entityClass, field, diagnostics));
        }
        List<DdlNativeIndexModel> indexes = new ArrayList<DdlNativeIndexModel>();
        for (EntDbIndex index : entityClass.getAnnotationsByType(EntDbIndex.class)) {
            indexes.add(toIndex(entityClass, index, fields, null, diagnostics));
        }
        for (Field field : allFields(entityClass)) {
            for (EntDbIndex index : field.getAnnotationsByType(EntDbIndex.class)) {
                indexes.add(toIndex(entityClass, index, fields, field.getName(), diagnostics));
            }
        }
        return new DdlNativeEntityModel(
            blankAsNull(entity.table()) == null
                ? SourcedValue.inferred(entity.namingStrategy() == NamingStrategy.AS_IS
                    ? entityClass.getSimpleName()
                    : toSnake(entityClass.getSimpleName()))
                : nativeValue(entity.table().trim()),
            blankAsNull(entity.schema()) == null
                ? SourcedValue.unknown(null)
                : nativeValue(entity.schema().trim()),
            blankAsNull(entity.comment()) == null
                ? SourcedValue.unknown(null)
                : nativeValue(entity.comment().trim()),
            entity.size() == DdlTableSize.UNSET
                ? SourcedValue.unknown(null)
                : nativeValue(entity.size()),
            fields,
            indexes
        );
    }

    private DdlNativeFieldModel toField(Class<?> entityClass, Field field, MetaDiagnosticCollector diagnostics) {
        EntDbField annotation = field.getAnnotation(EntDbField.class);
        if (annotation != null) {
            warnUnsupported(entityClass, field.getName(), "sqlType", annotation.sqlType() != SqlType.AUTO, diagnostics);
            warnUnsupported(entityClass, field.getName(), "collation", blankAsNull(annotation.collation()) != null, diagnostics);
            warnUnsupported(entityClass, field.getName(), "dialectOptions", blankAsNull(annotation.dialectOptions()) != null, diagnostics);
            warnUnsupported(entityClass, field.getName(), "defaultValueHint",
                annotation.defaultValueHint() != com.entloom.base.util.value.TypedValueType.UNSET, diagnostics);
            warnUnsupported(entityClass, field.getName(), "writePolicy",
                annotation.writePolicy() != WritePolicy.READ_WRITE, diagnostics);
        }
        boolean inferredPrimaryKey = "id".equals(field.getName());
        String column = annotation == null ? null : blankAsNull(annotation.column());
        String definition = annotation == null ? null : blankAsNull(annotation.columnDefinition());
        Boolean nullable = annotation == null || annotation.nullable() == OptionalBoolean.UNSET
            ? Boolean.valueOf(!field.getType().isPrimitive())
            : Boolean.valueOf(annotation.nullable() == OptionalBoolean.TRUE);
        Boolean unique = annotation == null || annotation.unique() == OptionalBoolean.UNSET
            ? Boolean.FALSE
            : Boolean.valueOf(annotation.unique() == OptionalBoolean.TRUE);
        Boolean persisted = annotation == null || annotation.persisted() == OptionalBoolean.UNSET
            ? Boolean.TRUE
            : Boolean.valueOf(annotation.persisted() == OptionalBoolean.TRUE);
        Boolean primaryKey = annotation == null || annotation.primaryKey() == OptionalBoolean.UNSET
            ? Boolean.valueOf(inferredPrimaryKey)
            : Boolean.valueOf(annotation.primaryKey() == OptionalBoolean.TRUE);
        if (primaryKey.booleanValue() && (annotation == null || annotation.nullable() != OptionalBoolean.TRUE)) {
            nullable = Boolean.FALSE;
        }
        return new DdlNativeFieldModel(
            field.getName(),
            field.getType(),
            column == null ? SourcedValue.inferred(toSnake(field.getName())) : nativeValue(column),
            definition == null ? SourcedValue.unknown(null) : nativeValue(definition),
            annotation == null || annotation.nullable() == OptionalBoolean.UNSET
                ? SourcedValue.inferred(nullable)
                : nativeValue(nullable),
            annotation == null || annotation.unique() == OptionalBoolean.UNSET
                ? SourcedValue.defaulted(unique)
                : nativeValue(unique),
            annotation == null || annotation.persisted() == OptionalBoolean.UNSET
                ? SourcedValue.defaulted(persisted)
                : nativeValue(persisted),
            annotation == null || annotation.primaryKey() == OptionalBoolean.UNSET
                ? SourcedValue.inferred(primaryKey)
                : nativeValue(primaryKey),
            annotation == null || annotation.length() < 0 ? SourcedValue.unknown(null) : nativeValue(annotation.length()),
            annotation == null || annotation.precision() < 0 ? SourcedValue.unknown(null) : nativeValue(annotation.precision()),
            annotation == null || annotation.scale() < 0 ? SourcedValue.unknown(null) : nativeValue(annotation.scale()),
            annotation == null || blankAsNull(annotation.defaultValue()) == null
                ? SourcedValue.unknown(null)
                : nativeValue(annotation.defaultValue().trim()),
            annotation == null || blankAsNull(annotation.comment()) == null
                ? SourcedValue.unknown(null)
                : nativeValue(annotation.comment().trim()),
            annotation == null || blankAsNull(annotation.renameFrom()) == null
                ? SourcedValue.unknown(null)
                : nativeValue(annotation.renameFrom().trim()),
            annotation == null || annotation.generationStrategy() == GenerationStrategy.UNSET
                ? SourcedValue.unknown(null)
                : nativeValue(annotation.generationStrategy())
        );
    }

    private DdlNativeIndexModel toIndex(Class<?> entityClass,
                                        EntDbIndex annotation,
                                        Map<String, DdlNativeFieldModel> fields,
                                        String fieldName,
                                        MetaDiagnosticCollector diagnostics) {
        warnUnsupported(entityClass, null, "uniqueScope", annotation.uniqueScope() != UniqueScope.ALL_ROWS, diagnostics);
        warnUnsupported(entityClass, null, "type", annotation.type() != IndexType.BTREE, diagnostics);
        List<String> indexFields = new ArrayList<String>();
        for (String field : annotation.fields()) {
            if (field != null && !field.trim().isEmpty()) {
                String value = field.trim();
                DdlNativeFieldModel nativeField = fields.get(value);
                indexFields.add(nativeField == null ? value : nativeField.columnName().value());
            }
        }
        if (indexFields.isEmpty() && blankAsNull(annotation.expression()) == null && fieldName != null) {
            DdlNativeFieldModel nativeField = fields.get(fieldName);
            indexFields.add(nativeField == null ? toSnake(fieldName) : nativeField.columnName().value());
        }
        return new DdlNativeIndexModel(
            blankAsNull(annotation.name()) == null ? SourcedValue.unknown(null) : nativeValue(annotation.name().trim()),
            indexFields,
            annotation.unique() == OptionalBoolean.UNSET
                ? SourcedValue.defaulted(Boolean.FALSE)
                : nativeValue(Boolean.valueOf(annotation.unique() == OptionalBoolean.TRUE)),
            blankAsNull(annotation.expression())
        );
    }

    private void warnUnsupported(Class<?> entityClass,
                                 String fieldName,
                                 String property,
                                 boolean explicit,
                                 MetaDiagnosticCollector diagnostics) {
        if (!explicit || diagnostics == null) {
            return;
        }
        diagnostics.add(MetaDiagnostic.warn(MetaDiagnosticCode.CONSUMER_UNSUPPORTED_PROPERTY)
            .entityClass(entityClass)
            .field(fieldName)
            .source(MetaValueSource.NATIVE_EXPLICIT)
            .property(property)
            .location(entityClass.getName() + (fieldName == null ? "" : "#" + fieldName))
            .message("DDL Adapter 当前不承接原生属性 " + property + "，已保留诊断并继续使用可表达的 DDL 模型")
            .build());
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<Field>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static <T> SourcedValue<T> nativeValue(T value) {
        return SourcedValue.nativeExplicit(value);
    }

    private static String blankAsNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static String toSnake(String value) {
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
}
