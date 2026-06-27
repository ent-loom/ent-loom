package com.entloom.meta.adapter.doc.merge;

import com.entloom.doc.core.model.DocEntityModel;
import com.entloom.doc.core.model.DocFieldConstraintModel;
import com.entloom.doc.core.model.DocFieldModel;
import com.entloom.doc.core.model.DocIndexModel;
import com.entloom.doc.core.model.DocRelationModel;
import com.entloom.doc.core.model.DocRuntimeProperties;
import com.entloom.meta.contract.descriptor.EntEntityDescriptor;
import com.entloom.meta.contract.descriptor.EntFieldConstraintDescriptor;
import com.entloom.meta.contract.descriptor.EntFieldDescriptor;
import com.entloom.meta.contract.descriptor.EntIndexDescriptor;
import com.entloom.meta.contract.descriptor.EntRelationDescriptor;
import com.entloom.meta.contract.descriptor.MetaDescriptorProperties;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCollector;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;
import com.entloom.meta.enums.RelationCardinality;
import com.entloom.meta.contract.enums.RelationOwnerSide;
import com.entloom.meta.contract.enums.RelationResolutionStatus;
import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.contract.value.SourcedValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * DOC P0 minimal runtime model merger.
 */
public class DocRuntimeModelMerger {

    public MetaDiagnosticResult<DocEntityModel> merge(
        Class<?> entityClass,
        EntEntityDescriptor meta,
        DocEntityModel nativeModel,
        SourcedValue<String> inferredTable
    ) {
        if (entityClass == null) {
            return MetaDiagnosticResult.of(null, java.util.Collections.emptyList());
        }
        if (meta == null && nativeModel == null) {
            return MetaDiagnosticResult.of(null, java.util.Collections.emptyList());
        }
        MetaDiagnosticCollector diagnostics = new MetaDiagnosticCollector();
        DocEntityModel model = new DocEntityModel(
            entityClass,
            choose(DocRuntimeProperties.RESOURCE_CODE, entityClass, null, diagnostics,
                nativeModel == null ? null : nativeModel.resourceCode(),
                meta == null ? null : stringMeta(meta.entityName()),
                SourcedValue.inferred(entityClass.getSimpleName())),
            choose(DocRuntimeProperties.ENTITY_NAME, entityClass, null, diagnostics,
                nativeModel == null ? null : nativeModel.entityName(),
                meta == null ? null : stringMeta(valueOrDefault(meta.label(), meta.entityName())),
                SourcedValue.inferred(entityClass.getSimpleName())),
            choose(DocRuntimeProperties.DESCRIPTION, entityClass, null, diagnostics,
                nativeModel == null ? null : nativeModel.description(),
                meta == null ? null : stringMeta(meta.description()),
                SourcedValue.defaulted("")),
            choose(DocRuntimeProperties.TABLE, entityClass, null, diagnostics,
                nativeModel == null ? null : nativeModel.tableName(),
                inferredTable,
                SourcedValue.defaulted("")),
            mergeFields(entityClass, meta, nativeModel, diagnostics),
            mergeRelations(entityClass, meta, nativeModel, diagnostics),
            mergeIndexes(meta, nativeModel)
        );
        return MetaDiagnosticResult.of(model, diagnostics.diagnostics());
    }

