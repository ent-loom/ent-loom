package com.entloom.meta.adapter.crud.model;

import com.entloom.crud.api.enums.JoinType;
import com.entloom.crud.enums.RelationScope;
import com.entloom.meta.enums.RelationCardinality;
import com.entloom.meta.contract.enums.RelationOwnerSide;
import com.entloom.meta.contract.value.SourcedValue;

/**
 * CRUD 合并后的关系模型。
 */
public final class CrudRelationRuntimeModel {
    private final String relationField;
    private final SourcedValue<String> targetService;
    private final SourcedValue<String> targetEntity;
    private final SourcedValue<Class<?>> targetClass;
    private final SourcedValue<String> sourceField;
    private final SourcedValue<String> targetField;
    private final SourcedValue<RelationCardinality> cardinality;
    private final SourcedValue<RelationOwnerSide> ownerSide;
    private final SourcedValue<RelationScope> scope;
    private final SourcedValue<JoinType> joinType;

    public CrudRelationRuntimeModel(
        String relationField,
        SourcedValue<String> targetService,
        SourcedValue<String> targetEntity,
        SourcedValue<Class<?>> targetClass,
        SourcedValue<String> sourceField,
        SourcedValue<String> targetField,
        SourcedValue<RelationCardinality> cardinality,
        SourcedValue<RelationOwnerSide> ownerSide,
        SourcedValue<RelationScope> scope,
        SourcedValue<JoinType> joinType
    ) {
        this.relationField = relationField;
        this.targetService = targetService;
        this.targetEntity = targetEntity;
        this.targetClass = targetClass;
        this.sourceField = sourceField;
        this.targetField = targetField;
        this.cardinality = cardinality;
        this.ownerSide = ownerSide;
        this.scope = scope;
        this.joinType = joinType;
    }

    public String relationField() {
        return relationField;
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

    public SourcedValue<RelationOwnerSide> ownerSide() {
        return ownerSide;
    }

    public SourcedValue<RelationScope> scope() {
        return scope;
    }

    public SourcedValue<JoinType> joinType() {
        return joinType;
    }
}
