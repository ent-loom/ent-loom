package com.entloom.doc.core.parser;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.doc.annotations.EntDocEntity;
import com.entloom.doc.annotations.EntDocField;
import com.entloom.doc.core.model.DocEntityModel;
import com.entloom.doc.core.model.DocFieldModel;
import com.entloom.doc.core.model.DocIndexModel;
import com.entloom.doc.core.model.DocRelationModel;
import com.entloom.doc.core.spi.DocEntityMetaResolver;
import com.entloom.doc.core.spi.DocIndexProvider;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;
import com.entloom.meta.enums.RelationCardinality;
import com.entloom.meta.contract.enums.RelationOwnerSide;
import com.entloom.meta.contract.enums.RelationResolutionStatus;
import com.entloom.meta.contract.value.SourcedValue;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Parses DOC native annotations into a stable runtime model.
 */
public class DocNativeAnnotationParser {
    private final DocEntityMetaResolver entityMetaResolver;
    private final DocIndexProvider indexProvider;

    public DocNativeAnnotationParser(DocEntityMetaResolver entityMetaResolver, DocIndexProvider indexProvider) {
        this.entityMetaResolver = entityMetaResolver;
        this.indexProvider = indexProvider == null ? DocIndexProvider.noop() : indexProvider;
    }

    public MetaDiagnosticResult<DocEntityModel> parseWithDiagnostics(Class<?> entityClass) {
        return parseWithDiagnostics(entityClass, null, null);
    }

    public MetaDiagnosticResult<DocEntityModel> parseWithDiagnostics(Class<?> entityClass, String resourceCode, String configuredTableName) {
        if (entityClass == null) {
            return MetaDiagnosticResult.of(null, Collections.emptyList());
        }
        EntDocEntity entity = entityClass.getAnnotation(EntDocEntity.class);
        if (entity == null && !hasDocFields(entityClass)) {
            return MetaDiagnosticResult.of(null, Collections.emptyList());
        }
        String tableName = entityMetaResolver == null ? configuredTableName : entityMetaResolver.resolveTableName(entityClass, configuredTableName);
        return MetaDiagnosticResult.of(
            new DocEntityModel(
                entityClass,
                stringExplicitOrInferred(resourceCode, entityClass.getSimpleName()),
                entity == null ? SourcedValue.inferred(entityClass.getSimpleName()) : stringExplicitOrInferred(entity.name(), entityClass.getSimpleName()),
                entity == null ? SourcedValue.unknown(null) : stringExplicitOrUnknown(entity.description()),
                stringExplicitOrUnknown(tableName),
                buildFields(entityClass),
                buildRelations(entityClass),
                buildIndexes(tableName)
            ),
            Collections.emptyList()
        );
    }

