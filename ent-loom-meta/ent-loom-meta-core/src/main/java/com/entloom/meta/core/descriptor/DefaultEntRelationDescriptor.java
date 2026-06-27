package com.entloom.meta.core.descriptor;

import com.entloom.meta.contract.descriptor.EntRelationDescriptor;
import com.entloom.meta.enums.RelationCardinality;
import com.entloom.meta.contract.enums.RelationOwnerSide;
import com.entloom.meta.contract.enums.RelationResolutionStatus;
import com.entloom.meta.contract.value.SourcedValue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Default immutable relation descriptor.
 */
public final class DefaultEntRelationDescriptor implements EntRelationDescriptor {
    private final String sourceField;
    private final String targetService;
    private final String targetEntity;
    private final String targetField;
    private final RelationCardinality cardinality;
    private final RelationOwnerSide ownerSide;
    private final RelationResolutionStatus resolutionStatus;
    private final boolean sourceFieldInferred;
    private final Map<String, SourcedValue<?>> sourcedValues;

    public DefaultEntRelationDescriptor(
        String sourceField,
        String targetService,
        String targetEntity,
        String targetField,
        RelationCardinality cardinality
    ) {
        this(
            sourceField,
            targetService,
            targetEntity,
            targetField,
            cardinality,
            RelationOwnerSide.UNKNOWN,
            RelationResolutionStatus.PARTIALLY_RESOLVED,
            false,
            Collections.<String, SourcedValue<?>>emptyMap()
        );
    }

    public DefaultEntRelationDescriptor(
        String sourceField,
        String targetService,
        String targetEntity,
        String targetField,
        RelationCardinality cardinality,
        Map<String, SourcedValue<?>> sourcedValues
    ) {
        this(
            sourceField,
            targetService,
            targetEntity,
            targetField,
            cardinality,
            RelationOwnerSide.UNKNOWN,
            RelationResolutionStatus.PARTIALLY_RESOLVED,
            false,
            sourcedValues
        );
    }

    public DefaultEntRelationDescriptor(
        String sourceField,
        String targetService,
        String targetEntity,
        String targetField,
        RelationCardinality cardinality,
        RelationOwnerSide ownerSide,
        RelationResolutionStatus resolutionStatus,
        boolean sourceFieldInferred,
        Map<String, SourcedValue<?>> sourcedValues
    ) {
        this.sourceField = sourceField;
        this.targetService = targetService;
        this.targetEntity = targetEntity;
        this.targetField = targetField;
        this.cardinality = cardinality;
        this.ownerSide = ownerSide;
        this.resolutionStatus = resolutionStatus;
        this.sourceFieldInferred = sourceFieldInferred;
        this.sourcedValues = immutableMap(sourcedValues);
    }

    @Override
    public String sourceField() {
        return sourceField;
    }

    @Override
    public String targetService() {
        return targetService;
    }

    @Override
    public String targetEntity() {
        return targetEntity;
    }

    @Override
    public String targetField() {
        return targetField;
    }

    @Override
    public RelationCardinality cardinality() {
        return cardinality;
    }

    @Override
    public RelationOwnerSide ownerSide() {
        return ownerSide;
    }

    @Override
    public RelationResolutionStatus resolutionStatus() {
        return resolutionStatus;
    }

    @Override
    public boolean sourceFieldInferred() {
        return sourceFieldInferred;
    }

    @Override
    public Map<String, SourcedValue<?>> sourcedValues() {
        return sourcedValues;
    }

    private static Map<String, SourcedValue<?>> immutableMap(Map<String, SourcedValue<?>> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<String, SourcedValue<?>>(source));
    }
}
