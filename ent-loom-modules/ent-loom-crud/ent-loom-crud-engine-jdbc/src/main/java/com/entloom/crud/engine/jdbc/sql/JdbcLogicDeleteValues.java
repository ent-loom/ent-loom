package com.entloom.crud.engine.jdbc.sql;

import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;

/**
 * 解析 JDBC 旧执行路径使用的逻辑删除状态值。
 *
 * <p>已注册的运行时元数据必须显式配置状态值；仅为保留直接构造旧测试元数据的默认 0/1
 * 行为，未配置时按字段类型提供兼容默认值。</p>
 */
public final class JdbcLogicDeleteValues {
    private JdbcLogicDeleteValues() {
    }

    /** 获取未删除状态值。 */
    public static Object notDeleted(EntityMeta meta) {
        return resolve(meta, false);
    }

    /** 获取已删除状态值。 */
    public static Object deleted(EntityMeta meta) {
        return resolve(meta, true);
    }

    private static Object resolve(EntityMeta meta, boolean deleted) {
        if (meta == null || meta.getLogicDeleteField() == null || meta.getLogicDeleteField().trim().isEmpty()) {
            return null;
        }
        Object notDeletedValue = meta.getLogicDeleteNotDeletedValue();
        Object deletedValue = meta.getLogicDeleteDeletedValue();
        if ((notDeletedValue == null) != (deletedValue == null)) {
            throw new ValidationException("逻辑删除未删除值和已删除值必须同时配置: " + meta.getEntityName());
        }
        if (meta.hasExplicitLogicDeleteValues()) {
            return deleted ? deletedValue : notDeletedValue;
        }

        EntityFieldMeta field = meta.resolveFieldMeta(meta.getLogicDeleteField());
        if (field == null || field.getJavaType() == null) {
            throw new ValidationException("逻辑删除字段未注册: " + meta.getEntityName());
        }
        Class<?> type = wrap(field.getJavaType());
        if (type == Boolean.class) {
            return Boolean.valueOf(deleted);
        }
        if (Number.class.isAssignableFrom(type)) {
            return Integer.valueOf(deleted ? 1 : 0);
        }
        throw new ValidationException("逻辑删除字段类型不支持默认状态值: " + meta.getEntityName());
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == Boolean.TYPE) {
            return Boolean.class;
        }
        if (type == Byte.TYPE) {
            return Byte.class;
        }
        if (type == Short.TYPE) {
            return Short.class;
        }
        if (type == Integer.TYPE) {
            return Integer.class;
        }
        if (type == Long.TYPE) {
            return Long.class;
        }
        if (type == Float.TYPE) {
            return Float.class;
        }
        if (type == Double.TYPE) {
            return Double.class;
        }
        return type;
    }
}
