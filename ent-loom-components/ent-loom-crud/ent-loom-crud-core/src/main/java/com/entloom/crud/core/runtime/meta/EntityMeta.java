package com.entloom.crud.core.runtime.meta;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/**
 * 实体元数据。
 */
@Getter
public class EntityMeta {
    /** 实体类型。 */
    private final Class<?> entityType;
    /** 资源身份描述。 */
    private final ResourceDescriptor resourceDescriptor;
    /** 实体名称。 */
    private final String entityName;
    /** 表名。 */
    private final String table;
    /** 主键字段名。 */
    private final String idField;
    /** 主键写入策略。 */
    private final EntityIdPolicy idPolicy;
    /** 逻辑删除字段名。 */
    private final String logicDeleteField;
    /** 归属服务名。 */
    private final String ownerService;
    /** 字段到列名的映射。 */
    private final Map<String, String> fieldToColumn;
    /** 允许访问的字段集合。 */
    private final Set<String> allowedFields;
    /** 字段 schema。 */
    private final Map<String, EntityFieldMeta> fieldMetas;

    public EntityMeta(
        Class<?> entityType,
        ResourceDescriptor resourceDescriptor,
        String table,
        String idField,
        String logicDeleteField,
        Map<String, EntityFieldMeta> fieldMetas
    ) {
        this(entityType, resourceDescriptor, table, idField, EntityIdPolicy.EXPLICIT, logicDeleteField, fieldMetas);
    }

    public EntityMeta(
        Class<?> entityType,
        ResourceDescriptor resourceDescriptor,
        String table,
        String idField,
        EntityIdPolicy idPolicy,
        String logicDeleteField,
        Map<String, EntityFieldMeta> fieldMetas
    ) {
        if (resourceDescriptor == null) {
            throw new IllegalArgumentException("resourceDescriptor 不能为空");
        }
        if (resourceDescriptor.getEntityType() != null
            && entityType != null
            && !resourceDescriptor.getEntityType().equals(entityType)) {
            throw new IllegalArgumentException("resourceDescriptor.entityType 必须等于 entityType");
        }
        this.entityType = entityType;
        this.resourceDescriptor = resourceDescriptor;
        this.entityName = resourceDescriptor.getResourceCode();
        this.table = table;
        this.idField = idField;
        this.idPolicy = idPolicy == null ? EntityIdPolicy.EXPLICIT : idPolicy;
        this.logicDeleteField = logicDeleteField;
        this.ownerService = resourceDescriptor.getOwnerService();
        LinkedHashMap<String, EntityFieldMeta> copy = fieldMetas == null
            ? new LinkedHashMap<String, EntityFieldMeta>()
            : new LinkedHashMap<String, EntityFieldMeta>(fieldMetas);
        LinkedHashMap<String, String> columns = new LinkedHashMap<String, String>();
        java.util.LinkedHashSet<String> allowed = new java.util.LinkedHashSet<String>();
        for (Map.Entry<String, EntityFieldMeta> entry : copy.entrySet()) {
            allowed.add(entry.getKey());
            columns.put(entry.getKey(), entry.getValue().getColumnName());
        }
        this.fieldMetas = copy;
        this.fieldToColumn = columns;
        this.allowedFields = allowed;
    }

    public Map<String, String> getFieldToColumn() {
        return Collections.unmodifiableMap(fieldToColumn);
    }

    public String resolveColumn(String field) {
        return fieldToColumn.get(field);
    }

    public Set<String> getAllowedFields() {
        return Collections.unmodifiableSet(allowedFields);
    }

    public Map<String, EntityFieldMeta> getFieldMetas() {
        return Collections.unmodifiableMap(fieldMetas);
    }

    public EntityFieldMeta resolveFieldMeta(String field) {
        return fieldMetas.get(field);
    }
}
