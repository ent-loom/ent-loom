package com.entloom.crud.core.runtime.model.parser;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.annotations.EntCrudExportField;
import com.entloom.crud.annotations.EntCrudField;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.runtime.model.CrudRuntimeModel;
import com.entloom.crud.core.util.NamingUtils;
import com.entloom.meta.enums.RelationCardinality;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 解析 CRUD native 注解为统一运行时模型。
 */
public class CrudNativeRuntimeModelParser {
    public CrudRuntimeModel parse(Collection<Class<?>> entityClasses) {
        List<EntityMeta> entityMetas = new ArrayList<EntityMeta>();
        List<RelationEdge> relationEdges = new ArrayList<RelationEdge>();
        if (entityClasses != null) {
            for (Class<?> entityClass : entityClasses) {
                if (entityClass == null) {
                    continue;
                }
                ParsedEntity parsed = parseEntity(entityClass);
                entityMetas.add(parsed.entityMeta);
                relationEdges.addAll(parsed.relationEdges);
            }
        }
        return CrudRuntimeModel.from(entityMetas, relationEdges);
    }

    private ParsedEntity parseEntity(Class<?> entityClass) {
        EntCrudEntity entity = entityClass.getAnnotation(EntCrudEntity.class);
        if (entity == null) {
            throw new ValidationException("缺少 @EntCrudEntity 注解: " + entityClass.getName());
        }

        Map<String, EntityFieldMeta> fieldMetas = new LinkedHashMap<String, EntityFieldMeta>();
        List<RelationEdge> relationEdges = new ArrayList<RelationEdge>();
        String idField = trimToDefault(entity.idField(), "id");
        for (Field field : entityClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            EntCrudField relation = field.getAnnotation(EntCrudField.class);
            if (relation != null) {
                relationEdges.add(toRelationEdge(entityClass, field, relation, idField));
            }
            if (isPersistentField(field)) {
                fieldMetas.put(field.getName(), toFieldMeta(field));
            }
        }

        ResourceDescriptor descriptor = new ResourceDescriptor(
            entityClass,
            trimToDefault(entity.name(), entityClass.getSimpleName()),
            trimToNull(entity.ownerService()),
            resourceAliases(entityClass)
        );
        EntityMeta entityMeta = new EntityMeta(
            entityClass,
            descriptor,
            trimToDefault(entity.table(), defaultTable(entityClass)),
            idField,
            trimToNull(entity.logicDeleteField()),
            fieldMetas
        );
        return new ParsedEntity(entityMeta, relationEdges);
    }

    private EntityFieldMeta toFieldMeta(Field field) {
        EntCrudExportField exportField = field.getAnnotation(EntCrudExportField.class);
        return new EntityFieldMeta(
            field.getName(),
            field.getType(),
            NamingUtils.camelToSnake(field.getName()),
            !field.getType().isPrimitive(),
            false,
            true,
            true,
            exportField == null ? null : Boolean.valueOf(exportField.exportable()),
            exportField == null ? null : Boolean.valueOf(exportField.defaultVisible()),
            exportField == null ? null : exportField.label(),
            exportField == null ? null : exportField.format(),
            exportField == null ? null : exportField.dictionaryCode(),
            exportField == null ? null : exportField.displayField()
        );
    }

    private RelationEdge toRelationEdge(
        Class<?> entityClass,
        Field field,
        EntCrudField relation,
        String idField
    ) {
        Class<?> targetEntity = relation.targetClass();
        if (targetEntity == null || Void.class.equals(targetEntity)) {
            throw new ValidationException("@EntCrudField.targetClass 不能为空: "
                + entityClass.getName() + "." + field.getName());
        }
        RelationCardinality cardinality = relation.cardinality();
        RelationEdge edge = new RelationEdge();
        edge.setFromEntity(entityClass);
        edge.setToEntity(targetEntity);
        edge.setRelationField(field.getName());
        edge.setFromField(resolveSourceField(field, relation, cardinality, idField));
        edge.setToField(trimToDefault(relation.targetField(), "id"));
        edge.setScope(relation.scope());
        edge.setCardinality(cardinality);
        edge.setJoinKind(relation.joinType());
        return edge;
    }

    private String resolveSourceField(
        Field field,
        EntCrudField relation,
        RelationCardinality cardinality,
        String idField
    ) {
        String explicit = trimToNull(relation.sourceField());
        if (explicit != null) {
            return explicit;
        }
        if (cardinality == RelationCardinality.ONE_TO_MANY && idField != null && !idField.trim().isEmpty()) {
            return idField.trim();
        }
        return field.getName();
    }

    private boolean isPersistentField(Field field) {
        return !Collection.class.isAssignableFrom(field.getType())
            && !java.util.Map.class.isAssignableFrom(field.getType())
            && field.getType().getAnnotation(EntCrudEntity.class) == null;
    }

    private Set<String> resourceAliases(Class<?> entityClass) {
        LinkedHashSet<String> aliases = new LinkedHashSet<String>();
        aliases.add(entityClass.getSimpleName());
        aliases.add(entityClass.getName());
        return aliases;
    }

    private String defaultTable(Class<?> entityClass) {
        String simpleName = entityClass.getSimpleName();
        if (simpleName.endsWith("Entity")) {
            simpleName = simpleName.substring(0, simpleName.length() - "Entity".length());
        }
        return NamingUtils.camelToSnake(simpleName);
    }

    private String trimToDefault(String value, String defaultValue) {
        String normalized = trimToNull(value);
        return normalized == null ? defaultValue : normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class ParsedEntity {
        private final EntityMeta entityMeta;
        private final List<RelationEdge> relationEdges;

        private ParsedEntity(EntityMeta entityMeta, List<RelationEdge> relationEdges) {
            this.entityMeta = entityMeta;
            this.relationEdges = relationEdges;
        }
    }
}
