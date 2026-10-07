package com.entloom.crud.core.capability.dao;

import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationResult;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import java.util.Objects;

/**
 * DAO 按次授权执行上下文。
 *
 * <p>该上下文只接受授权内核签发且绑定同一 {@link AuthorizationRequest} 的允许结果。
 * DAO 执行器必须在消费前校验请求对象、访问者、业务操作、规范资源和目标一致；不能用旧
 * {@link EntityAccessScope} 替代这些校验。</p>
 */
public final class EntityDaoAuthorizationContext {
    private final AuthorizationRequest request;
    private final AuthorizationResult result;

    private EntityDaoAuthorizationContext(AuthorizationRequest request, AuthorizationResult result) {
        this.request = Objects.requireNonNull(request, "DAO 授权请求不能为空");
        this.result = Objects.requireNonNull(result, "DAO 授权结果不能为空");
        result.requireApplicable(request);
    }

    /** 创建绑定本次授权请求的 DAO 上下文。 */
    public static EntityDaoAuthorizationContext of(
        AuthorizationRequest request,
        AuthorizationResult result
    ) {
        return new EntityDaoAuthorizationContext(request, result);
    }

    /** 验证 DAO 当前调用仍使用同一请求对象及其主体、操作和目标。 */
    public void requireApplicable(AuthorizationRequest current) {
        if (current == null || current != request
            || current.getVisitor() != request.getVisitor()
            || !current.getOperation().equals(request.getOperation())
            || !Objects.equals(current.getTargetId(), request.getTargetId())) {
            throw new PermissionDeniedException("DAO 按次授权不适用于当前调用");
        }
        result.requireApplicable(current);
    }

    /** 验证当前实体解析出的规范资源。 */
    public void requireResource(String resourceCode) {
        if (resourceCode == null
            || !resourceCode.equals(request.getOperation().getResourceCode())) {
            throw new PermissionDeniedException("DAO 按次授权资源与实体不一致");
        }
    }

    public AuthorizationRequest getRequest() {
        return request;
    }

    public AuthorizationResult getResult() {
        return result;
    }

    public VisitorContext getVisitor() {
        return request.getVisitor();
    }

    public BusinessOperation getOperation() {
        return request.getOperation();
    }

    public String getTargetId() {
        return request.getTargetId();
    }

    /**
     * 要求本次授权绑定一个明确目标。
     *
     * <p>当前标准授权 DAO 只实现单目标读写；无目标授权不能退化为批量或全量访问。
     * 批量能力需要逐目标授权、范围交集、事务和审计合同另行建立。</p>
     *
     * @return 已绑定的目标 ID
     */
    public String requireSingleTarget() {
        String targetId = request.getTargetId();
        if (targetId == null) {
            throw new PermissionDeniedException(
                "按次授权 DAO 当前只支持单一目标；批量或无目标授权尚未建立逐目标合同"
            );
        }
        return targetId;
    }

    public AuthorizationScope getScope() {
        return result.getScope();
    }

    public AuthorizationFields getFields() {
        return result.getFields();
    }
}
