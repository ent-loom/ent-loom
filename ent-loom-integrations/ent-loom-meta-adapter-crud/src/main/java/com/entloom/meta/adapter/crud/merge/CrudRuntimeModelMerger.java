package com.entloom.meta.adapter.crud.merge;

import com.entloom.crud.api.enums.JoinType;
import com.entloom.crud.core.util.NamingUtils;
import com.entloom.crud.enums.RelationScope;
import com.entloom.crud.core.convention.CrudConventionProperties;
import com.entloom.meta.adapter.crud.model.CrudEntityRuntimeModel;
import com.entloom.meta.adapter.crud.model.CrudFieldRuntimeModel;
import com.entloom.crud.core.runtime.model.input.CrudNativeEntityModel;
import com.entloom.crud.core.runtime.model.input.CrudNativeFieldModel;
import com.entloom.crud.core.runtime.model.input.CrudNativeRelationModel;
import com.entloom.meta.contract.contribution.Contribution;
import com.entloom.meta.contract.contribution.Priority;
import com.entloom.meta.contract.contribution.PropertyContributionResolver;
import com.entloom.meta.adapter.crud.model.CrudRelationRuntimeModel;
import com.entloom.meta.adapter.crud.model.CrudRuntimeProperties;
import com.entloom.meta.contract.descriptor.EntEntityDescriptor;
import com.entloom.meta.contract.descriptor.EntFieldDescriptor;
import com.entloom.meta.contract.descriptor.EntRelationDescriptor;
import com.entloom.meta.contract.descriptor.MetaDescriptorProperties;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCollector;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;
import com.entloom.meta.enums.RelationCardinality;
import com.entloom.meta.contract.enums.RelationOwnerSide;
import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.contract.value.SourcedValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * CRUD P0 最小合并器。
 */
public class CrudRuntimeModelMerger {
    private final PropertyContributionResolver contributionResolver = new PropertyContributionResolver();

    public MetaDiagnosticResult<CrudEntityRuntimeModel> merge(
        Class<?> entityClass,
        EntEntityDescriptor meta,
        CrudNativeEntityModel nativeModel
    ) {
        if (entityClass == null) {
            return MetaDiagnosticResult.of(null, java.util.Collections.emptyList());
        }
        if (meta == null && nativeModel == null) {
            return MetaDiagnosticResult.of(null, java.util.Collections.emptyList());
        }
        MetaDiagnosticCollector diagnostics = new MetaDiagnosticCollector();
        SourcedValue<String> idField = resolveProperty(
            CrudRuntimeProperties.ID_FIELD,
            entityClass,
            null,
            diagnostics,
            nativeModel == null ? null : nativeModel.idField(),
            meta == null ? null : SourcedValue.metaExplicit(resolveIdField(meta)),
            SourcedValue.defaulted("id")
        );
        CrudEntityRuntimeModel model = new CrudEntityRuntimeModel(
            entityClass,
            resolveProperty(CrudRuntimeProperties.RESOURCE_CODE, entityClass, null, diagnostics, nativeModel == null ? null : nativeModel.resourceCode(),
                meta == null ? null : SourcedValue.metaExplicit(meta.entityName())),
            resolveProperty(CrudRuntimeProperties.TABLE, entityClass, null, diagnostics, nativeModel == null ? null : nativeModel.table(),
                SourcedValue.inferred(defaultTable(entityClass))),
            idField,
            resolveProperty(CrudRuntimeProperties.LOGIC_DELETE_FIELD, entityClass, null, diagnostics, nativeModel == null ? null : nativeModel.logicDeleteField(),
                SourcedValue.defaulted("")),
            resolveProperty(CrudRuntimeProperties.OWNER_SERVICE, entityClass, null, diagnostics, nativeModel == null ? null : nativeModel.ownerService(),
                meta == null ? null : stringMeta(meta.serviceName())),
            mergeFields(entityClass, meta, nativeModel, diagnostics),
            mergeRelations(entityClass, meta, nativeModel, idField.value(), diagnostics)
        );
        return MetaDiagnosticResult.of(model, diagnostics.diagnostics());
    }

