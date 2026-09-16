package com.entloom.crud.core.runtime.meta.impl;

import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.runtime.model.CrudRuntimeEntityModel;
import com.entloom.crud.core.runtime.model.CrudRuntimeModel;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 基于统一 CRUD 运行时模型的实体元数据注册表。
 */
public class CrudRuntimeModelBackedEntityMetaRegistry implements EntityMetaRegistry {
    private final Map<Class<?>, EntityMeta> entityMetaMap;
    private final Map<String, Class<?>> resourceTypeMap;
    private final Map<Class<?>, Map<String, String>> columnToFieldMap;
    private final Map<Class<?>, RelationGraph> relationGraphs;

    public CrudRuntimeModelBackedEntityMetaRegistry(CrudRuntimeModel runtimeModel) {
        if (runtimeModel == null) {
            throw new ValidationException("CrudRuntimeModel 不能为空");
        }
        this.entityMetaMap = buildEntityMetas(runtimeModel);
        validateEntityMetas(entityMetaMap);
        this.resourceTypeMap = buildResourceTypeMap(entityMetaMap);
        this.columnToFieldMap = buildColumnToFieldMap(entityMetaMap);
        List<RelationEdge> relationEdges = buildRelationEdges(runtimeModel, entityMetaMap);
        this.relationGraphs = buildRelationGraphs(entityMetaMap, relationEdges);
        validateOrThrow();
    }

    @Override
    public EntityMeta getEntityMeta(Class<?> entityType) {
        EntityMeta meta = entityMetaMap.get(entityType);
        if (meta == null) {
            throw new ValidationException("未找到实体元数据: " + (entityType == null ? "null" : entityType.getName()));
        }
        return meta;
    }

    @Override
    public ResourceDescriptor getResourceDescriptor(Class<?> entityType) {
        return getEntityMeta(entityType).getResourceDescriptor();
    }

    @Override
    public RelationGraph getRelationGraph(Class<?> rootType) {
        if (rootType == null) {
            return RelationGraph.empty();
        }
        RelationGraph graph = relationGraphs.get(rootType);
        if (graph == null) {
            throw new ValidationException("未找到实体元数据: " + rootType.getName());
        }
        return graph;
    }

    @Override
    public void validateOrThrow() {
        if (entityMetaMap.isEmpty()) {
            throw new ValidationException("CrudRuntimeModel 未提供任何实体元数据");
        }
        validateEntityMetas(entityMetaMap);
    }

    public Class<?> resolveEntityType(String resourceCode) {
        if (resourceCode == null) {
            return null;
        }
        Class<?> direct = resourceTypeMap.get(resourceCode.trim());
        if (direct != null) {
            return direct;
        }
        return resourceTypeMap.get(resourceCode.trim().toLowerCase(Locale.ROOT));
    }

    public String resolveFieldByColumn(Class<?> entityType, String column) {
        if (entityType == null || column == null || column.trim().isEmpty()) {
            return null;
        }
        Map<String, String> lookup = columnToFieldMap.get(entityType);
        if (lookup == null || lookup.isEmpty()) {
            return null;
        }
        return lookup.get(column.trim().toLowerCase(Locale.ROOT));
    }

