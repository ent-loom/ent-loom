package com.entloom.crud.core.runtime.meta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 实体关系图。
 */
public final class RelationGraph {
    /** 关联边列表。 */
    private final List<RelationEdge> edges;

    private RelationGraph(List<RelationEdge> edges) {
        this.edges = edges;
    }

    public static RelationGraph empty() {
        return of(Collections.<RelationEdge>emptyList());
    }

    public static RelationGraph of(Collection<RelationEdge> edges) {
        List<RelationEdge> copy = new ArrayList<RelationEdge>();
        if (edges != null) {
            for (RelationEdge edge : edges) {
                if (edge != null) {
                    copy.add(RelationEdge.immutableCopyOf(edge));
                }
            }
        }
        return new RelationGraph(Collections.unmodifiableList(copy));
    }

    public List<RelationEdge> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    public List<RelationEdge> outgoingOf(Class<?> root) {
        return Collections.unmodifiableList(
            edges.stream().filter(e -> e != null && Objects.equals(e.getFromEntity(), root)).collect(Collectors.toList())
        );
    }
}
