package com.entloom.crud.core.capability.dao;

import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.authorization.AuthorizationResult;
import com.entloom.crud.core.governance.authorization.BusinessAuthorizationService;
import com.entloom.crud.core.exception.ValidationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * 将真实业务授权结果绑定到实体 DAO 的显式调用桥接。
 *
 * <p>调用方必须提供可信访问者、业务操作和待校验目标；本类不从实体 ID、旧 DAO
 * 范围或线程上下文推导授权。授权拒绝发生在创建 DAO 之前，只有原请求绑定的允许结果
 * 才能进入 {@link EntityDaoFactory#scopedAuthorized(EntityType, EntityDaoAuthorizationContext)}。</p>
 */
public final class EntityDaoAuthorizationProvider {
    private final BusinessAuthorizationService authorization;
    private final EntityDaoFactory factory;

    public EntityDaoAuthorizationProvider(
        BusinessAuthorizationService authorization,
        EntityDaoFactory factory
    ) {
        this.authorization = Objects.requireNonNull(authorization, "业务授权服务不能为空");
        this.factory = Objects.requireNonNull(factory, "实体 DAO 工厂不能为空");
    }

    /**
     * 按次授权并创建绑定本次请求的 DAO。
     *
     * @param entityType 实体和主键类型
     * @param visitor 可信访问者
     * @param operation 已注册的业务操作
     * @param targetId 可选目标 ID；目标 DAO 读取/写入形状自行继续校验
     * @return 消费授权结果的 DAO
     */
    public <T, ID> EntityDao<T, ID> open(
        EntityType<T, ID> entityType,
        VisitorContext visitor,
        BusinessOperation operation,
        String targetId
    ) {
        Objects.requireNonNull(entityType, "DAO 实体类型不能为空");
        AuthorizationRequest request = new AuthorizationRequest(
            Objects.requireNonNull(visitor, "可信访问者不能为空"),
            Objects.requireNonNull(operation, "业务操作不能为空"),
            targetId
        );
        AuthorizationResult result = authorization.authorize(request);
        if (result == null || result.getDecision() != AccessDecision.ALLOW) {
            throw new PermissionDeniedException("实体 DAO 业务授权被拒绝: " + operation);
        }
        result.requireApplicable(request);
        return factory.scopedAuthorized(
            entityType,
            EntityDaoAuthorizationContext.of(request, result)
        );
    }

    /**
     * 逐目标授权但不创建 DAO。
     *
     * <p>每个目标独立调用业务授权服务；任一目标拒绝时整体拒绝，不返回可被存储适配器消费的
     * 批次上下文。当前标准 JDBC 工厂尚未开放该上下文的批量执行。</p>
     */
    public EntityDaoBatchAuthorizationContext authorizeBatch(
        VisitorContext visitor,
        BusinessOperation operation,
        Collection<String> targetIds
    ) {
        Objects.requireNonNull(visitor, "可信访问者不能为空");
        Objects.requireNonNull(operation, "业务操作不能为空");
        Objects.requireNonNull(targetIds, "批量目标不能为空");
        EntityDaoBatchAuthorizationContext.requireSupportedSize(targetIds.size());
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        List<String> validatedTargetIds = new ArrayList<String>(targetIds.size());
        for (String targetId : targetIds) {
            if (targetId == null) {
                throw new ValidationException("批量授权目标不能为空");
            }
            if (!seen.add(targetId)) {
                throw new ValidationException("批量授权目标不能重复: " + targetId);
            }
            validatedTargetIds.add(targetId);
        }
        List<EntityDaoAuthorizationContext> contexts = new ArrayList<EntityDaoAuthorizationContext>();
        for (String targetId : validatedTargetIds) {
            AuthorizationRequest request = new AuthorizationRequest(visitor, operation, targetId);
            AuthorizationResult result = authorization.authorize(request);
            if (result == null || result.getDecision() != AccessDecision.ALLOW) {
                throw new PermissionDeniedException("实体 DAO 批量业务授权被拒绝: " + operation + ", target=" + targetId);
            }
            result.requireApplicable(request);
            contexts.add(EntityDaoAuthorizationContext.of(request, result));
        }
        return EntityDaoBatchAuthorizationContext.of(contexts);
    }

    /** 逐目标授权并创建只读批量 DAO。 */
    public <T, ID> EntityDaoBatch<T, ID> openBatch(
        EntityType<T, ID> entityType,
        VisitorContext visitor,
        BusinessOperation operation,
        Collection<String> targetIds
    ) {
        Objects.requireNonNull(entityType, "DAO 实体类型不能为空");
        EntityDaoBatchAuthorizationContext context = authorizeBatch(visitor, operation, targetIds);
        return factory.scopedAuthorizedBatch(entityType, context);
    }

    /** 逐目标授权并创建批量写入 DAO；具体存储能力不足时由 Factory 保持拒绝。 */
    public <T, ID> EntityDaoBatchWrite<T, ID> openBatchWrite(
        EntityType<T, ID> entityType,
        VisitorContext visitor,
        BusinessOperation operation,
        Collection<String> targetIds
    ) {
        Objects.requireNonNull(entityType, "DAO 实体类型不能为空");
        EntityDaoBatchAuthorizationContext context = authorizeBatch(visitor, operation, targetIds);
        return factory.scopedAuthorizedBatchWrite(entityType, context);
    }
}
