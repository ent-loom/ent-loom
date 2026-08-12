package com.entloom.crud.core.capability.command.aggregate;

import com.entloom.crud.core.runtime.meta.RelationEdge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聚合关系 PATCH 视图。
 *
 * @param <C> 子实体类型
 */
public class AggregateRelationPatch<C> {
    private final AggregateRelationSpec relationSpec;
    private final RelationEdge relationEdge;
    private final Class<C> childType;
    private final boolean present;
    private final List<C> items;

    public AggregateRelationPatch(
        AggregateRelationSpec relationSpec,
        RelationEdge relationEdge,
        Class<C> childType,
        boolean present,
        List<C> items
    ) {
        this.relationSpec = relationSpec;
        this.relationEdge = relationEdge;
        this.childType = childType;
        this.present = present;
        this.items = items == null
            ? Collections.<C>emptyList()
            : Collections.unmodifiableList(new ArrayList<C>(items));
    }

    public AggregateRelationSpec getRelationSpec() {
        return relationSpec;
    }

    public RelationEdge getRelationEdge() {
        return relationEdge;
    }

    public Class<C> getChildType() {
        return childType;
    }

    public boolean isPresent() {
        return present;
    }

    public List<C> getItems() {
        return items;
    }

    public String getRelationField() {
        return relationSpec.getRelationField();
    }

    public ChildSyncMode getSyncMode() {
        return relationSpec.getSyncMode();
    }
}
