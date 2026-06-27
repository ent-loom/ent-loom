package com.entloom.meta.adapter.crud.model;

import com.entloom.meta.contract.value.SourcedValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CRUD native 注解解析后的实体中间模型。
 */
public final class CrudNativeEntityModel {
    private final Class<?> entityClass;
    private final SourcedValue<String> resourceCode;
    private final SourcedValue<String> table;
    private final SourcedValue<String> idField;
    private final SourcedValue<String> logicDeleteField;
    private final SourcedValue<String> ownerService;
    private final List<CrudNativeFieldModel> fields;
    private final List<CrudNativeRelationModel> relations;

    public CrudNativeEntityModel(
        Class<?> entityClass,
        SourcedValue<String> resourceCode,
        SourcedValue<String> table,
        SourcedValue<String> idField,
        SourcedValue<String> logicDeleteField,
        SourcedValue<String> ownerService,
        List<CrudNativeFieldModel> fields,
        List<CrudNativeRelationModel> relations
    ) {
        this.entityClass = entityClass;
        this.resourceCode = resourceCode;
        this.table = table;
        this.idField = idField;
        this.logicDeleteField = logicDeleteField;
        this.ownerService = ownerService;
        this.fields = immutableList(fields);
        this.relations = immutableList(relations);
    }

    public Class<?> entityClass() {
        return entityClass;
    }

    public SourcedValue<String> resourceCode() {
        return resourceCode;
    }

    public SourcedValue<String> table() {
        return table;
    }

    public SourcedValue<String> idField() {
        return idField;
    }

    public SourcedValue<String> logicDeleteField() {
        return logicDeleteField;
    }

    public SourcedValue<String> ownerService() {
        return ownerService;
    }

    public List<CrudNativeFieldModel> fields() {
        return fields;
    }

    public List<CrudNativeRelationModel> relations() {
        return relations;
    }

    private static <T> List<T> immutableList(List<T> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<T>(source));
    }
}
