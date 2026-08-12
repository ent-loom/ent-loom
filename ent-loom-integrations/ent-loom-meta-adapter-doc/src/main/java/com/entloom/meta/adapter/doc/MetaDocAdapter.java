package com.entloom.meta.adapter.doc;

import com.entloom.doc.core.EntityDocCoreService;
import com.entloom.doc.core.model.DocEntityModel;
import com.entloom.doc.core.model.DocFieldModel;
import com.entloom.doc.core.model.DocRelationModel;
import com.entloom.doc.core.model.DocRuntimeProperties;
import com.entloom.doc.core.parser.DocNativeAnnotationParser;
import com.entloom.doc.core.spi.DocEntityMetaResolver;
import com.entloom.doc.core.spi.DocEntityIdentityResolver;
import com.entloom.doc.core.spi.DocIndexProvider;
import com.entloom.doc.core.spi.DocOverrideProvider;
import com.entloom.meta.adapter.doc.merge.DocRuntimeModelOverrideApplier;
import com.entloom.meta.adapter.doc.merge.DocRuntimeModelMerger;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.contract.descriptor.EntEntityDescriptor;
import com.entloom.meta.contract.diagnostic.DefaultMetaDiagnosticPolicy;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCollector;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticPolicy;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;
import com.entloom.meta.contract.value.SourcedValue;
import com.entloom.meta.core.parser.EntMetaParser;
import com.entloom.meta.core.parser.ReflectiveEntMetaParser;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adapts Meta descriptors to DOC runtime models and final compatibility maps.
 */
public class MetaDocAdapter {
    private final EntityDocCoreService docCoreService;
    private final DocEntityMetaResolver entityMetaResolver;
    private final DocNativeAnnotationParser nativeParser;
    private final DocRuntimeModelMerger merger;
    private final DocRuntimeModelOverrideApplier overrideApplier;
    private final MetaDiagnosticCollector diagnostics = new MetaDiagnosticCollector();
    private final List<DocEntityModel> models;
    private final Map<String, DocEntityModel> entityModels = new LinkedHashMap<String, DocEntityModel>();
    private final Map<String, Class<?>> registeredEntityClasses = new LinkedHashMap<String, Class<?>>();

    public MetaDocAdapter(DocEntityMetaResolver entityMetaResolver) {
        this(entityMetaResolver, Collections.<Class<?>>emptyList());
    }

    public MetaDocAdapter(DocEntityMetaResolver entityMetaResolver, EntMetaParser parser) {
        this(entityMetaResolver, Collections.<Class<?>>emptyList(), parser);
    }

    public MetaDocAdapter(DocEntityMetaResolver entityMetaResolver, Collection<Class<?>> entityClasses) {
        this(entityMetaResolver, entityClasses, new ReflectiveEntMetaParser());
    }

    public MetaDocAdapter(DocEntityMetaResolver entityMetaResolver, Collection<Class<?>> entityClasses, EntMetaParser parser) {
        this(entityMetaResolver, entityClasses, parser, DefaultMetaDiagnosticPolicy.failFast());
    }

    public MetaDocAdapter(
        DocEntityMetaResolver entityMetaResolver,
        Collection<Class<?>> entityClasses,
        EntMetaParser parser,
        MetaDiagnosticPolicy diagnosticPolicy
    ) {
        this(entityMetaResolver, DocIndexProvider.noop(), entityClasses, parser, new DocRuntimeModelMerger(), diagnosticPolicy);
    }

    public MetaDocAdapter(
        DocEntityMetaResolver entityMetaResolver,
        DocIndexProvider indexProvider,
        Collection<Class<?>> entityClasses,
        EntMetaParser parser,
        DocRuntimeModelMerger merger,
        MetaDiagnosticPolicy diagnosticPolicy
    ) {
        this(entityMetaResolver, indexProvider, entityClasses, parser, merger, DocOverrideProvider.noop(), diagnosticPolicy);
    }