    private List<CrudFieldRuntimeModel> mergeFields(
        Class<?> entityClass,
        EntEntityDescriptor meta,
        CrudNativeEntityModel nativeModel,
        MetaDiagnosticCollector diagnostics
    ) {
        LinkedHashSet<String> fieldNames = new LinkedHashSet<String>();
        Map<String, EntFieldDescriptor> metaFields = new LinkedHashMap<String, EntFieldDescriptor>();
        if (meta != null) {
            for (EntFieldDescriptor field : meta.fields()) {
                metaFields.put(field.fieldName(), field);
                fieldNames.add(field.fieldName());
            }
        }
        Map<String, CrudNativeFieldModel> nativeFields = new LinkedHashMap<String, CrudNativeFieldModel>();
        if (nativeModel != null) {
            for (CrudNativeFieldModel field : nativeModel.fields()) {
                nativeFields.put(field.fieldName(), field);
                fieldNames.add(field.fieldName());
            }
        }
        List<CrudFieldRuntimeModel> fields = new ArrayList<CrudFieldRuntimeModel>();
        for (String fieldName : fieldNames) {
            EntFieldDescriptor metaField = metaFields.get(fieldName);
            CrudNativeFieldModel nativeField = nativeFields.get(fieldName);
            collectUnsupportedMetaProperties(entityClass, metaField, diagnostics);
            Class<?> javaType = metaField != null && metaField.javaType() != null
                ? metaField.javaType()
                : nativeField == null ? Object.class : nativeField.javaType();
            SourcedValue<String> column = nativeField == null ? null : nativeField.columnName();
            if (column == null) {
                column = SourcedValue.inferred(NamingUtils.camelToSnake(fieldName));
            }
            SourcedValue<Boolean> nullable = metaField == null
                ? nativeField == null ? SourcedValue.inferred(Boolean.TRUE) : nativeField.nullable()
                : SourcedValue.metaExplicit(Boolean.valueOf(metaField.required() == null || !metaField.required().booleanValue()));
            SourcedValue<Boolean> writable = resolveWritable(entityClass, fieldName, metaField, nativeField, diagnostics);
            fields.add(new CrudFieldRuntimeModel(
                fieldName,
                javaType,
                column,
                nullable,
                false,
                true,
                true,
                writable.value() == null || writable.value().booleanValue(),
                false,
                false
            ));
        }
        return fields;
    }

    private SourcedValue<Boolean> resolveWritable(
        Class<?> entityClass,
        String fieldName,
        EntFieldDescriptor metaField,
        CrudNativeFieldModel nativeField,
        MetaDiagnosticCollector diagnostics
    ) {
        String target = entityClass.getName() + "#" + fieldName;
        List<Contribution<?>> contributions = new ArrayList<Contribution<?>>();
        if (nativeField != null) {
            addContribution(
                contributions,
                target,
                CrudConventionProperties.WRITABLE,
                nativeField.writable(),
                "crud.native." + fieldName + ".writable"
            );
        }
        if (metaField != null) {
            com.entloom.meta.contract.value.SourcedValue<?> readOnly = metaField.sourcedValue(
                MetaDescriptorProperties.READ_ONLY
            );
            if (readOnly == null && metaField.readOnly() != null) {
                readOnly = SourcedValue.metaExplicit(metaField.readOnly());
            }
            if (readOnly != null && readOnly.value() != null) {
                addContribution(
                    contributions,
                    target,
                    CrudConventionProperties.WRITABLE,
                    Boolean.valueOf(!Boolean.TRUE.equals(readOnly.value())),
                    "meta." + fieldName + ".readOnly",
                    readOnly.source()
                );
            }
        }
        if (contributions.isEmpty()) {
            addContribution(
                contributions,
                target,
                CrudConventionProperties.WRITABLE,
                Boolean.TRUE,
                "crud.default." + fieldName + ".writable",
                MetaValueSource.INFERRED
            );
        }
        com.entloom.meta.contract.diagnostic.MetaDiagnosticResult<Map<String, Contribution<?>>> result =
            contributionResolver.resolve(contributions);
        diagnostics.addAll(result.diagnostics());
        Contribution<?> winner = result.value().get(target + "." + CrudConventionProperties.WRITABLE);
        if (winner == null) {
            return SourcedValue.inferred(Boolean.TRUE);
        }
        return SourcedValue.of(
            (Boolean) winner.value(),
            winner.source(),
            state(winner.source()),
            isExplicit(winner.source()),
            winner.ruleId().value()
        );
    }