    private List<DocFieldModel> mergeFields(
        Class<?> entityClass,
        EntEntityDescriptor meta,
        DocEntityModel nativeModel,
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
        Map<String, DocFieldModel> nativeFields = new LinkedHashMap<String, DocFieldModel>();
        if (nativeModel != null) {
            for (DocFieldModel field : nativeModel.fields()) {
                nativeFields.put(field.property(), field);
                fieldNames.add(field.property());
            }
        }
        List<DocFieldModel> fields = new ArrayList<DocFieldModel>();
        for (String fieldName : fieldNames) {
            EntFieldDescriptor metaField = metaFields.get(fieldName);
            DocFieldModel nativeField = nativeFields.get(fieldName);
            Class<?> javaType = metaField != null && metaField.javaType() != null
                ? metaField.javaType()
                : nativeField == null ? Object.class : nativeField.javaType();
            fields.add(new DocFieldModel(
                fieldName,
                javaType,
                nativeField == null ? SourcedValue.unknown(null) : nativeField.column(),
                choose(DocRuntimeProperties.FIELD_NAME, entityClass, fieldName, diagnostics,
                    nativeField == null ? null : nativeField.name(),
                    metaField == null ? null : stringMeta(metaField.label()),
                    SourcedValue.inferred(fieldName)),
                choose(DocRuntimeProperties.DESCRIPTION, entityClass, fieldName, diagnostics,
                    nativeField == null ? null : nativeField.description(),
                    metaField == null ? null : stringMeta(metaField.description()),
                    SourcedValue.defaulted("")),
                choose("example", entityClass, fieldName, diagnostics,
                    nativeField == null ? null : nativeField.example(),
                    metaField == null || metaField.examples().isEmpty() ? null : SourcedValue.metaExplicit(metaField.examples().get(0)),
                    SourcedValue.defaulted("")),
                metaField != null && !metaField.examples().isEmpty()
                    ? new ArrayList<String>(metaField.examples())
                    : nativeField == null ? java.util.Collections.<String>emptyList() : nativeField.examples(),
                choose(DocRuntimeProperties.REQUIRED, entityClass, fieldName, diagnostics,
                    nativeField == null ? null : nativeField.required(),
                    metaField == null || metaField.required() == null ? null : SourcedValue.metaExplicit(metaField.required()),
                    SourcedValue.defaulted(Boolean.FALSE)),
                choose("readOnly", entityClass, fieldName, diagnostics,
                    nativeField == null ? null : nativeField.readOnly(),
                    metaField == null || metaField.readOnly() == null ? null : SourcedValue.metaExplicit(metaField.readOnly()),
                    SourcedValue.defaulted(Boolean.FALSE)),
                nativeField == null ? SourcedValue.unknown(null) : nativeField.maxLength(),
                nativeField == null ? SourcedValue.unknown(null) : nativeField.minLength(),
                choose("fieldKind", entityClass, fieldName, diagnostics,
                    nativeField == null ? null : nativeField.fieldKind(),
                    metaField == null ? null : stringMeta(metaField.fieldKind())),
                choose("role", entityClass, fieldName, diagnostics,
                    nativeField == null ? null : nativeField.role(),
                    metaField == null ? null : stringMeta(metaField.role())),
                choose("createDefaultValue", entityClass, fieldName, diagnostics,
                    nativeField == null ? null : nativeField.createDefaultValue(),
                    metaField == null ? null : stringMeta(metaField.createDefaultValue())),
                metaField == null ? nativeConstraints(nativeField) : metaConstraints(metaField)
            ));
        }
        return fields;
    }