    public MetaDocAdapter(
        DocEntityMetaResolver entityMetaResolver,
        DocIndexProvider indexProvider,
        Collection<Class<?>> entityClasses,
        EntMetaParser parser,
        DocRuntimeModelMerger merger,
        DocOverrideProvider overrideProvider,
        MetaDiagnosticPolicy diagnosticPolicy
    ) {
        if (entityMetaResolver == null) {
            throw new IllegalArgumentException("entityMetaResolver 不能为空");
        }
        if (parser == null) {
            throw new IllegalArgumentException("parser 不能为空");
        }
        if (merger == null) {
            throw new IllegalArgumentException("merger 不能为空");
        }
        this.entityMetaResolver = entityMetaResolver;
        this.nativeParser = new DocNativeAnnotationParser(entityMetaResolver, indexProvider);
        this.merger = merger;
        this.overrideApplier = new DocRuntimeModelOverrideApplier(overrideProvider);
        this.docCoreService = new EntityDocCoreService(entityMetaResolver, indexProvider);
        indexEntityClasses(entityClasses);
        List<DocEntityModel> parsedModels = parseAndMerge(entityClasses, parser);
        indexModels(parsedModels);
        this.models = withResolvedRelationTargetFields(parsedModels);
        entityModels.clear();
        indexModels(models);
        validateRelations(models);
        MetaDiagnosticPolicy policy = diagnosticPolicy == null ? DefaultMetaDiagnosticPolicy.failFast() : diagnosticPolicy;
        policy.evaluate(diagnostics.diagnostics());
    }

    public Map<String, Object> buildOne(Class<?> entityClass) {
        if (entityClass == null) {
            return null;
        }
        DocEntityModel model = entityModels.get(entityClass.getName().toLowerCase(Locale.ROOT));
        if (model == null) {
            List<DocEntityModel> parsed = parseAndMerge(Collections.<Class<?>>singletonList(entityClass), new ReflectiveEntMetaParser());
            if (parsed.isEmpty()) {
                return null;
            }
            model = parsed.get(0);
        }
        return docCoreService.buildOne(model);
    }

    public List<Map<String, Object>> buildAll(Collection<Class<?>> entityClasses) {
        if (entityClasses == null || entityClasses.isEmpty()) {
            return docCoreService.buildAllModels(models);
        }
        List<DocEntityModel> selected = new ArrayList<DocEntityModel>();
        for (Class<?> entityClass : entityClasses) {
            if (entityClass == null) {
                continue;
            }
            DocEntityModel model = entityModels.get(entityClass.getName().toLowerCase(Locale.ROOT));
            if (model != null) {
                selected.add(model);
            } else {
                List<DocEntityModel> parsed = parseAndMerge(Collections.<Class<?>>singletonList(entityClass), new ReflectiveEntMetaParser());
                selected.addAll(parsed);
            }
        }
        return docCoreService.buildAllModels(selected);
    }

    public List<DocEntityModel> models() {
        return models;
    }

    public List<MetaDiagnostic> diagnostics() {
        return diagnostics.diagnostics();
    }

    private List<DocEntityModel> parseAndMerge(Collection<Class<?>> entityClasses, EntMetaParser parser) {
        List<DocEntityModel> mergedModels = new ArrayList<DocEntityModel>();
        if (entityClasses == null) {
            return mergedModels;
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
            MetaDiagnosticResult<DocEntityModel> nativeResult = nativeParser.parseWithDiagnostics(entityClass);
            diagnostics.addAll(nativeResult.diagnostics());
            SourcedValue<String> inferredTable = SourcedValue.inferred(entityMetaResolver.resolveTableName(entityClass, null));
            MetaDiagnosticResult<DocEntityModel> merged = merger.merge(entityClass, metaDescriptor, nativeResult.value(), inferredTable);
            diagnostics.addAll(merged.diagnostics());
            if (merged.value() != null) {
                mergedModels.add(overrideApplier.apply(withResolvedColumns(merged.value()), diagnostics));
            }
        }
        return mergedModels;
    }

