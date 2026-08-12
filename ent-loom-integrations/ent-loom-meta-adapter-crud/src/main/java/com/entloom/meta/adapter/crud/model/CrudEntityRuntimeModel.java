package com.entloom.meta.adapter.crud.model;

import com.entloom.meta.contract.value.SourcedValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CRUD 合并后的实体模型。
 */
public final class CrudEntityRuntimeModel {
    private final Class<?> entityClass;
    private final SourcedValue<String> resourceCode;
    private final SourcedValue<String> table;
    private final SourcedValue<String> idField;
    private final SourcedValue<String> logicDeleteField;
    private final SourcedValue<String> ownerService;
    private final List<CrudFieldRuntimeModel> fields;
    private final List<CrudRelationRuntimeModel> relations;

    public CrudEntityRuntimeModel(
        Class<?> entityClass,
        SourcedValue<String> resourceCode,
        SourcedValue<String> table,
        SourcedValue<String> idField,
        SourcedValue<String> logicDeleteField,
        SourcedValue<String> ownerService,
        List<CrudFieldRuntimeModel> fields,
        List<CrudRelationRuntimeModel> relations
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

    public List<CrudFieldRuntimeModel> fields() {
        return fields;
    }

    public List<CrudRelationRuntimeModel> relations() {
        return relations;
    }

    private static <T> List<T> immutableList(List<T> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<T>(source));
    }
}
