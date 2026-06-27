package com.entloom.meta.adapter.crud;

import com.entloom.crud.api.enums.JoinType;
import com.entloom.crud.core.adapter.ResourceCatalogAdapter;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.runtime.model.CrudRuntimeModel;
import com.entloom.meta.adapter.crud.merge.CrudRuntimeModelMerger;
import com.entloom.meta.adapter.crud.model.CrudEntityRuntimeModel;
import com.entloom.meta.adapter.crud.model.CrudFieldRuntimeModel;
import com.entloom.meta.adapter.crud.model.CrudNativeEntityModel;
import com.entloom.meta.adapter.crud.model.CrudRelationRuntimeModel;
import com.entloom.meta.adapter.crud.model.CrudRuntimeProperties;
import com.entloom.meta.adapter.crud.parser.CrudNativeAnnotationParser;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.meta.EntMetaId;
import com.entloom.meta.contract.descriptor.EntEntityDescriptor;
import com.entloom.meta.contract.descriptor.MetaDescriptorProperties;
import com.entloom.meta.contract.diagnostic.DefaultMetaDiagnosticPolicy;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCollector;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticPolicy;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;
import com.entloom.meta.core.parser.EntMetaParser;
import com.entloom.meta.core.parser.ReflectiveEntMetaParser;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Adapts Meta descriptors to CRUD runtime metadata.
 */
public class MetaCrudAdapter implements ResourceCatalogAdapter {
    private final List<EntityMeta> entityMetas = new ArrayList<EntityMeta>();
    private final List<RelationEdge> relationEdges = new ArrayList<RelationEdge>();
    private final Map<String, Class<?>> entityTypes = new LinkedHashMap<String, Class<?>>();
    private final Set<Class<?>> registeredEntityTypes = new LinkedHashSet<Class<?>>();
    private final MetaDiagnosticCollector diagnostics = new MetaDiagnosticCollector();
    private final CrudNativeAnnotationParser nativeParser;
    private final CrudRuntimeModelMerger merger;
    private CrudRuntimeModel runtimeModel;

    public MetaCrudAdapter(Collection<Class<?>> entityClasses) {
        this(entityClasses, new ReflectiveEntMetaParser());
    }

    public MetaCrudAdapter(Collection<Class<?>> entityClasses, EntMetaParser parser) {
        this(entityClasses, parser, DefaultMetaDiagnosticPolicy.failFast());
    }

    public MetaCrudAdapter(Collection<Class<?>> entityClasses, EntMetaParser parser, MetaDiagnosticPolicy diagnosticPolicy) {
        this(entityClasses, parser, new CrudNativeAnnotationParser(), new CrudRuntimeModelMerger(), diagnosticPolicy);
    }

    public MetaCrudAdapter(
        Collection<Class<?>> entityClasses,
        EntMetaParser parser,
        CrudNativeAnnotationParser nativeParser,
        CrudRuntimeModelMerger merger,
        MetaDiagnosticPolicy diagnosticPolicy
    ) {
        if (parser == null) {
            throw new IllegalArgumentException("parser 不能为空");
        }
        if (nativeParser == null) {
            throw new IllegalArgumentException("nativeParser 不能为空");
        }
        if (merger == null) {
            throw new IllegalArgumentException("merger 不能为空");
        }
        this.nativeParser = nativeParser;
        this.merger = merger;
        MetaDiagnosticPolicy policy = diagnosticPolicy == null ? DefaultMetaDiagnosticPolicy.failFast() : diagnosticPolicy;
        List<CrudEntityRuntimeModel> models = parseAndMerge(entityClasses, parser);
        indexTypes(models);
        for (CrudEntityRuntimeModel model : models) {
            entityMetas.add(toEntityMeta(model));
        }
        for (CrudEntityRuntimeModel model : models) {
            relationEdges.addAll(toRelationEdges(model));
        }
        this.runtimeModel = CrudRuntimeModel.from(entityMetas, relationEdges);
        policy.evaluate(diagnostics.diagnostics());
    }

    @Override
    public List<MetaDiagnostic> diagnostics() {
        return diagnostics.diagnostics();
    }

    @Override
    public CrudRuntimeModel runtimeModel() {
        return runtimeModel;
    }

