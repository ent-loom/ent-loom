package com.entloom.crud.core.runtime.model.parser;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.annotations.EntCrudExportField;
import com.entloom.crud.annotations.EntCrudField;
import com.entloom.crud.api.enums.CrudIdPolicy;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.runtime.model.CrudRuntimeModel;
import com.entloom.crud.core.runtime.model.input.CrudNativeAnnotationParser;
import com.entloom.crud.core.runtime.model.input.CrudNativeEntityModel;
import com.entloom.crud.core.runtime.model.input.CrudNativeFieldModel;
import com.entloom.crud.core.util.NamingUtils;
import com.entloom.crud.core.convention.CrudConvention;
import com.entloom.meta.contract.diagnostic.DefaultMetaDiagnosticPolicy;
import com.entloom.meta.enums.RelationCardinality;
import java.lang.reflect.Field;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
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
    private final CrudNativeAnnotationParser annotationParser;

    public CrudNativeRuntimeModelParser() {
        this(java.util.Collections.<CrudConvention>emptyList());
    }

    public CrudNativeRuntimeModelParser(Collection<? extends CrudConvention> conventions) {
        this.annotationParser = new CrudNativeAnnotationParser(conventions);
    }

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
        com.entloom.meta.contract.diagnostic.MetaDiagnosticResult<CrudNativeEntityModel> nativeResult =
            annotationParser.parseWithDiagnostics(entityClass);
        DefaultMetaDiagnosticPolicy.failFast().evaluate(nativeResult.diagnostics());
        CrudNativeEntityModel nativeModel = nativeResult.value();

        Map<String, EntityFieldMeta> fieldMetas = new LinkedHashMap<String, EntityFieldMeta>();
        List<RelationEdge> relationEdges = new ArrayList<RelationEdge>();
        String idField = trimToDefault(entity.idField(), "id");
        Set<String> declaredFieldNames = new LinkedHashSet<String>();
        for (Field field : getAllFields(entityClass)) {
            if (Modifier.isStatic(field.getModifiers())
                || Modifier.isTransient(field.getModifiers())
                || field.isSynthetic()) {
                continue;
            }
            // 子类字段优先，避免 Java 字段隐藏时父类同名字段覆盖子类元数据。
            if (!declaredFieldNames.add(field.getName())) {
                continue;
            }
            EntCrudField relation = field.getAnnotation(EntCrudField.class);
            if (relation != null) {
                relationEdges.add(toRelationEdge(entityClass, field, relation, idField));
            }
            if (isPersistentField(field)) {
                fieldMetas.put(
                    field.getName(),
                    toFieldMeta(field, findFieldModel(nativeModel, field.getName()), contains(entity.scopeFields(), field.getName()))
                );
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
            resolveIdPolicy(entityClass, entity, idField),
            trimToNull(entity.logicDeleteField()),
            fieldMetas
        );
        return new ParsedEntity(entityMeta, relationEdges);
    }

    private EntityFieldMeta toFieldMeta(Field field, CrudNativeFieldModel nativeField, boolean scopeField) {
        EntCrudExportField exportField = field.getAnnotation(EntCrudExportField.class);
        return new EntityFieldMeta(
            field.getName(),
            field.getType(),
            NamingUtils.camelToSnake(field.getName()),
            !field.getType().isPrimitive(),
            false,
            true,
            true,
            !scopeField && (nativeField == null || nativeField.writable() == null || nativeField.writable().value() == null
                || nativeField.writable().value().booleanValue()),
            scopeField,
            false,
            exportField == null ? null : Boolean.valueOf(exportField.exportable()),
            exportField == null ? null : Boolean.valueOf(exportField.defaultVisible()),
            exportField == null ? null : exportField.label(),
            exportField == null ? null : exportField.format(),
            exportField == null ? null : exportField.dictionaryCode(),
            exportField == null ? null : exportField.displayField()
        );
    }

    private boolean contains(String[] fields, String fieldName) {
        if (fields == null || fieldName == null) {
            return false;
        }
        for (String field : fields) {
            if (fieldName.equals(field == null ? null : field.trim())) {
                return true;
            }
        }
        return false;
    }

    private CrudNativeFieldModel findFieldModel(CrudNativeEntityModel model, String fieldName) {
        if (model == null) {
            return null;
        }
        for (CrudNativeFieldModel field : model.fields()) {
            if (fieldName.equals(field.fieldName())) {
                return field;
            }
        }
        return null;
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

    private EntityIdPolicy resolveIdPolicy(Class<?> entityClass, EntCrudEntity entity, String idField) {
        CrudIdPolicy configured = entity.idPolicy();
        if (configured != null && configured != CrudIdPolicy.UNSET) {
            return toEntityIdPolicy(configured);
        }
        Field id = findField(entityClass, idField);
        if (id == null) {
            return EntityIdPolicy.EXPLICIT;
        }
        for (Annotation annotation : id.getAnnotations()) {
            String annotationName = annotation.annotationType().getName();
            if (isGeneratedValueAnnotation(annotation)) {
                return EntityIdPolicy.GENERATED;
            }
            if (isMybatisAutoIdAnnotation(annotation)) {
                return EntityIdPolicy.GENERATED;
            }
            if (isDdlGeneratedIdAnnotation(annotation)) {
                return EntityIdPolicy.GENERATED;
            }
            if ("com.entloom.meta.annotations.meta.EntMetaId".equals(annotationName)) {
                String generator = enumAttributeName(annotation, "generator");
                if (generator != null && !"UNSET".equals(generator)) {
                    return EntityIdPolicy.APPLICATION;
                }
            }
        }
        return EntityIdPolicy.EXPLICIT;
    }

    private EntityIdPolicy toEntityIdPolicy(CrudIdPolicy policy) {
        switch (policy) {
            case EXPLICIT:
                return EntityIdPolicy.EXPLICIT;
            case GENERATED:
                return EntityIdPolicy.GENERATED;
            case APPLICATION:
                return EntityIdPolicy.APPLICATION;
            case COMPOSITE:
                return EntityIdPolicy.COMPOSITE;
            case UNSET:
            default:
                return EntityIdPolicy.EXPLICIT;
        }
    }

    private boolean isGeneratedValueAnnotation(Annotation annotation) {
        String annotationName = annotation.annotationType().getName();
        if (!"javax.persistence.GeneratedValue".equals(annotationName)
            && !"jakarta.persistence.GeneratedValue".equals(annotationName)) {
            return false;
        }
        String strategy = enumAttributeName(annotation, "strategy");
        return "IDENTITY".equals(strategy);
    }

    private boolean isMybatisAutoIdAnnotation(Annotation annotation) {
        String annotationName = annotation.annotationType().getName();
        if (!"com.baomidou.mybatisplus.annotations.TableId".equals(annotationName)
            && !"com.baomidou.mybatisplus.annotation.TableId".equals(annotationName)) {
            return false;
        }
        String type = enumAttributeName(annotation, "type");
        return "AUTO".equals(type);
    }

    private boolean isDdlGeneratedIdAnnotation(Annotation annotation) {
        if (!"com.entloom.ddl.annotations.EntDbField".equals(annotation.annotationType().getName())) {
            return false;
        }
        String strategy = enumAttributeName(annotation, "generationStrategy");
        return "AUTO_INCREMENT".equals(strategy) || "IDENTITY".equals(strategy);
    }

    private String enumAttributeName(Annotation annotation, String attributeName) {
        try {
            Method method = annotation.annotationType().getMethod(attributeName);
            Object value = method.invoke(annotation);
            return value instanceof Enum<?> ? ((Enum<?>) value).name() : null;
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
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
