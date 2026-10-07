package com.entloom.crud.api.access;

import java.util.Objects;

/** 固定当前身份的只读上下文视图；类型匹配不代表业务操作已获授权。 */
public final class AccessContext<I extends BusinessIdentity> {
    private final VisitorContext visitorContext;
    private final I identity;

    private AccessContext(VisitorContext visitorContext, I identity) {
        this.visitorContext = visitorContext;
        this.identity = identity;
    }

    public static <I extends BusinessIdentity> AccessContext<I> from(
        VisitorContext visitorContext,
        Class<I> identityType
    ) {
        Objects.requireNonNull(visitorContext, "访问者上下文不能为空");
        return new AccessContext<I>(visitorContext, visitorContext.requireIdentity(identityType));
    }

    public VisitorContext getVisitorContext() {
        return visitorContext;
    }

    public AccessSubject getSubject() {
        return visitorContext.getSubject().orElseThrow(() -> new IllegalStateException("固定身份上下文不能匿名"));
    }

    public I getIdentity() {
        return identity;
    }
}