    private Map<Class<?>, EntityMeta> buildEntityMetas(CrudRuntimeModel runtimeModel) {
        LinkedHashMap<Class<?>, EntityMeta> result = new LinkedHashMap<Class<?>, EntityMeta>();
        for (CrudRuntimeEntityModel entity : runtimeModel.getEntities().values()) {
            if (entity == null || entity.getEntityType() == null) {
                continue;
            }
            EntityMeta previous = result.putIfAbsent(entity.getEntityType(), entity.toEntityMeta());
            if (previous != null) {
                throw new ValidationException("实体元数据重复: " + entity.getEntityType().getName());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 在注册表创建阶段收紧实体元数据，避免字段问题延迟到首个请求才暴露。
     */
    private void validateEntityMetas(Map<Class<?>, EntityMeta> entityMetas) {
        for (EntityMeta meta : entityMetas.values()) {
            if (meta == null || meta.getEntityType() == null) {
                throw new ValidationException("实体元数据 entityType 不能为空");
            }
            if (meta.getResourceDescriptor() == null) {
                throw new ValidationException("实体元数据 resourceDescriptor 不能为空: " + meta.getEntityType().getName());
            }
            if (isBlank(meta.getTable())) {
                throw new ValidationException("实体表名不能为空: " + meta.getEntityType().getName());
            }
            if (isBlank(meta.getIdField())) {
                throw new ValidationException("实体主键字段不能为空: " + meta.getEntityType().getName());
            }
            if (meta.getFieldMetas() == null || meta.getFieldMetas().isEmpty()) {
                throw new ValidationException("实体未提供字段元数据: " + meta.getEntityType().getName());
            }

            Set<String> columns = new LinkedHashSet<String>();
            for (Map.Entry<String, com.entloom.crud.core.runtime.meta.EntityFieldMeta> entry
                : meta.getFieldMetas().entrySet()) {
                String fieldName = entry.getKey();
                com.entloom.crud.core.runtime.meta.EntityFieldMeta field = entry.getValue();
                if (isBlank(fieldName) || field == null) {
                    throw new ValidationException("实体字段元数据不能为空: " + meta.getEntityType().getName());
                }
                if (!fieldName.equals(field.getFieldName()) || isBlank(field.getColumnName())) {
                    throw new ValidationException("实体字段名或列名无效: "
                        + meta.getEntityType().getName() + "." + fieldName);
                }
                if (field.getJavaType() == null || field.getJavaType() == Void.TYPE) {
                    throw new ValidationException("实体字段 Java 类型不能为空: "
                        + meta.getEntityType().getName() + "." + fieldName);
                }
                String normalizedColumn = field.getColumnName().trim().toLowerCase(Locale.ROOT);
                if (!columns.add(normalizedColumn)) {
                    throw new ValidationException("实体列名重复: "
                        + meta.getEntityType().getName() + "." + normalizedColumn);
                }
            }

            validateIdentity(meta);
            validateLogicDelete(meta);
        }
    }

    private void validateIdentity(EntityMeta meta) {
        String idField = meta.getIdField().trim();
        if (meta.getIdPolicy() == null) {
            throw new ValidationException("实体主键策略不能为空: " + meta.getEntityType().getName());
        }
        if (meta.getIdPolicy() == com.entloom.crud.core.runtime.meta.EntityIdPolicy.COMPOSITE
            || idField.indexOf(',') >= 0) {
            for (String part : idField.split(",")) {
                if (isBlank(part) || meta.resolveFieldMeta(part.trim()) == null) {
                    throw new ValidationException("联合主键字段未注册: "
                        + meta.getEntityType().getName() + "." + part);
                }
            }
            return;
        }
        com.entloom.crud.core.runtime.meta.EntityFieldMeta idMeta = meta.resolveFieldMeta(idField);
        if (idMeta == null) {
            throw new ValidationException("主键字段未注册: " + meta.getEntityType().getName() + "." + idField);
        }
        if (meta.getIdPolicy() == com.entloom.crud.core.runtime.meta.EntityIdPolicy.GENERATED
            && !isSupportedGeneratedIdType(idMeta.getJavaType())) {
            throw new ValidationException("数据库生成主键类型不受支持: "
                + meta.getEntityType().getName() + "." + idField + " -> " + idMeta.getJavaType().getName());
        }
    }

    private void validateLogicDelete(EntityMeta meta) {
        if (isBlank(meta.getLogicDeleteField())) {
            return;
        }
        com.entloom.crud.core.runtime.meta.EntityFieldMeta field = meta.resolveFieldMeta(meta.getLogicDeleteField().trim());
        if (field == null) {
            throw new ValidationException("逻辑删除字段未注册: "
                + meta.getEntityType().getName() + "." + meta.getLogicDeleteField());
        }
        Class<?> type = wrap(field.getJavaType());
        if (type != Boolean.class && !Number.class.isAssignableFrom(type)) {
            throw new ValidationException("逻辑删除字段类型必须是布尔或数值类型: "
                + meta.getEntityType().getName() + "." + field.getFieldName());
        }
    }

    private boolean isSupportedGeneratedIdType(Class<?> type) {
        Class<?> wrapped = wrap(type);
        return wrapped == String.class
            || wrapped == Long.class
            || wrapped == Integer.class
            || wrapped == Short.class
            || wrapped == BigInteger.class
            || wrapped == BigDecimal.class;
    }

    private Class<?> wrap(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return type;
        }
        if (type == Long.TYPE) {
            return Long.class;
        }
        if (type == Integer.TYPE) {
            return Integer.class;
        }
        if (type == Short.TYPE) {
            return Short.class;
        }
        if (type == Byte.TYPE) {
            return Byte.class;
        }
        if (type == Boolean.TYPE) {
            return Boolean.class;
        }
        if (type == Character.TYPE) {
            return Character.class;
        }
        if (type == Float.TYPE) {
            return Float.class;
        }
        if (type == Double.TYPE) {
            return Double.class;
        }
        return type;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Map<String, Class<?>> buildResourceTypeMap(Map<Class<?>, EntityMeta> entityMetas) {
        LinkedHashMap<String, Class<?>> result = new LinkedHashMap<String, Class<?>>();
        for (EntityMeta entityMeta : entityMetas.values()) {
            ResourceDescriptor descriptor = entityMeta.getResourceDescriptor();
            registerResourceCode(result, descriptor.getResourceCode(), entityMeta.getEntityType());
            for (String alias : descriptor.getAliases()) {
                registerResourceCode(result, alias, entityMeta.getEntityType());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private void registerResourceCode(Map<String, Class<?>> result, String code, Class<?> entityType) {
        if (code == null || code.trim().isEmpty() || entityType == null) {
            return;
        }
        registerResourceCodeVariant(result, code.trim(), entityType);
        registerResourceCodeVariant(result, code.trim().toLowerCase(Locale.ROOT), entityType);
    }

    private void registerResourceCodeVariant(Map<String, Class<?>> result, String code, Class<?> entityType) {
        Class<?> previous = result.putIfAbsent(code, entityType);
        if (previous != null && !previous.equals(entityType)) {
            throw new ValidationException("资源编码或别名重复: " + code);
        }
    }

    private Map<Class<?>, Map<String, String>> buildColumnToFieldMap(Map<Class<?>, EntityMeta> entityMetas) {
        LinkedHashMap<Class<?>, Map<String, String>> result = new LinkedHashMap<Class<?>, Map<String, String>>();
        for (EntityMeta entityMeta : entityMetas.values()) {
            LinkedHashMap<String, String> lookup = new LinkedHashMap<String, String>();
            for (Map.Entry<String, String> entry : entityMeta.getFieldToColumn().entrySet()) {
                String column = entry.getValue();
                if (column != null && !column.trim().isEmpty()) {
                    lookup.put(column.trim().toLowerCase(Locale.ROOT), entry.getKey());
                }
            }
            result.put(entityMeta.getEntityType(), Collections.unmodifiableMap(lookup));
        }
        return Collections.unmodifiableMap(result);
    }

    private List<RelationEdge> buildRelationEdges(
        CrudRuntimeModel runtimeModel,
        Map<Class<?>, EntityMeta> entityMetas
    ) {
        java.util.ArrayList<RelationEdge> edges = new java.util.ArrayList<RelationEdge>();
        for (RelationEdge edge : runtimeModel.relationEdges()) {
            if (edge != null) {
                validateRelationEdge(edge, entityMetas);
                edges.add(RelationEdge.immutableCopyOf(edge));
            }
        }
        return Collections.unmodifiableList(edges);
    }

    private Map<Class<?>, RelationGraph> buildRelationGraphs(
        Map<Class<?>, EntityMeta> entityMetas,
        List<RelationEdge> relationEdges
    ) {
        LinkedHashMap<Class<?>, RelationGraph> graphs = new LinkedHashMap<Class<?>, RelationGraph>();
        for (Class<?> rootType : entityMetas.keySet()) {
            graphs.put(rootType, buildRelationGraph(rootType, relationEdges));
        }
        return Collections.unmodifiableMap(graphs);
    }

    private RelationGraph buildRelationGraph(Class<?> rootType, List<RelationEdge> relationEdges) {
        LinkedHashSet<RelationEdge> edges = new LinkedHashSet<RelationEdge>();
        Set<Class<?>> reachable = new LinkedHashSet<Class<?>>();
        Deque<Class<?>> queue = new ArrayDeque<Class<?>>();
        Set<String> edgeKeys = new LinkedHashSet<String>();
        reachable.add(rootType);
        queue.add(rootType);
        for (Class<?> sourceType = queue.pollFirst(); sourceType != null; sourceType = queue.pollFirst()) {
            for (RelationEdge edge : relationEdges) {
                boolean fromMatched = Objects.equals(edge.getFromEntity(), sourceType);
                boolean toMatched = Objects.equals(edge.getToEntity(), sourceType);
                if (!fromMatched && !toMatched) {
                    continue;
                }
                if (edgeKeys.add(edgeKey(edge))) {
                    edges.add(edge);
                }
                Class<?> fromType = edge.getFromEntity();
                Class<?> toType = edge.getToEntity();
                if (fromMatched && toType != null && reachable.add(toType)) {
                    queue.addLast(toType);
                }
                if (toMatched && fromType != null && reachable.add(fromType)) {
                    queue.addLast(fromType);
                }
            }
        }
        return RelationGraph.of(edges);
    }

    private void validateRelationEdge(RelationEdge edge, Map<Class<?>, EntityMeta> entityMetas) {
        if (edge.getFromEntity() == null || edge.getToEntity() == null) {
            throw new ValidationException("关系边 fromEntity/toEntity 不能为空");
        }
        EntityMeta fromMeta = entityMetas.get(edge.getFromEntity());
        if (fromMeta == null) {
            throw new ValidationException("关系起始实体未注册: " + edge.getFromEntity().getName());
        }
        EntityMeta toMeta = entityMetas.get(edge.getToEntity());
        if (toMeta == null) {
            throw new ValidationException("关系目标实体未注册: " + edge.getToEntity().getName());
        }
        validateRelationField(fromMeta, edge.getFromField(), "关系起始字段不存在: ");
        validateRelationField(toMeta, edge.getToField(), "关系目标字段不存在: ");
    }

    private void validateRelationField(EntityMeta entityMeta, String field, String messagePrefix) {
        if (field == null || field.trim().isEmpty()) {
            throw new ValidationException(messagePrefix + entityMeta.getEntityType().getName() + ".<empty>");
        }
        if (entityMeta.resolveFieldMeta(field.trim()) == null) {
            throw new ValidationException(messagePrefix + entityMeta.getEntityType().getName() + "." + field.trim());
        }
    }

    private String edgeKey(RelationEdge edge) {
        return String.valueOf(edge.getFromEntity())
            + "->"
            + String.valueOf(edge.getToEntity())
            + "#"
            + String.valueOf(edge.getRelationField())
            + "#"
            + String.valueOf(edge.getFromField())
            + "#"
            + String.valueOf(edge.getToField());
    }
}
