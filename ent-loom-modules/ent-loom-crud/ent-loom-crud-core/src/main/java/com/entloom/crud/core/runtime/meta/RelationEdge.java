package com.entloom.crud.core.runtime.meta;

import com.entloom.crud.api.enums.JoinType;
import com.entloom.crud.enums.RelationScope;
import com.entloom.meta.enums.RelationCardinality;
import lombok.Getter;

/**
 * 关系边定义。
 */
@Getter
public class RelationEdge {
    private final boolean frozen;
    /** 起始实体类型。 */
    private Class<?> fromEntity;
    /** 目标实体类型。 */
    private Class<?> toEntity;
    /** 根实体上的关联字段名。 */
    private String relationField;
    /** 起始字段名。 */
    private String fromField;
    /** 目标字段名。 */
    private String toField;
    /** 范围配置。 */
    private RelationScope scope;
    /** 关联连接类型。 */
    private JoinType joinKind;
    /** 关联基数。 */
    private RelationCardinality cardinality;

    public RelationEdge() {
        this(false);
    }

    private RelationEdge(boolean frozen) {
        this.frozen = frozen;
    }

    public static RelationEdge mutableCopyOf(RelationEdge source) {
        RelationEdge edge = new RelationEdge(false);
        copy(source, edge);
        return edge;
    }

    public static RelationEdge immutableCopyOf(RelationEdge source) {
        RelationEdge edge = new RelationEdge(true);
        copy(source, edge);
        return edge;
    }

    public void setFromEntity(Class<?> fromEntity) {
        requireMutable();
        this.fromEntity = fromEntity;
    }

    public void setToEntity(Class<?> toEntity) {
        requireMutable();
        this.toEntity = toEntity;
    }

    public void setRelationField(String relationField) {
        requireMutable();
        this.relationField = relationField;
    }

    public void setFromField(String fromField) {
        requireMutable();
        this.fromField = fromField;
    }

    public void setToField(String toField) {
        requireMutable();
        this.toField = toField;
    }

    public void setScope(RelationScope scope) {
        requireMutable();
        this.scope = scope;
    }

    public void setJoinKind(JoinType joinKind) {
        requireMutable();
        this.joinKind = joinKind;
    }

    public void setCardinality(RelationCardinality cardinality) {
        requireMutable();
        this.cardinality = cardinality;
    }

    private void requireMutable() {
        if (frozen) {
            throw new UnsupportedOperationException("RelationEdge 已冻结");
        }
    }

    private static void copy(RelationEdge source, RelationEdge target) {
        if (source == null) {
            return;
        }
        target.fromEntity = source.fromEntity;
        target.toEntity = source.toEntity;
        target.relationField = source.relationField;
        target.fromField = source.fromField;
        target.toField = source.toField;
        target.scope = source.scope;
        target.joinKind = source.joinKind;
        target.cardinality = source.cardinality;
    }
}
