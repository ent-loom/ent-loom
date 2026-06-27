package com.entloom.meta.adapter.crud.parser;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.annotations.EntCrudField;
import com.entloom.crud.api.enums.JoinType;
import com.entloom.crud.core.util.NamingUtils;
import com.entloom.crud.enums.RelationScope;
import com.entloom.meta.adapter.crud.model.CrudNativeEntityModel;
import com.entloom.meta.adapter.crud.model.CrudNativeFieldModel;
import com.entloom.meta.adapter.crud.model.CrudNativeRelationModel;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;
import com.entloom.meta.enums.RelationCardinality;
import com.entloom.meta.contract.value.SourcedValue;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 解析 CRUD 原生注解为中间模型，不直接注册 runtime。
 */
public class CrudNativeAnnotationParser {

    public MetaDiagnosticResult<CrudNativeEntityModel> parseWithDiagnostics(Class<?> entityClass) {
        if (entityClass == null) {
            return MetaDiagnosticResult.of(null, java.util.Collections.emptyList());
        }
        EntCrudEntity entity = entityClass.getAnnotation(EntCrudEntity.class);
        if (entity == null) {
            return MetaDiagnosticResult.of(null, java.util.Collections.emptyList());
        }
        List<CrudNativeFieldModel> fields = new ArrayList<CrudNativeFieldModel>();
        List<CrudNativeRelationModel> relations = new ArrayList<CrudNativeRelationModel>();
        for (Field field : getAllFields(entityClass)) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            EntCrudField relation = field.getAnnotation(EntCrudField.class);
            if (relation != null) {
                relations.add(toRelationModel(field, relation));
            }
            if (isPersistentField(field)) {
                fields.add(toFieldModel(field));
            }
        }
        return MetaDiagnosticResult.of(
            new CrudNativeEntityModel(
                entityClass,
                stringExplicitOrInferred(entity.name(), entityClass.getSimpleName()),
                stringExplicitOrInferred(entity.table(), defaultTable(entityClass)),
                "id".equals(entity.idField()) ? SourcedValue.unknown("id") : SourcedValue.nativeExplicit(entity.idField()),
                stringExplicitOrUnknown(entity.logicDeleteField()),
                stringExplicitOrUnknown(entity.ownerService()),
                fields,
                relations
            ),
            java.util.Collections.emptyList()
        );
    }

    private CrudNativeFieldModel toFieldModel(Field field) {
        return new CrudNativeFieldModel(
            field.getName(),
            field.getType(),
            SourcedValue.inferred(NamingUtils.camelToSnake(field.getName())),
            SourcedValue.inferred(Boolean.valueOf(!field.getType().isPrimitive()))
        );
    }

    private CrudNativeRelationModel toRelationModel(Field field, EntCrudField relation) {
        Class<?> targetClass = relation.targetClass();
        SourcedValue<Class<?>> sourcedTargetClass = targetClass == null || Void.class.equals(targetClass)
            ? SourcedValue.unknown(null)
            : SourcedValue.nativeExplicit(targetClass);
        SourcedValue<String> targetEntity = stringExplicitOrUnknown(relation.targetEntity());
        if (!targetEntity.explicit() && sourcedTargetClass.value() != null) {
            targetEntity = SourcedValue.inferred(sourcedTargetClass.value().getSimpleName());
        }
        return new CrudNativeRelationModel(
            field.getName(),
            stringExplicitOrUnknown(relation.targetService()),
            targetEntity,
            sourcedTargetClass,
            isBlank(relation.sourceField()) ? SourcedValue.inferred(field.getName()) : SourcedValue.nativeExplicit(relation.sourceField()),
            "id".equals(relation.targetField()) ? SourcedValue.unknown("id") : SourcedValue.nativeExplicit(relation.targetField()),
            relation.cardinality() == RelationCardinality.MANY_TO_ONE
                ? SourcedValue.unknown(RelationCardinality.MANY_TO_ONE)
                : SourcedValue.nativeExplicit(relation.cardinality()),
            relation.scope() == RelationScope.LOCAL_DB
                ? SourcedValue.unknown(RelationScope.LOCAL_DB)
                : SourcedValue.nativeExplicit(relation.scope()),
            relation.joinType() == JoinType.LEFT
                ? SourcedValue.unknown(JoinType.LEFT)
                : SourcedValue.nativeExplicit(relation.joinType())
        );
    }

    private List<Field> getAllFields(Class<?> entityClass) {
        List<Field> fields = new ArrayList<Field>();
        Class<?> current = entityClass;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private boolean isPersistentField(Field field) {
        return !Collection.class.isAssignableFrom(field.getType())
            && !java.util.Map.class.isAssignableFrom(field.getType())
            && field.getType().getAnnotation(EntCrudEntity.class) == null;
    }

    private SourcedValue<String> stringExplicitOrInferred(String raw, String inferred) {
        return isBlank(raw) ? SourcedValue.inferred(inferred) : SourcedValue.nativeExplicit(raw.trim());
    }

    private SourcedValue<String> stringExplicitOrUnknown(String raw) {
        return isBlank(raw) ? SourcedValue.unknown(null) : SourcedValue.nativeExplicit(raw.trim());
    }

    private String defaultTable(Class<?> entityClass) {
        String simpleName = entityClass.getSimpleName();
        if (simpleName.endsWith("Entity")) {
            simpleName = simpleName.substring(0, simpleName.length() - "Entity".length());
        }
        return NamingUtils.camelToSnake(simpleName);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
