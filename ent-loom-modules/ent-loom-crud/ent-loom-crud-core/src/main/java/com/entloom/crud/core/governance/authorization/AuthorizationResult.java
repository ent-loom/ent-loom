package com.entloom.crud.core.governance.authorization;

import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.core.exception.PermissionDeniedException;
import java.util.Objects;

/** 绑定单次请求的不可变授权结果；允许结果只能由本包授权内核签发。 */
public final class AuthorizationResult {
    private final AuthorizationRequest request;
    private final AccessDecision decision;
    private final AuthorizationScope scope;
    private final AuthorizationFields fields;

    private AuthorizationResult(AuthorizationRequest request, AccessDecision decision,
                                AuthorizationScope scope, AuthorizationFields fields) {
        this.request = Objects.requireNonNull(request, "授权请求不能为空");
        this.decision = decision;
        this.scope = scope;
        this.fields = fields;
    }

    static AuthorizationResult granted(AuthorizationRequest request, AuthorizationScope scope, AuthorizationFields fields) {
        return new AuthorizationResult(request, AccessDecision.ALLOW,
            Objects.requireNonNull(scope, "允许结果必须有明确范围"),
            Objects.requireNonNull(fields, "允许结果必须有明确字段约束"));
    }

    static AuthorizationResult denied(AuthorizationRequest request) {
        return new AuthorizationResult(request, AccessDecision.DENY,
            AuthorizationScope.none(), AuthorizationFields.none());
    }

    public AuthorizationRequest getRequest() { return request; }
    public AccessDecision getDecision() { return decision; }
    public AuthorizationScope getScope() { return scope; }
    public AuthorizationFields getFields() { return fields; }

    /** 执行适配必须用原请求校验本次授权，不能把结果复用到另一目标或调用。 */
    public void requireApplicable(AuthorizationRequest current) {
        if (request != current || decision != AccessDecision.ALLOW) {
            throw new PermissionDeniedException("授权结果不适用于当前调用");
        }
    }
}