    private List<CrudEntityRuntimeModel> parseAndMerge(Collection<Class<?>> entityClasses, EntMetaParser parser) {
        List<CrudEntityRuntimeModel> models = new ArrayList<CrudEntityRuntimeModel>();
        if (entityClasses == null) {
            return models;
        }
        for (Class<?> entityClass : entityClasses) {
            if (entityClass == null) {
                continue;
            }
            EntEntityDescriptor metaDescriptor = null;
            if (entityClass.getAnnotation(EntEntity.class) != null) {
                MetaDiagnosticResult<EntEntityDescriptor> metaResult = parser.parseWithDiagnostics(entityClass);
                diagnostics.addAll(metaResult.diagnostics());
                metaDescriptor = metaResult.value();
            }
            MetaDiagnosticResult<CrudNativeEntityModel> nativeResult = nativeParser.parseWithDiagnostics(entityClass);
            diagnostics.addAll(nativeResult.diagnostics());
            MetaDiagnosticResult<CrudEntityRuntimeModel> merged = merger.merge(entityClass, metaDescriptor, nativeResult.value());
            diagnostics.addAll(merged.diagnostics());
            if (merged.value() != null) {
                models.add(merged.value());
            }
        }
        return models;
    }

    private void indexTypes(List<CrudEntityRuntimeModel> models) {
        for (CrudEntityRuntimeModel model : models) {
            Class<?> entityClass = model.entityClass();
            registeredEntityTypes.add(entityClass);
            registerEntityType(model.resourceCode().value(), entityClass);
            registerEntityType(entityClass.getSimpleName(), entityClass);
            registerEntityType(entityClass.getName(), entityClass);
        }
    }

    private EntityMeta toEntityMeta(CrudEntityRuntimeModel model) {
        Class<?> entityClass = model.entityClass();
        Map<String, EntityFieldMeta> fieldMetas = new LinkedHashMap<String, EntityFieldMeta>();
        for (CrudFieldRuntimeModel field : model.fields()) {
            Class<?> javaType = field.javaType() == null ? Object.class : field.javaType();
            fieldMetas.put(
                field.fieldName(),
                new EntityFieldMeta(
                    field.fieldName(),
                    javaType,
                    field.columnName().value(),
                    field.nullable().value() == null || field.nullable().value().booleanValue(),
                    field.relation(),
                    true,
                    true
                )
            );
        }
        return new EntityMeta(
            entityClass,
            new ResourceDescriptor(entityClass, model.resourceCode().value(), model.ownerService().value(), aliases(entityClass)),
            model.table().value(),
            model.idField().value(),
            resolveIdPolicy(entityClass, model.idField().value()),
            model.logicDeleteField().value(),
            fieldMetas
        );
    }

    private EntityIdPolicy resolveIdPolicy(Class<?> entityClass, String idField) {
        Field field = findField(entityClass, idField);
        if (field == null) {
            return EntityIdPolicy.EXPLICIT;
        }
        EntityIdPolicy metaPolicy = resolveEntMetaIdPolicy(field);
        if (metaPolicy != null) {
            return metaPolicy;
        }
        EntityIdPolicy persistencePolicy = resolvePersistenceIdPolicy(field);
        return persistencePolicy == null ? EntityIdPolicy.EXPLICIT : persistencePolicy;
    }

    private EntityIdPolicy resolveEntMetaIdPolicy(Field field) {
        EntMetaId id = field.getAnnotation(EntMetaId.class);
        if (id == null) {
            return null;
        }
        EntMetaId.IdGenerator generator = id.generator();
        if (generator == EntMetaId.IdGenerator.UNSET) {
            return null;
        }
        return EntityIdPolicy.APPLICATION;
    }

    private EntityIdPolicy resolvePersistenceIdPolicy(Field field) {
        for (Annotation annotation : field.getAnnotations()) {
            String annotationName = annotation.annotationType().getName();
            if (isGeneratedValueAnnotation(annotationName)) {
                return EntityIdPolicy.GENERATED;
            }
            if (isMybatisTableIdAnnotation(annotationName)) {
                String idType = enumAttributeName(annotation, "type");
                if ("AUTO".equals(idType)) {
                    return EntityIdPolicy.GENERATED;
                }
                if ("INPUT".equals(idType)) {
                    return EntityIdPolicy.EXPLICIT;
                }
            }
        }
        return null;
    }

    private boolean isGeneratedValueAnnotation(String annotationName) {
        return "javax.persistence.GeneratedValue".equals(annotationName)
            || "jakarta.persistence.GeneratedValue".equals(annotationName);
    }

