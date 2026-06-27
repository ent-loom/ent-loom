package com.entloom.meta.core.model;

import com.entloom.meta.enums.RelationCardinality;

/**
 * Framework-neutral relation edge resolved from Ent meta descriptors.
 */
public final class EntRelationEdgeModel {
    private final Class<?> fromEntity;
    private final Class<?> toEntity;
    private final String relationField;
    private final String fromField;
    private final String toField;
    private final RelationCardinality cardinality;

    public EntRelationEdgeModel(
        Class<?> fromEntity,
        Class<?> toEntity,
        String relationField,
        String fromField,
        String toField,
        RelationCardinality cardinality
    ) {
        this.fromEntity = fromEntity;
        this.toEntity = toEntity;
        this.relationField = relationField;
        this.fromField = fromField;
        this.toField = toField;
        this.cardinality = cardinality;
    }

    public Class<?> fromEntity() {
        return fromEntity;
    }

    public Class<?> toEntity() {
        return toEntity;
    }

    public String relationField() {
        return relationField;
    }

    public String fromField() {
        return fromField;
    }

    public String toField() {
        return toField;
    }

    public RelationCardinality cardinality() {
        return cardinality;
    }
}