    private void addContribution(
        List<Contribution<?>> contributions,
        String target,
        String property,
        SourcedValue<?> value,
        String ruleId
    ) {
        if (value != null) {
            addContribution(
                contributions,
                target,
                property,
                value.value(),
                isBlank(value.ruleId()) ? ruleId : value.ruleId(),
                value.source()
            );
        }
    }

    private void addContribution(
        List<Contribution<?>> contributions,
        String target,
        String property,
        Object value,
        String ruleId,
        MetaValueSource source
    ) {
        if (value == null || source == null) {
            return;
        }
        Priority priority = Priority.fromSource(source);
        if (priority == null) {
            return;
        }
        contributions.add(Contribution.builder()
            .target(target)
            .property(property)
            .value(value)
            .source(source)
            .ruleId(ruleId)
            .priority(priority)
            .build());
    }

    private void collectUnsupportedMetaProperties(
        Class<?> entityClass,
        EntFieldDescriptor metaField,
        MetaDiagnosticCollector diagnostics
    ) {
        if (metaField == null || metaField.role() == null || !metaField.role().startsWith("DATETIME.")) {
            return;
        }
        if ("DATETIME.CREATED_TIME".equals(metaField.role())) {
            return;
        }
        com.entloom.meta.contract.value.SourcedValue<?> role = metaField.sourcedValue(MetaDescriptorProperties.ROLE);
        diagnostics.add(MetaDiagnostic.warn(MetaDiagnosticCode.CONSUMER_UNSUPPORTED_PROPERTY)
            .entityClass(entityClass)
            .field(metaField.fieldName())
            .source(role == null ? MetaValueSource.META_EXPLICIT : role.source())
            .ruleId("crud.consumer.datetime.role")
            .property(MetaDescriptorProperties.ROLE)
            .location(entityClass.getName() + "#" + metaField.fieldName())
            .message("CRUD 当前仅消费 DATETIME.CREATED_TIME 的只读语义，暂不执行: " + metaField.role())
            .build());
    }

