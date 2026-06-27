package com.entloom.crud.core.foundation.read.relation;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.JoinType;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.enums.RelationScope;
import com.entloom.meta.enums.RelationCardinality;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 关系边推断与匹配解析器。
 */
final class RelationEdgeInferenceResolver {
    private final EntityMetaRegistry metaRegistry;

    RelationEdgeInferenceResolver(EntityMetaRegistry metaRegistry) {
        this.metaRegistry = metaRegistry;
    }

    /**
     * 查找 source -> target 的候选关系边（包含图定义与反向推断）。
     */
    List<RelationEdge> findCandidateEdges(Class<?> sourceType, Class<?> targetType, RelationGraph relationGraph) {
        List<RelationEdge> matched = new ArrayList<RelationEdge>();
        for (RelationEdge edge : relationGraph.getEdges()) {
            if (Objects.equals(edge.getFromEntity(), sourceType) && Objects.equals(edge.getToEntity(), targetType)) {
                matched.add(edge);
            }
        }
        matched.addAll(inferReferenceEdges(sourceType, targetType, relationGraph));
        return matched;
    }

    /**
     * 从根实体的 outgoing edges 中解析具体 requested relation。
     */
    RelationEdge resolveEdge(List<RelationEdge> outgoingEdges, String requestedRelation) {
        List<RelationEdge> matchedEdges = outgoingEdges.stream()
            .filter(edge -> matches(edge, requestedRelation))
            .collect(Collectors.toList());
        if (matchedEdges.isEmpty()) {
            throw new ValidationException("未找到关联关系: " + requestedRelation);
        }
        if (matchedEdges.size() > 1) {
            throw new CrudException(
                CrudErrorCode.ENTITY_SCOPE_ILLEGAL,
                "关联关系不明确: " + requestedRelation + "，请使用 expandRelations 显式指定 relationField"
            );
        }
        return matchedEdges.get(0);
    }

    /**
     * 按关系边关键字段去重。
     */
    List<RelationEdge> deduplicateEdges(List<RelationEdge> edges) {
        Map<String, RelationEdge> dedup = new LinkedHashMap<String, RelationEdge>();
        for (RelationEdge edge : edges) {
            dedup.put(edgeKey(edge), edge);
        }
        return new ArrayList<RelationEdge>(dedup.values());
    }

    private List<RelationEdge> inferReferenceEdges(Class<?> sourceType, Class<?> targetType, RelationGraph relationGraph) {
        if (sourceType == null || targetType == null) {
            return new ArrayList<RelationEdge>();
        }
        List<RelationEdge> resolved = new ArrayList<RelationEdge>();
        for (Field field : getAllFields(sourceType)) {
            if (!isDirectRelationField(field, targetType)) {
                continue;
            }
            RelationEdge inferred = inferReferenceEdge(sourceType, targetType, field, relationGraph);
            if (inferred != null) {
                resolved.add(inferred);
            }
        }
        return resolved;
    }

    private RelationEdge inferReferenceEdge(
        Class<?> sourceType,
        Class<?> targetType,
        Field relationField,
        RelationGraph relationGraph
    ) {
        List<RelationEdge> reverseEdges = relationGraph == null
            ? new ArrayList<RelationEdge>()
            : relationGraph.getEdges().stream()
            .filter(edge -> Objects.equals(edge.getFromEntity(), targetType))
            .filter(edge -> Objects.equals(edge.getToEntity(), sourceType))
            .collect(Collectors.toList());
        if (reverseEdges.isEmpty() && metaRegistry != null) {
            RelationGraph reverseGraph = metaRegistry.getRelationGraph(targetType);
            reverseEdges = reverseGraph.outgoingOf(targetType).stream()
                .filter(edge -> Objects.equals(edge.getToEntity(), sourceType))
                .collect(Collectors.toList());
        }
        if (reverseEdges.isEmpty()) {
            return null;
        }

        RelationEdge reverseEdge = selectReverseEdge(reverseEdges, relationField, sourceType, targetType);
        if (reverseEdge == null) {
            return null;
        }

        RelationEdge edge = new RelationEdge();
        edge.setFromEntity(sourceType);
        edge.setToEntity(targetType);
        edge.setRelationField(relationField.getName());
        edge.setFromField(reverseEdge.getToField());
        edge.setToField(reverseEdge.getFromField());
        edge.setScope(reverseEdge.getScope() == null ? RelationScope.LOCAL_DB : reverseEdge.getScope());
        edge.setJoinKind(reverseEdge.getJoinKind() == null ? JoinType.LEFT : reverseEdge.getJoinKind());
        edge.setCardinality(Collection.class.isAssignableFrom(relationField.getType())
            ? RelationCardinality.ONE_TO_MANY
            : RelationCardinality.ONE_TO_ONE);
        return edge;
    }

    private RelationEdge selectReverseEdge(
        List<RelationEdge> reverseEdges,
        Field relationField,
        Class<?> sourceType,
        Class<?> targetType
    ) {
        if (reverseEdges.size() == 1) {
            return reverseEdges.get(0);
        }
        if (metaRegistry == null) {
            return null;
        }
        String preferredField = relationField.getName() + "Id";
        for (RelationEdge edge : reverseEdges) {
            if (preferredField.equals(edge.getToField())) {
                return edge;
            }
        }

        EntityMeta sourceMeta = metaRegistry.getEntityMeta(sourceType);
        EntityMeta targetMeta = metaRegistry.getEntityMeta(targetType);
        if (sourceMeta.getAllowedFields().contains(targetMeta.getIdField())) {
            for (RelationEdge edge : reverseEdges) {
                if (targetMeta.getIdField().equals(edge.getToField())) {
                    return edge;
                }
            }
        }
        return null;
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

    private boolean matches(RelationEdge edge, String requestedRelation) {
        if (equalsIgnoreCase(edge.getRelationField(), requestedRelation)) {
            return true;
        }
        Class<?> toEntity = edge.getToEntity();
        return equalsIgnoreCase(toEntity.getName(), requestedRelation)
            || equalsIgnoreCase(toEntity.getSimpleName(), requestedRelation)
            || equalsIgnoreCase(toEntity.getSimpleName().replace("Entity", ""), requestedRelation);
    }

    private boolean isDirectRelationField(Field field, Class<?> targetType) {
        if (field == null || targetType == null) {
            return false;
        }
        if (Objects.equals(field.getType(), targetType)) {
            return true;
        }
        return Collection.class.isAssignableFrom(field.getType()) && isCollectionElementType(field, targetType);
    }

    private boolean isCollectionElementType(Field field, Class<?> childType) {
        Type type = field.getGenericType();
        if (!(type instanceof ParameterizedType)) {
            return false;
        }
        ParameterizedType parameterizedType = (ParameterizedType) type;
        Type[] args = parameterizedType.getActualTypeArguments();
        return args.length == 1 && Objects.equals(args[0], childType);
    }

    private List<Field> getAllFields(Class<?> entityClass) {
        List<Field> fields = new ArrayList<Field>();
        Class<?> current = entityClass;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