    private DocEntityModel withResolvedColumns(DocEntityModel model) {
        if (model == null || model.fields().isEmpty()) {
            return model;
        }
        List<DocFieldModel> fields = new ArrayList<DocFieldModel>();
        for (DocFieldModel field : model.fields()) {
            if (field == null) {
                continue;
            }
            fields.add(withResolvedColumn(model.entityClass(), field));
        }
        return new DocEntityModel(
            model.entityClass(),
            model.resourceCode(),
            model.entityName(),
            model.description(),
            model.tableName(),
            model.group(),
            model.remark(),
            model.hidden(),
            model.visibleFor(),
            fields,
            model.relations(),
            model.indexes()
        );
    }

    private DocFieldModel withResolvedColumn(Class<?> entityClass, DocFieldModel field) {
        if (field == null || field.column().value() != null) {
            return field;
        }
        String column = entityMetaResolver.resolveColumn(entityClass, field.property());
        if (column == null || column.trim().isEmpty()) {
            return field;
        }
        return new DocFieldModel(
            field.property(),
            field.javaType(),
            SourcedValue.inferred(column),
            field.name(),
            field.description(),
            field.example(),
            field.examples(),
            field.required(),
            field.readOnly(),
            field.maxLength(),
            field.minLength(),
            field.fieldKind(),
            field.role(),
            field.createDefaultValue(),
            field.group(),
            field.remark(),
            field.hidden(),
            field.visibleFor(),
            field.constraints()
        );
    }

    private void indexModels(List<DocEntityModel> docModels) {
        for (DocEntityModel model : docModels) {
            registerModel(model.resourceCode().value(), model);
            registerModel(model.entityName().value(), model);
            registerModel(model.entityClass().getSimpleName(), model);
            registerModel(model.entityClass().getName(), model);
        }
    }

    private void validateRelations(List<DocEntityModel> docModels) {
        for (DocEntityModel model : docModels) {
            for (DocRelationModel relation : model.relations()) {
                if (relation == null) {
                    continue;
                }
                if (!hasField(model.entityClass(), relation.sourceField().value())) {
                    diagnostics.add(MetaDiagnostic.error(MetaDiagnosticCode.RELATION_SOURCE_FIELD_NOT_FOUND)
                        .entity(model.resourceCode().value())
                        .entityClass(model.entityClass())
                        .field(relation.sourceField().value())
                        .source(relation.sourceField().source())
                        .property(DocRuntimeProperties.SOURCE_FIELD)
                        .location(model.entityClass().getName() + "#" + relation.sourceField().value())
                        .message("DOC adapter 解析不到关系 sourceField: " + relation.sourceField().value())
                        .build());
                    continue;
                }
                DocEntityModel targetModel = resolveTargetModel(relation.targetEntity().value());
                Class<?> targetClass = targetModel == null ? resolveTargetClass(relation.targetEntity().value()) : targetModel.entityClass();
                if (targetClass == null) {
                    diagnostics.add(MetaDiagnostic.error(MetaDiagnosticCode.RELATION_TARGET_ENTITY_NOT_FOUND)
                        .entity(model.resourceCode().value())
                        .entityClass(model.entityClass())
                        .field(relation.sourceField().value())
                        .source(relation.targetEntity().source())
                        .property(DocRuntimeProperties.TARGET_ENTITY)
                        .location(model.entityClass().getName() + "#" + relation.sourceField().value())
                        .message("DOC adapter 解析不到已注册关系目标实体: " + relation.targetEntity().value())
                        .build());
                    continue;
                }
                if (!hasField(targetClass, relation.targetField().value())) {
                    diagnostics.add(MetaDiagnostic.error(MetaDiagnosticCode.RELATION_TARGET_FIELD_NOT_FOUND)
                        .entity(model.resourceCode().value())
                        .entityClass(model.entityClass())
                        .field(relation.sourceField().value())
                        .source(relation.targetField().source())
                        .property(DocRuntimeProperties.TARGET_FIELD)
                        .location(targetClass.getName() + "#" + relation.targetField().value())
                        .message("DOC adapter 解析不到关系 targetField: " + relation.targetField().value())
                        .build());
                }
            }
        }
    }

