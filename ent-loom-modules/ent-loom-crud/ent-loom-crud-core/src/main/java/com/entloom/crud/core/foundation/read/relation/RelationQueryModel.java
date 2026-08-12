package com.entloom.crud.core.foundation.read.relation;

import com.entloom.crud.core.runtime.meta.RelationEdge;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * 关系查询解析结果。
 */
@Getter
public class RelationQueryModel {
    /** 请求的展开路径。 */
    private final List<String> requestedRelations;
    /** 解析出的展开边。 */
    private final List<RelationEdge> expandEdges;

    public RelationQueryModel(List<String> requestedRelations, List<RelationEdge> expandEdges) {
        this.requestedRelations = requestedRelations == null ? new ArrayList<String>() : new ArrayList<String>(requestedRelations);
        this.expandEdges = expandEdges == null ? new ArrayList<RelationEdge>() : new ArrayList<RelationEdge>(expandEdges);
    }
}
