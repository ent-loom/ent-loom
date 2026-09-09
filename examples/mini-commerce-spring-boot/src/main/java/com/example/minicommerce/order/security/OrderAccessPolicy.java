package com.example.minicommerce.order.security;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.governance.permission.CrudPermissionService;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import org.springframework.stereotype.Component;

/** 复用主体与动作权限规则；本地示例不提供租户或订单归属隔离。 */
@Component
public class OrderAccessPolicy {
    private final CrudSubjectResolver subjectResolver;
    private final CrudPermissionService permissionService;

    public OrderAccessPolicy(CrudSubjectResolver subjectResolver, CrudPermissionService permissionService) {
        this.subjectResolver = subjectResolver;
        this.permissionService = permissionService;
    }

    /** 检查 {@link OrderAction}，仅明确允许时继续执行。 */
    public void require(OrderAction action) {
        SubjectContext subject;
        try {
            subject = subjectResolver.resolveOrThrow();
        } catch (ValidationException exception) {
            throw new PermissionDeniedException("无法解析订单操作主体");
        }
        if (subject == null || subject.getSubjectId() == null || subject.getSubjectId().isBlank()) {
            throw new PermissionDeniedException("订单操作需要有效主体");
        }
        // 业务动作没有 CRUD 查询规格；示例默认规则服务只读取资源、动作、场景和主体。
        AccessDecision decision = permissionService.decide(
            new CrudResourceAction("order", action.name(), "default"), subject, null);
        if (decision != AccessDecision.ALLOW) {
            throw new PermissionDeniedException("订单操作未获授权");
        }
    }
}
