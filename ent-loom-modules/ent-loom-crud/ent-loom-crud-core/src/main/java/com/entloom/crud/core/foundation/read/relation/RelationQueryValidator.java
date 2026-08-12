package com.entloom.crud.core.foundation.read.relation;

import com.entloom.crud.enums.RelationScope;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 关系查询校验器。
 */
public class RelationQueryValidator {
    private final RelationQueryPolicy policy;
    private final RelationLoaderRegistry loaderRegistry;

    public RelationQueryValidator() {
        this(RelationQueryPolicy.defaultPolicy(), RelationLoaderRegistry.empty());
    }

    public RelationQueryValidator(RelationQueryPolicy policy, RelationLoaderRegistry loaderRegistry) {
        this.policy = policy == null ? RelationQueryPolicy.defaultPolicy() : policy;
        this.loaderRegistry = loaderRegistry == null ? RelationLoaderRegistry.empty() : loaderRegistry;
    }

    public void validate(QuerySpec<?> spec, RelationQueryModel model) {
        if (model == null || model.getExpandEdges().isEmpty()) {
            return;
        }
        List<RelationEdge> expandEdges = model.getExpandEdges();
        if (expandEdges.size() > policy.getMaxExpandEdges()) {
            throw new ValidationException("关系展开数量超过限制: " + policy.getMaxExpandEdges());
        }
        if (maxDepth(expandEdges) > policy.getMaxDepth()) {
            throw new ValidationException("关系展开深度超过限制: " + policy.getMaxDepth());
        }
        if (!policy.isAllowCycles() && hasCycle(expandEdges)) {
            throw new ValidationException("关系展开不允许出现循环");
        }
        for (RelationEdge edge : expandEdges) {
            if (edge.getScope() != RelationScope.LOCAL_DB) {
                validateExternalLoader(edge);
            }
        }
    }

    private void validateExternalLoader(RelationEdge edge) {
        if (!policy.isAllowExternalLoaders()) {
            throw new ValidationException("默认关系查询仅支持 LOCAL_DB 范围");
        }
        if (!loaderRegistry.hasLoader(edge)) {
            throw new ValidationException("未找到可处理关系的 RelationLoader: " + relationName(edge));
        }
    }

    private boolean hasCycle(List<RelationEdge> edges) {
        Map<Class<?>, List<Class<?>>> graph = new HashMap<Class<?>, List<Class<?>>>();
        Set<Class<?>> nodes = new HashSet<Class<?>>();
        for (RelationEdge edge : edges) {
            if (edge == null || edge.getFromEntity() == null || edge.getToEntity() == null) {
                continue;
            }
            nodes.add(edge.getFromEntity());
            nodes.add(edge.getToEntity());
            List<Class<?>> targets = graph.get(edge.getFromEntity());
            if (targets == null) {
                targets = new ArrayList<Class<?>>();
                graph.put(edge.getFromEntity(), targets);
            }
            targets.add(edge.getToEntity());
        }
        Set<Class<?>> visiting = new HashSet<Class<?>>();
        Set<Class<?>> visited = new HashSet<Class<?>>();
        for (Class<?> node : nodes) {
            if (visit(node, graph, visiting, visited)) {
                return true;
            }
        }
        return false;
    }

    private int maxDepth(List<RelationEdge> edges) {
        Map<Class<?>, List<Class<?>>> graph = new HashMap<Class<?>, List<Class<?>>>();
        Set<Class<?>> fromNodes = new HashSet<Class<?>>();
        Set<Class<?>> toNodes = new HashSet<Class<?>>();
        for (RelationEdge edge : edges) {
            if (edge == null || edge.getFromEntity() == null || edge.getToEntity() == null) {
                continue;
            }
            fromNodes.add(edge.getFromEntity());
            toNodes.add(edge.getToEntity());
            List<Class<?>> targets = graph.get(edge.getFromEntity());
            if (targets == null) {
                targets = new ArrayList<Class<?>>();
                graph.put(edge.getFromEntity(), targets);
            }
            targets.add(edge.getToEntity());
        }
        int max = 0;
        for (Class<?> node : fromNodes) {
            if (!toNodes.contains(node)) {
                max = Math.max(max, maxDepthFrom(node, graph, new HashSet<Class<?>>()));
            }
        }
        if (max == 0) {
            for (Class<?> node : fromNodes) {
                max = Math.max(max, maxDepthFrom(node, graph, new HashSet<Class<?>>()));
            }
        }
        return max;
    }

    private int maxDepthFrom(Class<?> node, Map<Class<?>, List<Class<?>>> graph, Set<Class<?>> visiting) {
        if (node == null || !visiting.add(node)) {
            return 0;
        }
        int max = 0;
        List<Class<?>> targets = graph.get(node);
        if (targets != null) {
            for (Class<?> target : targets) {
                max = Math.max(max, 1 + maxDepthFrom(target, graph, visiting));
            }
        }
        visiting.remove(node);
        return max;
    }

    private boolean visit(
        Class<?> node,
        Map<Class<?>, List<Class<?>>> graph,
        Set<Class<?>> visiting,
        Set<Class<?>> visited
    ) {
        if (visited.contains(node)) {
            return false;
        }
        if (!visiting.add(node)) {
            return true;
        }
        List<Class<?>> targets = graph.get(node);
        if (targets != null) {
            for (Class<?> target : targets) {
                if (visit(target, graph, visiting, visited)) {
                    return true;
                }
            }
        }
        visiting.remove(node);
        visited.add(node);
        return false;
    }

    private String relationName(RelationEdge edge) {
        if (edge == null) {
            return "null";
        }
        if (edge.getRelationField() != null && !edge.getRelationField().trim().isEmpty()) {
            return edge.getRelationField();
        }
        return String.valueOf(edge.getFromEntity()) + "->" + String.valueOf(edge.getToEntity());
    }
}
