package com.entloom.crud.core.capability.dao;

import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.admission.AccessContextScope;
import java.util.Objects;

/**
 * DAO 直接调用门禁。
 *
 * <p>当前 {@link EntityDao} 只携带旧的 {@link EntityAccessScope}，没有按次业务操作、目标和字段授权结果。
 * 因此在已经打开业务授权作用域时，直接 DAO 调用必须拒绝，避免把行范围伪装成完整业务授权。</p>
 */
public final class EntityDaoAuthorizationGuard {
    private EntityDaoAuthorizationGuard() {
    }

    /**
     * 要求当前调用不处于需要业务授权的作用域。
     *
     * @param entityType DAO 实体类型
     */
    public static void requireDirectAccessAllowed(EntityType<?, ?> entityType) {
        Objects.requireNonNull(entityType, "DAO 实体类型不能为空");
        if (AccessContextScope.isAuthorizationRequired()) {
            throw new PermissionDeniedException(
                "当前 DAO 入口没有单次业务授权，不能在受治理访问作用域内直接执行: "
                    + entityType.getEntityClass().getName()
                    + "；请改用受授权的 QueryGateway 或 CommandGateway"
            );
        }
    }
}