    private List<DocFieldModel> buildFields(Class<?> entityClass) {
        List<DocFieldModel> fields = new ArrayList<DocFieldModel>();
        LinkedHashSet<String> dedup = new LinkedHashSet<String>();
        for (Field field : getAllFields(entityClass)) {
            if (field == null || Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            EntDocField fieldDoc = field.getAnnotation(EntDocField.class);
            if (fieldDoc == null || !dedup.add(field.getName())) {
                continue;
            }
            String column = entityMetaResolver == null ? null : entityMetaResolver.resolveColumn(entityClass, field.getName());
            fields.add(new DocFieldModel(
                field.getName(),
                field.getType(),
                stringExplicitOrUnknown(column),
                stringExplicitOrInferred(fieldDoc.name(), field.getName()),
                stringExplicitOrUnknown(fieldDoc.description()),
                stringExplicitOrUnknown(fieldDoc.example()),
                exampleList(fieldDoc.example()),
                optionalBoolean(fieldDoc.required()),
                SourcedValue.unknown(null),
                intExplicitOrUnknown(fieldDoc.maxLength()),
                intExplicitOrUnknown(fieldDoc.minLength()),
                SourcedValue.unknown(null),
                SourcedValue.unknown(null),
                SourcedValue.unknown(null),
                Collections.<com.entloom.doc.core.model.DocFieldConstraintModel>emptyList()
            ));
        }
        if (!fields.isEmpty()) {
            return fields;
        }
        for (Field field : getAllFields(entityClass)) {
            if (field == null || Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            if (Collection.class.isAssignableFrom(field.getType()) || Map.class.isAssignableFrom(field.getType())) {
                continue;
            }
            String column = entityMetaResolver == null ? null : entityMetaResolver.resolveColumn(entityClass, field.getName());
            if (isBlank(column)) {
                continue;
            }
            fields.add(new DocFieldModel(
                field.getName(),
                field.getType(),
                SourcedValue.inferred(column),
                SourcedValue.inferred(field.getName()),
                SourcedValue.defaulted(field.getName()),
                SourcedValue.defaulted(""),
                Collections.<String>emptyList(),
                SourcedValue.defaulted(Boolean.FALSE),
                SourcedValue.defaulted(Boolean.FALSE),
                SourcedValue.defaulted(Integer.valueOf(-1)),
                SourcedValue.defaulted(Integer.valueOf(-1)),
                SourcedValue.unknown(null),
                SourcedValue.unknown(null),
                SourcedValue.unknown(null),
                Collections.<com.entloom.doc.core.model.DocFieldConstraintModel>emptyList()
            ));
        }
        return fields;
    }

    private List<DocRelationModel> buildRelations(Class<?> entityClass) {
        List<DocRelationModel> relations = new ArrayList<DocRelationModel>();
        LinkedHashSet<String> dedup = new LinkedHashSet<String>();
        for (Field field : getAllFields(entityClass)) {
            if (field == null || Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            EntDocField fieldDoc = field.getAnnotation(EntDocField.class);
            if (fieldDoc == null || !hasRelation(fieldDoc) || !dedup.add(field.getName())) {
                continue;
            }
            boolean sourceInferred = isBlank(fieldDoc.sourceField());
            RelationCardinality cardinality = fieldDoc.cardinality();
            relations.add(new DocRelationModel(
                field.getName(),
                stringExplicitOrUnknown(fieldDoc.targetService()),
                stringExplicitOrUnknown(fieldDoc.targetEntity()),
                sourceInferred ? SourcedValue.inferred(field.getName()) : SourcedValue.nativeExplicit(fieldDoc.sourceField()),
                "id".equals(fieldDoc.targetField()) ? SourcedValue.unknown("id") : SourcedValue.nativeExplicit(fieldDoc.targetField()),
                cardinality == RelationCardinality.MANY_TO_ONE
                    ? SourcedValue.unknown(RelationCardinality.MANY_TO_ONE)
                    : SourcedValue.nativeExplicit(cardinality),
                SourcedValue.inferred(ownerSide(cardinality)),
                SourcedValue.inferred(RelationResolutionStatus.RESOLVED),
                stringExplicitOrUnknown(fieldDoc.targetEntityLabel()),
                stringExplicitOrUnknown(fieldDoc.relationRemark()),
                sourceInferred
            ));
        }
        return relations;
    }

    private boolean hasDocFields(Class<?> entityClass) {
        for (Field field : getAllFields(entityClass)) {
            if (field != null && field.getAnnotation(EntDocField.class) != null) {
                return true;
            }
        }
        return false;
    }

    private List<DocIndexModel> buildIndexes(String tableName) {
        List<Map<String, Object>> rawIndexes = indexProvider.queryIndexes(tableName);
        if (rawIndexes == null || rawIndexes.isEmpty()) {
            return Collections.emptyList();
        }
        List<DocIndexModel> indexes = new ArrayList<DocIndexModel>();
        for (Map<String, Object> rawIndex : rawIndexes) {
            if (rawIndex == null) {
                continue;
            }
            indexes.add(new DocIndexModel(
                stringExplicitOrUnknown(toStringValue(rawIndex.get("name"))),
                toStringList(rawIndex.get("fields")),
                SourcedValue.nativeExplicit(Boolean.valueOf(Boolean.TRUE.equals(toBoolean(rawIndex.get("unique")))))
            ));
        }
        return indexes;
    }

    private boolean hasRelation(EntDocField fieldDoc) {
        return !isBlank(fieldDoc.targetService())
            || !isBlank(fieldDoc.targetEntity())
            || !isBlank(fieldDoc.sourceField())
            || !"id".equals(fieldDoc.targetField())
            || fieldDoc.cardinality() != RelationCardinality.MANY_TO_ONE
            || !isBlank(fieldDoc.targetEntityLabel())
            || !isBlank(fieldDoc.relationRemark());
    }

    private List<Field> getAllFields(Class<?> entityClass) {
        List<Field> fields = new ArrayList<Field>();
        Class<?> current = entityClass;
        while (current != null && current != Object.class) {
            Field[] declaredFields = current.getDeclaredFields();
            for (Field field : declaredFields) {
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private SourcedValue<Boolean> optionalBoolean(OptionalBoolean value) {
        if (value == OptionalBoolean.TRUE) {
            return SourcedValue.nativeExplicit(Boolean.TRUE);
        }
        if (value == OptionalBoolean.FALSE) {
            return SourcedValue.nativeExplicit(Boolean.FALSE);
        }
        return SourcedValue.unknown(null);
    }

    private SourcedValue<Integer> intExplicitOrUnknown(int value) {
        return value < 0 ? SourcedValue.unknown(null) : SourcedValue.nativeExplicit(Integer.valueOf(value));
    }

    private SourcedValue<String> stringExplicitOrInferred(String raw, String inferred) {
        return isBlank(raw) ? SourcedValue.inferred(inferred) : SourcedValue.nativeExplicit(raw.trim());
    }

    private SourcedValue<String> stringExplicitOrUnknown(String raw) {
        return isBlank(raw) ? SourcedValue.unknown(null) : SourcedValue.nativeExplicit(raw.trim());
    }

    private List<String> exampleList(String example) {
        if (isBlank(example)) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<String>();
        values.add(example.trim());
        return values;
    }

    private RelationOwnerSide ownerSide(RelationCardinality cardinality) {
        if (cardinality == RelationCardinality.ONE_TO_MANY) {
            return RelationOwnerSide.TARGET_ENTITY;
        }
        if (cardinality == RelationCardinality.MANY_TO_MANY) {
            return RelationOwnerSide.UNKNOWN;
        }
        return RelationOwnerSide.DECLARING_ENTITY;
    }

    private String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<String> toStringList(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<String>();
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                if (item != null) {
                    values.add(String.valueOf(item));
                }
            }
            return values;
        }
        values.add(String.valueOf(value));
        return values;
    }

    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return Boolean.valueOf(((Number) value).intValue() != 0);
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
