package com.entloom.crud.core.foundation.read.relation;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关系路径解析器。
 */
public class PathResolver {
    private final RelationEdgeInferenceResolver edgeResolver;

    public PathResolver() {
        this(null);
    }

    public PathResolver(EntityMetaRegistry metaRegistry) {
        this.edgeResolver = new RelationEdgeInferenceResolver(metaRegistry);
    }

    /**
     * 解析请求中的 expandRelations/entityCodes，输出统一关系查询模型。
     */
    public RelationQueryModel resolve(QuerySpec<?> spec, RelationGraph relationGraph) {
        List<String> requestedRelations = resolveRequestedRelations(spec);
        if (requestedRelations.isEmpty()) {
            validateEntityScope(spec, relationGraph, new ArrayList<RelationEdge>());
            return new RelationQueryModel(requestedRelations, new ArrayList<RelationEdge>());
        }

        if (hasExplicitExpandRelations(spec)) {
            return resolveExplicitExpandRelations(spec, relationGraph, requestedRelations);
        }

        if (spec.getEntityClasses() != null && spec.getEntityClasses().size() > 1) {
            return resolveEntityClassSequence(spec, relationGraph, requestedRelations);
        }

        List<RelationEdge> outgoingEdges = relationGraph.outgoingOf(spec.getRootType());
        Map<String, RelationEdge> dedup = new LinkedHashMap<String, RelationEdge>();
        for (String requestedRelation : requestedRelations) {
            RelationEdge edge = edgeResolver.resolveEdge(outgoingEdges, requestedRelation);
            dedup.put(edgeKey(edge), edge);
        }
        List<RelationEdge> expandEdges = new ArrayList<RelationEdge>(dedup.values());
        validateEntityScope(spec, relationGraph, expandEdges);
        return new RelationQueryModel(requestedRelations, expandEdges);
    }

    private RelationQueryModel resolveExplicitExpandRelations(
        QuerySpec<?> spec,
        RelationGraph relationGraph,
        List<String> requestedRelations
    ) {
        List<RelationEdge> outgoingEdges = relationGraph.outgoingOf(spec.getRootType());
        Map<String, RelationEdge> dedup = new LinkedHashMap<String, RelationEdge>();
        for (String requestedRelation : requestedRelations) {
            RelationEdge edge = edgeResolver.resolveEdge(outgoingEdges, requestedRelation);
            dedup.put(edgeKey(edge), edge);
        }
        List<RelationEdge> expandEdges = new ArrayList<RelationEdge>(dedup.values());
        validateEntityScope(spec, relationGraph, expandEdges);
        return new RelationQueryModel(requestedRelations, expandEdges);
    }

    private RelationQueryModel resolveEntityClassSequence(
        QuerySpec<?> spec,
        RelationGraph relationGraph,
        List<String> requestedRelations
    ) {
        List<RelationEdge> expandEdges = new ArrayList<RelationEdge>();
        Set<Class<?>> reachable = new LinkedHashSet<Class<?>>();
        reachable.add(spec.getRootType());

        List<Class<?>> entityClasses = spec.getEntityClasses();
        for (int i = 1; i < entityClasses.size(); i++) {
            Class<?> entityClass = entityClasses.get(i);
            if (entityClass == null) {
                continue;
            }
            List<RelationEdge> matchedEdges = new ArrayList<RelationEdge>();
            for (Class<?> sourceType : reachable) {
                matchedEdges.addAll(edgeResolver.findCandidateEdges(sourceType, entityClass, relationGraph));
            }
            matchedEdges = edgeResolver.deduplicateEdges(matchedEdges);
            if (matchedEdges.isEmpty()) {
                throw new CrudException(
                    CrudErrorCode.ENTITY_SCOPE_ILLEGAL,
                    "entityCodes 超出根实体允许关系范围: " + entityClass.getSimpleName()
                );
            }
            if (matchedEdges.size() > 1) {
                throw new CrudException(
                    CrudErrorCode.ENTITY_SCOPE_ILLEGAL,
                    "entityCodes 中的关联不明确: " + entityClass.getSimpleName()
                        + "，请通过 entityCodes 调整顺序或改用定制 QueryHandler"
                );
            }
            RelationEdge resolved = matchedEdges.get(0);
            expandEdges.add(resolved);
            reachable.add(entityClass);
        }

        validateEntityScope(spec, relationGraph, expandEdges);
        return new RelationQueryModel(requestedRelations, expandEdges);
    }

