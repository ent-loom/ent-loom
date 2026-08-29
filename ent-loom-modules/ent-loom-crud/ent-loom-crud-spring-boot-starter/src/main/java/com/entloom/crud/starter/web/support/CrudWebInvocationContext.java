package com.entloom.crud.starter.web.support;

import com.entloom.crud.core.adapter.PortalResolver;
import com.entloom.crud.core.runtime.context.CrudInvocationContext;

/** Web 入口可信调用上下文工厂。 */
public final class CrudWebInvocationContext {
    /** HTTP Controller 入口形态。 */
    public static final String HTTP_PORTAL = "http";

    private CrudWebInvocationContext() {
    }

    public static CrudInvocationContext http() {
        return CrudInvocationContext.ofAttribute(PortalResolver.ATTRIBUTE_KEY, HTTP_PORTAL);
    }
}
