package com.entloom.crud.core.runtime.model;

import com.entloom.crud.api.enums.JoinType;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.enums.RelationScope;
import com.entloom.meta.enums.RelationCardinality;
import lombok.Getter;

/**
 * CRUD 运行期关系元数据。
 */
@Getter
public final class CrudRuntimeRelationModel {
    private final Class<?> fromEntity;
    private final Class<?> toEntity;
    private final String relationField;
    private final String fromField;
    private final String toField;
    private final RelationScope scope;
    private final JoinType joinKind;
    private final RelationCardinality cardinality;

    public CrudRuntimeRelationModel(
        Class<?> fromEntity,
        Class<?> toEntity,
        String relationField,
        String fromField,
        String toField,
        RelationScope scope,
        JoinType joinKind,
        RelationCardinality cardinality
    ) {
        this.fromEntity = fromEntity;
        this.toEntity = toEntity;
        this.relationField = relationField;
        this.fromField = fromField;
        this.toField = toField;
        this.scope = scope;
        this.joinKind = joinKind;
        this.cardinality = cardinality;
    }

    public static CrudRuntimeRelationModel from(RelationEdge edge) {
        if (edge == null) {
            return null;
        }
        return new CrudRuntimeRelationModel(
            edge.getFromEntity(),
            edge.getToEntity(),
            edge.getRelationField(),
            edge.getFromField(),
            edge.getToField(),
            edge.getScope(),
            edge.getJoinKind(),
            edge.getCardinality()
        );
    }

    public RelationEdge toRelationEdge() {
        RelationEdge edge = new RelationEdge();
        edge.setFromEntity(fromEntity);
        edge.setToEntity(toEntity);
        edge.setRelationField(relationField);
        edge.setFromField(fromField);
        edge.setToField(toField);
        edge.setScope(scope);
        edge.setJoinKind(joinKind);
        edge.setCardinality(cardinality);
        return edge;
    }

}
