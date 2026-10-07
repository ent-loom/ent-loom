package com.entloom.crud.api.access;

import java.util.Objects;
import java.util.UUID;

/** 一次业务操作授权请求，不承载 CRUD Spec 或业务 payload。 */
public final class AuthorizationRequest {
    private final String invocationId;
    private final VisitorContext visitor;
    private final BusinessOperation operation;
    private final String targetId;

    public AuthorizationRequest(VisitorContext visitor, BusinessOperation operation, String targetId) {
        this.visitor = Objects.requireNonNull(visitor, "访问者上下文不能为空");
        this.operation = Objects.requireNonNull(operation, "业务操作不能为空");
        if (targetId != null && (targetId.trim().isEmpty() || !targetId.equals(targetId.trim()))) {
            throw new IllegalArgumentException("待校验目标 ID 不能包含首尾空格或为空");
        }
        this.targetId = targetId;
        this.invocationId = UUID.randomUUID().toString();
    }

    public String getInvocationId() { return invocationId; }
    public VisitorContext getVisitor() { return visitor; }
    public BusinessOperation getOperation() { return operation; }
    /** 调用方提供的待校验目标，不代表已经拥有目标权限。 */
    public String getTargetId() { return targetId; }
}
