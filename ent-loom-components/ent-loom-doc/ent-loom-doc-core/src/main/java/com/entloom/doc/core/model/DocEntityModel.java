package com.entloom.doc.core.model;

import com.entloom.meta.contract.value.SourcedValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stable DOC entity model consumed by EntityDocCoreService.
 */
public final class DocEntityModel {
    private final Class<?> entityClass;
    private final SourcedValue<String> resourceCode;
    private final SourcedValue<String> entityName;
    private final SourcedValue<String> description;
    private final SourcedValue<String> tableName;
    private final SourcedValue<String> group;
    private final SourcedValue<String> remark;
    private final SourcedValue<Boolean> hidden;
    private final List<String> visibleFor;
    private final List<DocFieldModel> fields;
    private final List<DocRelationModel> relations;
    private final List<DocIndexModel> indexes;

    public DocEntityModel(
        Class<?> entityClass,
        SourcedValue<String> resourceCode,
        SourcedValue<String> entityName,
        SourcedValue<String> description,
        SourcedValue<String> tableName,
        List<DocFieldModel> fields,
        List<DocRelationModel> relations,
        List<DocIndexModel> indexes
    ) {
        this(
            entityClass,
            resourceCode,
            entityName,
            description,
            tableName,
            SourcedValue.unknown(null),
            SourcedValue.unknown(null),
            SourcedValue.defaulted(Boolean.FALSE),
            Collections.<String>emptyList(),
            fields,
            relations,
            indexes
        );
    }

    public DocEntityModel(
        Class<?> entityClass,
        SourcedValue<String> resourceCode,
        SourcedValue<String> entityName,
        SourcedValue<String> description,
        SourcedValue<String> tableName,
        SourcedValue<String> group,
        SourcedValue<String> remark,
        SourcedValue<Boolean> hidden,
        List<String> visibleFor,
        List<DocFieldModel> fields,
        List<DocRelationModel> relations,
        List<DocIndexModel> indexes
    ) {
        this.entityClass = entityClass;
        this.resourceCode = resourceCode == null ? SourcedValue.unknown(null) : resourceCode;
        this.entityName = entityName == null ? SourcedValue.unknown(null) : entityName;
        this.description = description == null ? SourcedValue.unknown(null) : description;
        this.tableName = tableName == null ? SourcedValue.unknown(null) : tableName;
        this.group = group == null ? SourcedValue.unknown(null) : group;
        this.remark = remark == null ? SourcedValue.unknown(null) : remark;
        this.hidden = hidden == null ? SourcedValue.defaulted(Boolean.FALSE) : hidden;
        this.visibleFor = visibleFor == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<String>(visibleFor));
        this.fields = fields == null ? Collections.<DocFieldModel>emptyList() : Collections.unmodifiableList(new ArrayList<DocFieldModel>(fields));
        this.relations = relations == null ? Collections.<DocRelationModel>emptyList() : Collections.unmodifiableList(new ArrayList<DocRelationModel>(relations));
        this.indexes = indexes == null ? Collections.<DocIndexModel>emptyList() : Collections.unmodifiableList(new ArrayList<DocIndexModel>(indexes));
    }

    public Class<?> entityClass() {
        return entityClass;
    }

    public SourcedValue<String> resourceCode() {
        return resourceCode;
    }

    public SourcedValue<String> entityName() {
        return entityName;
    }

    public SourcedValue<String> description() {
        return description;
    }

    public SourcedValue<String> tableName() {
        return tableName;
    }

    public SourcedValue<String> group() {
        return group;
    }

    public SourcedValue<String> remark() {
        return remark;
    }

    public SourcedValue<Boolean> hidden() {
        return hidden;
    }

    public List<String> visibleFor() {
        return visibleFor;
    }

    public List<DocFieldModel> fields() {
        return fields;
    }

    public List<DocRelationModel> relations() {
        return relations;
    }

    public List<DocIndexModel> indexes() {
        return indexes;
    }
}