    private boolean isMybatisTableIdAnnotation(String annotationName) {
        return "com.baomidou.mybatisplus.annotations.TableId".equals(annotationName)
            || "com.baomidou.mybatisplus.annotation.TableId".equals(annotationName);
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

    private List<RelationEdge> toRelationEdges(CrudEntityRuntimeModel model) {
        List<RelationEdge> edges = new ArrayList<RelationEdge>();
        for (CrudRelationRuntimeModel relation : model.relations()) {
            Class<?> targetClass = resolveRelationTargetClass(relation);
            if (targetClass == null || !registeredEntityTypes.contains(targetClass)) {
                boolean explicitTargetClass = relation.targetClass().value() != null;
                diagnostics.add(MetaDiagnostic.error(MetaDiagnosticCode.RELATION_TARGET_ENTITY_NOT_FOUND)
                    .entity(model.resourceCode().value())
                    .entityClass(model.entityClass())
                    .field(relation.sourceField().value())
                    .source(explicitTargetClass ? relation.targetClass().source() : relation.targetEntity().source())
                    .property(explicitTargetClass ? CrudRuntimeProperties.TARGET_CLASS : CrudRuntimeProperties.TARGET_ENTITY)
                    .location(model.entityClass().getName() + "#" + relation.sourceField().value())
                    .message("CRUD adapter 解析不到已注册关系目标实体: "
                        + (targetClass == null ? relation.targetEntity().value() : targetClass.getName()))
                    .build());
                continue;
            }
            if (!hasField(model.entityClass(), relation.sourceField().value())) {
                diagnostics.add(MetaDiagnostic.error(MetaDiagnosticCode.RELATION_SOURCE_FIELD_NOT_FOUND)
                    .entity(model.resourceCode().value())
                    .entityClass(model.entityClass())
                    .field(relation.sourceField().value())
                    .source(relation.sourceField().source())
                    .property(CrudRuntimeProperties.SOURCE_FIELD)
                    .location(model.entityClass().getName() + "#" + relation.sourceField().value())
                    .message("CRUD adapter 解析不到关系 sourceField: " + relation.sourceField().value())
                    .build());
                continue;
            }
            if (!hasField(targetClass, relation.targetField().value())) {
                diagnostics.add(MetaDiagnostic.error(MetaDiagnosticCode.RELATION_TARGET_FIELD_NOT_FOUND)
                    .entity(model.resourceCode().value())
                    .entityClass(model.entityClass())
                    .field(relation.sourceField().value())
                    .source(relation.targetField().source())
                    .property(CrudRuntimeProperties.TARGET_FIELD)
                    .location(targetClass.getName() + "#" + relation.targetField().value())
                    .message("CRUD adapter 解析不到关系 targetField: " + relation.targetField().value())
                    .build());
                continue;
            }
            RelationEdge edge = new RelationEdge();
            edge.setFromEntity(model.entityClass());
            edge.setToEntity(targetClass);
            edge.setRelationField(relation.relationField());
            edge.setFromField(relation.sourceField().value());
            edge.setToField(relation.targetField().value());
            edge.setScope(relation.scope().value());
            edge.setJoinKind(relation.joinType().value());
            edge.setCardinality(relation.cardinality().value());
            edges.add(edge);
        }
        return edges;
    }

    private boolean hasField(Class<?> type, String fieldName) {
        return findField(type, fieldName) != null;
    }

    private Field findField(Class<?> type, String fieldName) {
        if (type == null || fieldName == null || fieldName.trim().isEmpty()) {
            return null;
        }
        for (Field field : getAllFields(type)) {
            if (fieldName.equals(field.getName())) {
                return field;
            }
        }
        return null;
    }

    private List<Field> getAllFields(Class<?> type) {
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

    private Set<String> aliases(Class<?> entityClass) {
        LinkedHashSet<String> aliases = new LinkedHashSet<String>();
        aliases.add(entityClass.getSimpleName());
        aliases.add(entityClass.getName());
        return aliases;
    }

    private Class<?> resolveEntityType(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        return entityTypes.get(code.trim().toLowerCase(Locale.ROOT));
    }

    private Class<?> resolveRelationTargetClass(CrudRelationRuntimeModel relation) {
        return relation.targetClass().value() == null
            ? resolveEntityType(relation.targetEntity().value())
            : relation.targetClass().value();
    }

    private void registerEntityType(String code, Class<?> entityClass) {
        if (code == null || code.trim().isEmpty() || entityClass == null) {
            return;
        }
        entityTypes.put(code.trim().toLowerCase(Locale.ROOT), entityClass);
    }

}
