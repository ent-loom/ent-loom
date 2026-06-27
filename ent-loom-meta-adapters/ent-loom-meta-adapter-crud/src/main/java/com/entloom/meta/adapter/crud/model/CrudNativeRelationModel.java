package com.entloom.meta.adapter.crud.model;

import com.entloom.crud.api.enums.JoinType;
import com.entloom.crud.enums.RelationScope;
import com.entloom.meta.enums.RelationCardinality;
import com.entloom.meta.contract.value.SourcedValue;

/**
 * CRUD native 关系中间模型。
 */
public final class CrudNativeRelationModel {
    private final String fieldName;
    private final SourcedValue<String> targetService;
    private final SourcedValue<String> targetEntity;
    private final SourcedValue<Class<?>> targetClass;
    private final SourcedValue<String> sourceField;
    private final SourcedValue<String> targetField;
    private final SourcedValue<RelationCardinality> cardinality;
    private final SourcedValue<RelationScope> scope;
    private final SourcedValue<JoinType> joinType;

    public CrudNativeRelationModel(
        String fieldName,
        SourcedValue<String> targetService,
        SourcedValue<String> targetEntity,
        SourcedValue<Class<?>> targetClass,
        SourcedValue<String> sourceField,
        SourcedValue<String> targetField,
        SourcedValue<RelationCardinality> cardinality,
        SourcedValue<RelationScope> scope,
        SourcedValue<JoinType> joinType
    ) {
        this.fieldName = fieldName;
        this.targetService = targetService;
        this.targetEntity = targetEntity;
        this.targetClass = targetClass;
        this.sourceField = sourceField;
        this.targetField = targetField;
        this.cardinality = cardinality;
        this.scope = scope;
        this.joinType = joinType;
    }

    public String fieldName() {
        return fieldName;
    }

    public SourcedValue<String> targetService() {
        return targetService;
    }

    public SourcedValue<String> targetEntity() {
        return targetEntity;
    }

    public SourcedValue<Class<?>> targetClass() {
        return targetClass;
    }

    public SourcedValue<String> sourceField() {
        return sourceField;
    }

    public SourcedValue<String> targetField() {
        return targetField;
    }

    public SourcedValue<RelationCardinality> cardinality() {
        return cardinality;
    }

    public SourcedValue<RelationScope> scope() {
        return scope;
    }

    public SourcedValue<JoinType> joinType() {
        return joinType;
    }
}
