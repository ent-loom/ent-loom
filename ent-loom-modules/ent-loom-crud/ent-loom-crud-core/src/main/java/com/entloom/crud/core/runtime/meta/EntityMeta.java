package com.entloom.crud.core.runtime.meta;

import com.entloom.crud.core.exception.ValidationException;
import com.entloom.meta.enums.role.DateTimeRole;
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
    /** 逻辑未删除值；逻辑删除字段为空时不使用。 */
    private final Object logicDeleteNotDeletedValue;
    /** 逻辑已删除值；逻辑删除字段为空时不使用。 */
    private final Object logicDeleteDeletedValue;
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
        this(
            entityType,
            resourceDescriptor,
            table,
            idField,
            idPolicy,
            logicDeleteField,
            null,
            null,
            fieldMetas
        );
    }

    /**
     * 创建包含显式逻辑删除值的实体元数据。
     *
     * @param logicDeleteNotDeletedValue 未删除值
     * @param logicDeleteDeletedValue 已删除值
     */
    public EntityMeta(
        Class<?> entityType,
        ResourceDescriptor resourceDescriptor,
        String table,
        String idField,
        EntityIdPolicy idPolicy,
        String logicDeleteField,
        Object logicDeleteNotDeletedValue,
        Object logicDeleteDeletedValue,
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
        this.logicDeleteNotDeletedValue = logicDeleteNotDeletedValue;
        this.logicDeleteDeletedValue = logicDeleteDeletedValue;
        this.ownerService = resourceDescriptor.getOwnerService();
        LinkedHashMap<String, EntityFieldMeta> copy = fieldMetas == null
            ? new LinkedHashMap<String, EntityFieldMeta>()
            : new LinkedHashMap<String, EntityFieldMeta>(fieldMetas);
        LinkedHashMap<String, String> columns = new LinkedHashMap<String, String>();
        java.util.LinkedHashSet<String> allowed = new java.util.LinkedHashSet<String>();
        for (Map.Entry<String, EntityFieldMeta> entry : copy.entrySet()) {
            EntityFieldMeta field = entry.getValue();
            if (field.isScopeField() && (field.isRelation() || entry.getKey().equals(idField))) {
                throw new ValidationException("治理字段必须是主键以外的持久化标量属性: " + entityName + "#" + entry.getKey());
            }
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

    /** 读取唯一创建时间字段；存在多个角色时拒绝猜测，避免排序取决于声明顺序。 */
    public String getCreatedTimeField() {
        String found = null;
        for (EntityFieldMeta field : fieldMetas.values()) {
            if (field.getDateTimeRole() != DateTimeRole.CREATED_TIME) continue;
            if (field.isRelation()) {
                throw new ValidationException("创建时间必须为普通字段: " + entityType.getName() + "#" + field.getFieldName());
            }
            if (found != null) {
                throw new ValidationException("实体创建时间角色不唯一: " + entityType.getName() + "#" + found + ", " + field.getFieldName());
            }
            found = field.getFieldName();
        }
        return found;
    }

    /** 是否已显式提供逻辑删除的两个状态值。 */
    public boolean hasExplicitLogicDeleteValues() {
        return logicDeleteNotDeletedValue != null && logicDeleteDeletedValue != null;
    }
}