    private List<CrudRelationRuntimeModel> mergeRelations(
        Class<?> entityClass,
        EntEntityDescriptor meta,
        CrudNativeEntityModel nativeModel,
        String idField,
        MetaDiagnosticCollector diagnostics
    ) {
        LinkedHashSet<String> relationFields = new LinkedHashSet<String>();
        Map<String, EntRelationDescriptor> metaRelations = new LinkedHashMap<String, EntRelationDescriptor>();
        if (meta != null) {
            for (EntRelationDescriptor relation : meta.relations()) {
                metaRelations.put(relation.sourceField(), relation);
                relationFields.add(relation.sourceField());
            }
        }
        Map<String, CrudNativeRelationModel> nativeRelations = new LinkedHashMap<String, CrudNativeRelationModel>();
        if (nativeModel != null) {
            for (CrudNativeRelationModel relation : nativeModel.relations()) {
                nativeRelations.put(relation.fieldName(), relation);
                relationFields.add(relation.fieldName());
            }
        }
        List<CrudRelationRuntimeModel> relations = new ArrayList<CrudRelationRuntimeModel>();
        for (String relationField : relationFields) {
            EntRelationDescriptor metaRelation = metaRelations.get(relationField);
            CrudNativeRelationModel nativeRelation = nativeRelations.get(relationField);
            if (metaRelation == null && nativeRelation != null) {
                metaRelation = metaRelations.get(nativeRelation.sourceField().value());
            }
            if (metaRelation == null && nativeRelation == null) {
                continue;
            }
            SourcedValue<Class<?>> targetClass = nativeRelation == null ? null : nativeRelation.targetClass();
            SourcedValue<RelationCardinality> cardinality = resolveProperty(CrudRuntimeProperties.CARDINALITY, entityClass, relationField, diagnostics,
                nativeRelation == null ? null : nativeRelation.cardinality(),
                metaRelation == null ? null : relationValue(metaRelation.cardinality(), metaRelation, CrudRuntimeProperties.CARDINALITY),
                SourcedValue.defaulted(RelationCardinality.MANY_TO_ONE));
            relations.add(new CrudRelationRuntimeModel(
                nativeRelation == null ? relationField : nativeRelation.fieldName(),
                resolveProperty(CrudRuntimeProperties.TARGET_SERVICE, entityClass, relationField, diagnostics,
                    nativeRelation == null ? null : nativeRelation.targetService(),
                    metaRelation == null ? null : stringMeta(metaRelation.targetService())),
                resolveProperty(CrudRuntimeProperties.TARGET_ENTITY, entityClass, relationField, diagnostics,
                    nativeRelation == null ? null : nativeRelation.targetEntity(),
                    metaRelation == null ? null : stringMeta(metaRelation.targetEntity()),
                    targetClass == null || targetClass.value() == null ? null : SourcedValue.inferred(targetClass.value().getSimpleName())),
                targetClass == null ? SourcedValue.unknown(null) : targetClass,
                resolveProperty(CrudRuntimeProperties.SOURCE_FIELD, entityClass, relationField, diagnostics,
                    normalizeInferredSourceField(nativeRelation == null ? null : nativeRelation.sourceField(), cardinality, idField),
                    normalizeInferredSourceField(
                        metaRelation == null ? null : sourceValue(metaRelation.sourceField(), metaRelation.sourceFieldInferred()),
                        cardinality,
                        idField
                    )),
                resolveProperty(CrudRuntimeProperties.TARGET_FIELD, entityClass, relationField, diagnostics,
                    nativeRelation == null ? null : nativeRelation.targetField(),
                    metaRelation == null ? null : relationValue(metaRelation.targetField(), metaRelation, CrudRuntimeProperties.TARGET_FIELD),
                    SourcedValue.defaulted("id")),
                cardinality,
                metaRelation == null
                    ? SourcedValue.inferred(ownerSide(nativeRelation == null ? RelationCardinality.MANY_TO_ONE : nativeRelation.cardinality().value()))
                    : SourcedValue.inferred(metaRelation.ownerSide()),
                resolveProperty(CrudRuntimeProperties.SCOPE, entityClass, relationField, diagnostics,
                    nativeRelation == null ? null : nativeRelation.scope(), SourcedValue.defaulted(RelationScope.LOCAL_DB)),
                resolveProperty(CrudRuntimeProperties.JOIN_TYPE, entityClass, relationField, diagnostics,
                    nativeRelation == null ? null : nativeRelation.joinType(), SourcedValue.defaulted(JoinType.LEFT))
            ));
        }
        return relations;
    }

    private SourcedValue<String> normalizeInferredSourceField(
        SourcedValue<String> sourceField,
        SourcedValue<RelationCardinality> cardinality,
        String idField
    ) {
        if (sourceField == null || sourceField.explicit()) {
            return sourceField;
        }
        if (cardinality == null || cardinality.value() != RelationCardinality.ONE_TO_MANY || isBlank(idField)) {
            return sourceField;
        }
        return SourcedValue.inferred(idField);
    }

    @SafeVarargs
    private final <T> SourcedValue<T> resolveProperty(
        String property,
        Class<?> entityClass,
        String field,
        MetaDiagnosticCollector diagnostics,
        SourcedValue<T>... candidates
    ) {
        List<SourcedValue<T>> values = new ArrayList<SourcedValue<T>>();
        if (candidates != null) {
            for (SourcedValue<T> value : candidates) {
                add(values, value);
            }
        }
        warnExplicitConflict(property, entityClass, field, values, diagnostics);
        String target = entityClass.getName() + (field == null ? "" : "#" + field);
        List<Contribution<?>> contributions = new ArrayList<Contribution<?>>();
        for (SourcedValue<T> value : values) {
            addContribution(
                contributions,
                target,
                property,
                value,
                candidateRuleId(property, value)
            );
        }
        com.entloom.meta.contract.diagnostic.MetaDiagnosticResult<Map<String, Contribution<?>>> result =
            contributionResolver.resolve(contributions);
        diagnostics.addAll(result.diagnostics());
        Contribution<?> winner = result.value().get(target + "." + property);
        if (winner == null) {
            return SourcedValue.unknown(null);
        }
        @SuppressWarnings("unchecked")
        T winnerValue = (T) winner.value();
        return SourcedValue.of(
            winnerValue,
            winner.source(),
            state(winner.source()),
            isExplicit(winner.source()),
            winner.ruleId().value()
        );
    }

