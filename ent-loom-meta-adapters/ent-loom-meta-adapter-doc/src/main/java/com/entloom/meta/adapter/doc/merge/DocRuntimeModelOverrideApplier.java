package com.entloom.meta.adapter.doc.merge;

import com.entloom.doc.core.model.DocEntityModel;
import com.entloom.doc.core.model.DocFieldModel;
import com.entloom.doc.core.spi.DocEntityOverride;
import com.entloom.doc.core.spi.DocFieldOverride;
import com.entloom.doc.core.spi.DocOverrideProvider;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCollector;
import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.contract.value.SourcedValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Applies business DOC overrides after Meta/native merge.
 */
public class DocRuntimeModelOverrideApplier {
    private final DocOverrideProvider overrideProvider;

    public DocRuntimeModelOverrideApplier(DocOverrideProvider overrideProvider) {
        this.overrideProvider = overrideProvider == null ? DocOverrideProvider.noop() : overrideProvider;
    }

    public DocEntityModel apply(DocEntityModel model, MetaDiagnosticCollector diagnostics) {
        if (model == null || model.entityClass() == null) {
            return model;
        }
        DocEntityOverride override = overrideProvider.overrideFor(model.entityClass(), model.resourceCode().value());
        if (override == null) {
            return model;
        }
        return new DocEntityModel(
            model.entityClass(),
            model.resourceCode(),
            stringOverride("entityName", model, null, diagnostics, model.entityName(), override.entityName()),
            stringOverride("description", model, null, diagnostics, model.description(), override.description()),
            model.tableName(),
            stringOverride("group", model, null, diagnostics, model.group(), override.group()),
            stringOverride("remark", model, null, diagnostics, model.remark(), override.remark()),
            booleanOverride("hidden", model, null, diagnostics, model.hidden(), override.hidden()),
            listOverride(model.visibleFor(), override.visibleFor()),
            applyFields(model, override, diagnostics),
            model.relations(),
            model.indexes()
        );
    }

    private List<DocFieldModel> applyFields(
        DocEntityModel model,
        DocEntityOverride entityOverride,
        MetaDiagnosticCollector diagnostics
    ) {
        if (model.fields().isEmpty() || entityOverride.fields().isEmpty()) {
            return model.fields();
        }
        List<DocFieldModel> fields = new ArrayList<DocFieldModel>();
        for (DocFieldModel field : model.fields()) {
            DocFieldOverride override = entityOverride.fields().get(field.property());
            if (override == null) {
                fields.add(field);
                continue;
            }
            fields.add(new DocFieldModel(
                field.property(),
                field.javaType(),
                field.column(),
                stringOverride("name", model, field.property(), diagnostics, field.name(), override.name()),
                stringOverride("description", model, field.property(), diagnostics, field.description(), override.description()),
                stringOverride("example", model, field.property(), diagnostics, field.example(), override.example()),
                examplesOverride(field.examples(), override),
                field.required(),
                field.readOnly(),
                field.maxLength(),
                field.minLength(),
                field.fieldKind(),
                field.role(),
                field.createDefaultValue(),
                stringOverride("group", model, field.property(), diagnostics, field.group(), override.group()),
                stringOverride("remark", model, field.property(), diagnostics, field.remark(), override.remark()),
                booleanOverride("hidden", model, field.property(), diagnostics, field.hidden(), override.hidden()),
                listOverride(field.visibleFor(), override.visibleFor()),
                field.constraints()
            ));
        }
        return fields;
    }

    private List<String> examplesOverride(List<String> current, DocFieldOverride override) {
        if (override.examples() != null && !override.examples().isEmpty()) {
            return override.examples();
        }
        if (!isBlank(override.example())) {
            return Collections.singletonList(override.example().trim());
        }
        return current;
    }

    private List<String> listOverride(List<String> current, List<String> override) {
        if (override == null || override.isEmpty()) {
            return current;
        }
        return override;
    }

    private SourcedValue<String> stringOverride(
        String property,
        DocEntityModel model,
        String field,
        MetaDiagnosticCollector diagnostics,
        SourcedValue<String> current,
        String override
    ) {
        if (isBlank(override)) {
            return current;
        }
        warnConflict(property, model, field, diagnostics, current, override.trim());
        return SourcedValue.businessExplicitOverride(override.trim());
    }

    private SourcedValue<Boolean> booleanOverride(
        String property,
        DocEntityModel model,
        String field,
        MetaDiagnosticCollector diagnostics,
        SourcedValue<Boolean> current,
        Boolean override
    ) {
        if (override == null) {
            return current;
        }
        warnConflict(property, model, field, diagnostics, current, override);
        return SourcedValue.businessExplicitOverride(override);
    }

    private <T> void warnConflict(
        String property,
        DocEntityModel model,
        String field,
        MetaDiagnosticCollector diagnostics,
        SourcedValue<T> current,
        T override
    ) {
        if (diagnostics == null || current == null || !current.explicit()) {
            return;
        }
        T currentValue = current.value();
        if (currentValue == null ? override == null : currentValue.equals(override)) {
            return;
        }
        diagnostics.add(MetaDiagnostic.warn(MetaDiagnosticCode.EXPLICIT_VALUE_CONFLICT)
            .entity(model.resourceCode().value())
            .entityClass(model.entityClass())
            .field(field)
            .source(MetaValueSource.BUSINESS_EXPLICIT_OVERRIDE)
            .property(property)
            .location(model.entityClass().getName() + (field == null ? "" : "#" + field))
            .message("DOC business provider 显式覆盖已有显式值: " + property)
            .build());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
