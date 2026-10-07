package com.entloom.crud.core.governance.authorization;

import java.util.Objects;

/** 项目动态判定；允许时必须明确给出范围和字段能力。 */
public final class BusinessPermissionDecision {
    public enum Kind {
        /** 明确允许。 */
        ALLOW,
        /** 明确拒绝。 */
        DENY
    }

    private static final BusinessPermissionDecision DENIED = new BusinessPermissionDecision(
        Kind.DENY, AuthorizationScope.none(), AuthorizationFields.none());

    private final Kind kind;
    private final AuthorizationScope scope;
    private final AuthorizationFields fields;

    private BusinessPermissionDecision(Kind kind, AuthorizationScope scope, AuthorizationFields fields) {
        this.kind = kind;
        this.scope = scope;
        this.fields = fields;
    }

    public static BusinessPermissionDecision allow(AuthorizationScope scope, AuthorizationFields fields) {
        return new BusinessPermissionDecision(Kind.ALLOW,
            Objects.requireNonNull(scope, "允许判定必须有明确范围"),
            Objects.requireNonNull(fields, "允许判定必须有明确字段能力"));
    }

    public static BusinessPermissionDecision deny() { return DENIED; }

    public Kind getKind() { return kind; }
    public AuthorizationScope getScope() { return scope; }
    public AuthorizationFields getFields() { return fields; }
}