    private String candidateRuleId(String property, SourcedValue<?> value) {
        if (value != null && !isBlank(value.ruleId())) {
            return value.ruleId();
        }
        MetaValueSource source = value == null ? null : value.source();
        if (source == MetaValueSource.NATIVE_EXPLICIT) {
            return "crud.native." + property;
        }
        if (source == MetaValueSource.META_EXPLICIT) {
            return "meta.explicit." + property;
        }
        if (source == MetaValueSource.MODULE_PROJECT_CONVENTION) {
            return "crud.project." + property;
        }
        if (source == MetaValueSource.META_PROJECT_CONVENTION) {
            return "meta.project." + property;
        }
        if (source == MetaValueSource.MODULE_BUILT_IN_CONVENTION) {
            return "crud.built-in." + property;
        }
        if (source == MetaValueSource.META_BUILT_IN_CONVENTION) {
            return "meta.built-in." + property;
        }
        return "crud.candidate." + property;
    }

    private com.entloom.meta.contract.value.MetaValueState state(MetaValueSource source) {
        if (isExplicit(source)) {
            return com.entloom.meta.contract.value.MetaValueState.EXPLICIT;
        }
        if (source == MetaValueSource.INFERRED) {
            return com.entloom.meta.contract.value.MetaValueState.INFERRED;
        }
        if (source == MetaValueSource.DEFAULT_OR_EXPLICIT_UNKNOWN) {
            return com.entloom.meta.contract.value.MetaValueState.UNKNOWN;
        }
        return com.entloom.meta.contract.value.MetaValueState.DEFAULTED;
    }

    private boolean isExplicit(MetaValueSource source) {
        return source == MetaValueSource.META_EXPLICIT
            || source == MetaValueSource.NATIVE_EXPLICIT
            || source == MetaValueSource.BUSINESS_EXPLICIT_OVERRIDE;
    }

    private <T> void add(List<SourcedValue<T>> values, SourcedValue<T> value) {
        if (value != null) {
            values.add(value);
        }
    }

    private <T> void warnExplicitConflict(
        String property,
        Class<?> entityClass,
        String field,
        List<SourcedValue<T>> values,
        MetaDiagnosticCollector diagnostics
    ) {
        if (diagnostics == null) {
            return;
        }
        SourcedValue<T> winner = null;
        for (SourcedValue<T> value : values) {
            if (!value.explicit()) {
                continue;
            }
            if (winner == null) {
                winner = value;
                continue;
            }
            if (winner.value() == null ? value.value() != null : !winner.value().equals(value.value())) {
                diagnostics.add(MetaDiagnostic.warn(MetaDiagnosticCode.EXPLICIT_VALUE_CONFLICT)
                    .entityClass(entityClass)
                    .field(field)
                    .source(MetaValueSource.NATIVE_EXPLICIT)
                    .property(property)
                    .location(entityClass.getName() + (field == null ? "" : "#" + field))
                    .message("CRUD native 显式值覆盖 Meta 显式值: " + property)
                    .build());
                return;
            }
        }
    }

    private SourcedValue<String> stringMeta(String value) {
        return isBlank(value) ? SourcedValue.unknown(null) : SourcedValue.metaExplicit(value);
    }

    private SourcedValue<String> sourceValue(String value, boolean inferred) {
        return inferred ? SourcedValue.inferred(value) : SourcedValue.metaExplicit(value);
    }

    private <T> SourcedValue<T> relationValue(T value, EntRelationDescriptor relation, String property) {
        com.entloom.meta.contract.value.SourcedValue<?> sourced = relation.sourcedValue(property);
        if (sourced != null && sourced.explicit()) {
            return SourcedValue.of(value, sourced.source(), sourced.state(), true, sourced.ruleId());
        }
        return SourcedValue.unknown(value);
    }

    private String resolveIdField(EntEntityDescriptor descriptor) {
        for (EntFieldDescriptor field : descriptor.fields()) {
            if ("ID".equals(field.fieldKind())) {
                return field.fieldName();
            }
        }
        return "id";
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