    private List<DocEntityModel> withResolvedRelationTargetFields(List<DocEntityModel> docModels) {
        if (docModels == null || docModels.isEmpty() || !(entityMetaResolver instanceof DocEntityIdentityResolver)) {
            return docModels;
        }
        List<DocEntityModel> resolved = new ArrayList<DocEntityModel>();
        for (DocEntityModel model : docModels) {
            if (model == null || model.relations().isEmpty()) {
                resolved.add(model);
                continue;
            }
            List<DocRelationModel> relations = new ArrayList<DocRelationModel>();
            boolean changed = false;
            for (DocRelationModel relation : model.relations()) {
                DocRelationModel resolvedRelation = withResolvedRelationTargetField(relation);
                relations.add(resolvedRelation);
                changed = changed || resolvedRelation != relation;
            }
            resolved.add(changed ? withRelations(model, relations) : model);
        }
        return resolved;
    }

    private DocRelationModel withResolvedRelationTargetField(DocRelationModel relation) {
        if (relation == null || relation.targetField().explicit() || !"id".equals(relation.targetField().value())) {
            return relation;
        }
        Class<?> targetClass = resolveTargetClass(relation.targetEntity().value());
        if (targetClass == null) {
            return relation;
        }
        String idField = ((DocEntityIdentityResolver) entityMetaResolver).resolveIdField(targetClass);
        if (idField == null || idField.trim().isEmpty() || "id".equals(idField.trim()) || !hasField(targetClass, idField.trim())) {
            return relation;
        }
        return new DocRelationModel(
            relation.relationField(),
            relation.targetService(),
            relation.targetEntity(),
            relation.sourceField(),
            SourcedValue.inferred(idField.trim()),
            relation.cardinality(),
            relation.ownerSide(),
            relation.resolutionStatus(),
            relation.targetEntityLabel(),
            relation.relationRemark(),
            relation.sourceFieldInferred()
        );
    }

    private DocEntityModel withRelations(DocEntityModel model, List<DocRelationModel> relations) {
        return new DocEntityModel(
            model.entityClass(),
            model.resourceCode(),
            model.entityName(),
            model.description(),
            model.tableName(),
            model.group(),
            model.remark(),
            model.hidden(),
            model.visibleFor(),
            model.fields(),
            relations,
            model.indexes()
        );
    }

    private boolean hasField(Class<?> type, String fieldName) {
        if (type == null || fieldName == null || fieldName.trim().isEmpty()) {
            return false;
        }
        Class<?> current = type;
        while (current != null && current != Object.class) {
            Field[] fields = current.getDeclaredFields();
            for (Field field : fields) {
                if (fieldName.equals(field.getName())) {
                    return true;
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private DocEntityModel resolveTargetModel(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        return entityModels.get(code.trim().toLowerCase(Locale.ROOT));
    }

    private Class<?> resolveTargetClass(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        Class<?> entityClass = registeredEntityClasses.get(code.trim().toLowerCase(Locale.ROOT));
        if (entityClass != null) {
            return entityClass;
        }
        DocEntityModel model = resolveTargetModel(code);
        if (model != null) {
            return model.entityClass();
        }
        if (entityMetaResolver instanceof DocEntityIdentityResolver) {
            return ((DocEntityIdentityResolver) entityMetaResolver).resolveEntityClass(code.trim());
        }
        return null;
    }

    private void indexEntityClasses(Collection<Class<?>> entityClasses) {
        if (entityClasses == null || entityClasses.isEmpty()) {
            return;
        }
        for (Class<?> entityClass : entityClasses) {
            registerEntityClass(entityClass);
        }
    }

    private void registerEntityClass(Class<?> entityClass) {
        if (entityClass == null) {
            return;
        }
        registerEntityClass(entityClass.getSimpleName(), entityClass);
        registerEntityClass(entityClass.getName(), entityClass);
    }

    private void registerEntityClass(String code, Class<?> entityClass) {
        if (code == null || code.trim().isEmpty() || entityClass == null) {
            return;
        }
        registeredEntityClasses.put(code.trim().toLowerCase(Locale.ROOT), entityClass);
    }

    private void registerModel(String code, DocEntityModel model) {
        if (code == null || code.trim().isEmpty() || model == null) {
            return;
        }
        entityModels.put(code.trim().toLowerCase(Locale.ROOT), model);
        registerEntityClass(code, model.entityClass());
    }
}
