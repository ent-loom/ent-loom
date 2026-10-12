package com.entloom.crud.api.access;

import java.util.Objects;

/** 按业务身份码固定当前身份的只读视图；身份匹配不代表业务操作已获授权。 */
public final class AccessContext {
    private final VisitorContext visitorContext;
    private final BusinessIdentity identity;

    private AccessContext(VisitorContext visitorContext, BusinessIdentity identity) {
        this.visitorContext = visitorContext;
        this.identity = identity;
    }

    public static AccessContext from(
        VisitorContext visitorContext,
        String identityCode
    ) {
        Objects.requireNonNull(visitorContext, "访问者上下文不能为空");
        return new AccessContext(visitorContext, visitorContext.requireIdentity(identityCode));
    }

    public VisitorContext getVisitorContext() {
        return visitorContext;
    }

    public AccessSubject getSubject() {
        return visitorContext.getSubject().orElseThrow(() -> new IllegalStateException("固定身份上下文不能匿名"));
    }

    public BusinessIdentity getIdentity() {
        return identity;
    }
}