    private List<DocRelationModel> mergeRelations(
        Class<?> entityClass,
        EntEntityDescriptor meta,
        DocEntityModel nativeModel,
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
        Map<String, DocRelationModel> nativeRelations = new LinkedHashMap<String, DocRelationModel>();
        if (nativeModel != null) {
            for (DocRelationModel relation : nativeModel.relations()) {
                nativeRelations.put(relation.relationField(), relation);
                relationFields.add(relation.relationField());
            }
        }
        List<DocRelationModel> relations = new ArrayList<DocRelationModel>();
        for (String relationField : relationFields) {
            EntRelationDescriptor metaRelation = metaRelations.get(relationField);
            DocRelationModel nativeRelation = nativeRelations.get(relationField);
            if (metaRelation == null && nativeRelation != null) {
                metaRelation = metaRelations.get(nativeRelation.sourceField().value());
            }
            String outputField = nativeRelation == null ? relationField : nativeRelation.relationField();
            relations.add(new DocRelationModel(
                outputField,
                choose(DocRuntimeProperties.TARGET_SERVICE, entityClass, outputField, diagnostics,
                    nativeRelation == null ? null : nativeRelation.targetService(),
                    metaRelation == null ? null : stringMeta(metaRelation.targetService())),
                choose(DocRuntimeProperties.TARGET_ENTITY, entityClass, outputField, diagnostics,
                    nativeRelation == null ? null : nativeRelation.targetEntity(),
                    metaRelation == null ? null : stringMeta(metaRelation.targetEntity())),
                choose(DocRuntimeProperties.SOURCE_FIELD, entityClass, outputField, diagnostics,
                    nativeRelation == null ? null : nativeRelation.sourceField(),
                    metaRelation == null ? null : sourceValue(metaRelation.sourceField(), metaRelation.sourceFieldInferred()),
                    SourcedValue.inferred(outputField)),
                choose(DocRuntimeProperties.TARGET_FIELD, entityClass, outputField, diagnostics,
                    nativeRelation == null ? null : nativeRelation.targetField(),
                    metaRelation == null ? null : relationValue(metaRelation.targetField(), metaRelation, MetaDescriptorProperties.TARGET_FIELD),
                    SourcedValue.defaulted("id")),
                choose(DocRuntimeProperties.CARDINALITY, entityClass, outputField, diagnostics,
                    nativeRelation == null ? null : nativeRelation.cardinality(),
                    metaRelation == null ? null : relationValue(metaRelation.cardinality(), metaRelation, MetaDescriptorProperties.CARDINALITY),
                    SourcedValue.defaulted(RelationCardinality.MANY_TO_ONE)),
                metaRelation == null
                    ? nativeRelation == null ? SourcedValue.inferred(RelationOwnerSide.DECLARING_ENTITY) : nativeRelation.ownerSide()
                    : SourcedValue.inferred(metaRelation.ownerSide()),
                metaRelation == null
                    ? nativeRelation == null ? SourcedValue.inferred(RelationResolutionStatus.PARTIALLY_RESOLVED) : nativeRelation.resolutionStatus()
                    : SourcedValue.inferred(metaRelation.resolutionStatus()),
                nativeRelation == null ? SourcedValue.unknown(null) : nativeRelation.targetEntityLabel(),
                nativeRelation == null ? SourcedValue.unknown(null) : nativeRelation.relationRemark(),
                metaRelation != null ? metaRelation.sourceFieldInferred() : nativeRelation != null && nativeRelation.sourceFieldInferred()
            ));
        }
        return relations;
    }

    private List<DocIndexModel> mergeIndexes(EntEntityDescriptor meta, DocEntityModel nativeModel) {
        List<DocIndexModel> indexes = new ArrayList<DocIndexModel>();
        if (meta != null) {
            for (EntIndexDescriptor index : meta.indexes()) {
                indexes.add(new DocIndexModel(
                    stringMeta(index.indexName()),
                    index.fields(),
                    SourcedValue.metaExplicit(Boolean.valueOf(index.unique()))
                ));
            }
        }
        if (nativeModel != null) {
            indexes.addAll(nativeModel.indexes());
        }
        return indexes;
    }

    @SafeVarargs
    private final <T> SourcedValue<T> choose(
        String property,
        Class<?> entityClass,
        String field,
        MetaDiagnosticCollector diagnostics,
        SourcedValue<T>... candidates
    ) {
        List<SourcedValue<T>> values = new ArrayList<SourcedValue<T>>();
        if (candidates != null) {
            for (SourcedValue<T> value : candidates) {
                if (value != null) {
                    values.add(value);
                }
            }
        }
        warnExplicitConflict(property, entityClass, field, values, diagnostics);
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
                    .message("DOC native 显式值覆盖 Meta 显式值: " + property)
                    .build());
                return;
            }
        }
    }

    private List<DocFieldConstraintModel> metaConstraints(EntFieldDescriptor field) {
        List<DocFieldConstraintModel> constraints = new ArrayList<DocFieldConstraintModel>();
        for (EntFieldConstraintDescriptor constraint : field.constraints()) {
            constraints.add(new DocFieldConstraintModel(constraint.name(), constraint.value()));
        }
        return constraints;
    }

    private List<DocFieldConstraintModel> nativeConstraints(DocFieldModel nativeField) {
        return nativeField == null ? java.util.Collections.<DocFieldConstraintModel>emptyList() : nativeField.constraints();
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
            return SourcedValue.metaExplicit(value);
        }
        return SourcedValue.unknown(value);
    }

    private String valueOrDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
