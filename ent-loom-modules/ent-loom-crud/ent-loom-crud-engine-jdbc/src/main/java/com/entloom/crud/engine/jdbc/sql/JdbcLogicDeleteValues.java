package com.entloom.crud.engine.jdbc.sql;

import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;

/**
 * 解析 JDBC 旧执行路径使用的逻辑删除状态值。
 *
 * <p>逻辑删除实体必须通过运行时元数据显式配置两个状态值；未配置时直接拒绝，避免
 * 不同 JDBC 路径隐式采用不同状态语义。</p>
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
        if (!meta.hasExplicitLogicDeleteValues()) {
            throw new ValidationException("逻辑删除状态值必须显式配置: " + meta.getEntityName());
        }
        return deleted ? deletedValue : notDeletedValue;
    }
}
