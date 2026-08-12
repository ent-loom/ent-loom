package com.entloom.doc.core.model;

import com.entloom.meta.enums.RelationCardinality;
import com.entloom.meta.contract.enums.RelationOwnerSide;
import com.entloom.meta.contract.enums.RelationResolutionStatus;
import com.entloom.meta.contract.value.SourcedValue;

/**
 * Stable DOC relation model.
 */
public final class DocRelationModel {
    private final String relationField;
    private final SourcedValue<String> targetService;
    private final SourcedValue<String> targetEntity;
    private final SourcedValue<String> sourceField;
    private final SourcedValue<String> targetField;
    private final SourcedValue<RelationCardinality> cardinality;
    private final SourcedValue<RelationOwnerSide> ownerSide;
    private final SourcedValue<RelationResolutionStatus> resolutionStatus;
    private final SourcedValue<String> targetEntityLabel;
    private final SourcedValue<String> relationRemark;
    private final boolean sourceFieldInferred;

    public DocRelationModel(
        String relationField,
        SourcedValue<String> targetService,
        SourcedValue<String> targetEntity,
        SourcedValue<String> sourceField,
        SourcedValue<String> targetField,
        SourcedValue<RelationCardinality> cardinality,
        SourcedValue<RelationOwnerSide> ownerSide,
        SourcedValue<RelationResolutionStatus> resolutionStatus,
        SourcedValue<String> targetEntityLabel,
        SourcedValue<String> relationRemark,
        boolean sourceFieldInferred
    ) {
        this.relationField = relationField;
        this.targetService = targetService == null ? SourcedValue.unknown(null) : targetService;
        this.targetEntity = targetEntity == null ? SourcedValue.unknown(null) : targetEntity;
        this.sourceField = sourceField == null ? SourcedValue.unknown(null) : sourceField;
        this.targetField = targetField == null ? SourcedValue.unknown(null) : targetField;
        this.cardinality = cardinality == null ? SourcedValue.unknown(null) : cardinality;
        this.ownerSide = ownerSide == null ? SourcedValue.unknown(null) : ownerSide;
        this.resolutionStatus = resolutionStatus == null ? SourcedValue.unknown(null) : resolutionStatus;
        this.targetEntityLabel = targetEntityLabel == null ? SourcedValue.unknown(null) : targetEntityLabel;
        this.relationRemark = relationRemark == null ? SourcedValue.unknown(null) : relationRemark;
        this.sourceFieldInferred = sourceFieldInferred;
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

    public SourcedValue<RelationResolutionStatus> resolutionStatus() {
        return resolutionStatus;
    }

    public SourcedValue<String> targetEntityLabel() {
        return targetEntityLabel;
    }

    public SourcedValue<String> relationRemark() {
        return relationRemark;
    }

    public boolean sourceFieldInferred() {
        return sourceFieldInferred;
    }
}
