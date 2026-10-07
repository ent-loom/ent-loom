package com.entloom.crud.core.capability.dao;

import com.entloom.crud.core.exception.PermissionDeniedException;
import java.lang.reflect.Method;

/**
 * 创建绑定实体和访问范围的 DAO。
 */
public interface EntityDaoFactory {
    /**
     * 创建指定实体和不可变访问范围下的 DAO。
     *
     * @param entityType 实体及主键类型描述
     * @param scope 可信应用层确定的访问范围
     * @param <T> 实体类型
     * @param <ID> 主键类型
     * @return 绑定范围的 DAO
     */
    <T, ID> EntityDao<T, ID> scoped(EntityType<T, ID> entityType, EntityAccessScope scope);

    /**
     * 创建消费按次业务授权结果的 DAO。
     *
     * <p>实现必须验证授权上下文与实体资源、目标和字段能力的适用性。默认实现保持拒绝，
     * 在具体存储完成完整范围与字段适配前，不能退回旧的 {@link #scoped(EntityType, EntityAccessScope)}。</p>
     *
     * @param entityType 实体及主键类型描述
     * @param authorization 本次按次授权上下文
     * @param <T> 实体类型
     * @param <ID> 主键类型
     * @return 按次授权 DAO
     */
    default <T, ID> EntityDao<T, ID> scopedAuthorized(
        EntityType<T, ID> entityType,
        EntityDaoAuthorizationContext authorization
    ) {
        throw new PermissionDeniedException("当前 EntityDaoFactory 尚不支持消费按次业务授权");
    }

    /**
     * 创建消费逐目标授权上下文的只读批量 DAO。
     *
     * <p>默认拒绝，存储适配器必须显式实现逐目标授权、目标绑定和范围消费，不能退回单目标或旧范围 DAO。</p>
     */
    default <T, ID> EntityDaoBatch<T, ID> scopedAuthorizedBatch(
        EntityType<T, ID> entityType,
        EntityDaoBatchAuthorizationContext authorization
    ) {
        throw new PermissionDeniedException("当前 EntityDaoFactory 尚不支持消费逐目标批量授权");
    }

    /**
     * 创建消费逐目标批量写入合同的 DAO。
     *
     * <p>默认拒绝；实现必须同时具备版本、事务回滚和最终审计能力，不能因已有只读批量
     * DAO 而推断具备写入能力。</p>
     */
    default <T, ID> EntityDaoBatchWrite<T, ID> scopedAuthorizedBatchWrite(
        EntityType<T, ID> entityType,
        EntityDaoBatchAuthorizationContext authorization
    ) {
        throw new PermissionDeniedException("当前 EntityDaoFactory 尚不支持逐目标批量写入");
    }

    /**
     * 启动期校验 DAO 自定义方法。实现可以根据 JDBC 方言和实体元数据解析 SQL。
     *
     * @param entityType DAO 绑定的实体类型
     * @param method 自定义方法
     */
    default void validateCustomMethod(EntityType<?, ?> entityType, Method method) {
        throw new UnsupportedOperationException("当前 EntityDaoFactory 不支持 DAO 自定义方法: " + method);
    }

    /**
     * 在可信访问范围内执行 DAO 自定义方法。
     *
     * @param entityType DAO 绑定的实体类型
     * @param scope 本次调用的可信访问范围
     * @param method 自定义方法
     * @param args 方法参数
     * @return 方法结果
     */
    default Object invokeCustom(
        EntityType<?, ?> entityType,
        EntityAccessScope scope,
        Method method,
        Object[] args
    ) {
        throw new UnsupportedOperationException("当前 EntityDaoFactory 不支持 DAO 自定义方法: " + method);
    }
}