    private String edgeKey(RelationEdge edge) {
        return edge.getFromEntity().getName()
            + "->"
            + edge.getToEntity().getName()
            + "#"
            + String.valueOf(edge.getRelationField())
            + "#"
            + String.valueOf(edge.getFromField())
            + "#"
            + String.valueOf(edge.getToField());
    }

    private List<String> resolveRequestedRelations(QuerySpec<?> spec) {
        List<String> resolved = new ArrayList<String>();
        if (spec.getExpandRelations() != null && !spec.getExpandRelations().isEmpty()) {
            for (String expandRelation : spec.getExpandRelations()) {
                if (isBlank(expandRelation)) {
                    continue;
                }
                resolved.add(expandRelation.trim());
            }
            return resolved;
        }
        if (spec.getEntityClasses() == null || spec.getEntityClasses().size() <= 1) {
            return resolved;
        }
        for (int i = 1; i < spec.getEntityClasses().size(); i++) {
            Class<?> entityClass = spec.getEntityClasses().get(i);
            if (entityClass != null) {
                resolved.add(entityClass.getName());
            }
        }
        return resolved;
    }

    /**
     * 校验 entityCodes 是否落在允许关系范围内。
     */
    private void validateEntityScope(
        QuerySpec<?> spec,
        RelationGraph relationGraph,
        List<RelationEdge> expandEdges
    ) {
        if (spec.getEntityClasses() == null || spec.getEntityClasses().size() <= 1) {
            return;
        }
        boolean hasExplicitExpandRelations = hasExplicitExpandRelations(spec);
        List<RelationEdge> rootOutgoingEdges = relationGraph.outgoingOf(spec.getRootType());
        for (int i = 1; i < spec.getEntityClasses().size(); i++) {
            Class<?> entityClass = spec.getEntityClasses().get(i);
            if (entityClass == null) {
                continue;
            }
            List<RelationEdge> matchedEdges = expandEdges.stream()
                .filter(edge -> entityClass.equals(edge.getToEntity()))
                .collect(Collectors.toList());
            if (matchedEdges.isEmpty()) {
                if (!hasExplicitExpandRelations) {
                    throw new CrudException(
                        CrudErrorCode.ENTITY_SCOPE_ILLEGAL,
                        "entityCodes 超出根实体允许关系范围: " + entityClass.getSimpleName()
                    );
                }
                List<RelationEdge> rootMatchedEdges = rootOutgoingEdges.stream()
                    .filter(edge -> entityClass.equals(edge.getToEntity()))
                    .collect(Collectors.toList());
                if (rootMatchedEdges.isEmpty()) {
                    throw new CrudException(
                        CrudErrorCode.ENTITY_SCOPE_ILLEGAL,
                        "entityCodes 超出根实体允许关系范围: " + entityClass.getSimpleName()
                    );
                }
            }

            if (hasExplicitExpandRelations && matchedEdges.isEmpty()) {
                throw new CrudException(
                    CrudErrorCode.ENTITY_SCOPE_ILLEGAL,
                    "entityCodes 与 expandRelations 不一致，未明确关联: " + entityClass.getSimpleName()
                );
            }
        }
    }

    private boolean hasExplicitExpandRelations(QuerySpec<?> spec) {
        return spec.getExpandRelations() != null && !spec.getExpandRelations().isEmpty();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
