package com.entloom.crud.starter.web.support;

import com.entloom.crud.api.enums.CrudNullFieldMode;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 在当前 HTTP 请求内传递查询响应的空字段模式。
 */
public final class CrudResponseNullFieldModeContext {
    private static final String ATTRIBUTE_NAME = CrudResponseNullFieldModeContext.class.getName() + ".mode";

    private CrudResponseNullFieldModeContext() {
    }

    public static void bind(CrudNullFieldMode mode) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null && mode != null) {
            attributes.setAttribute(ATTRIBUTE_NAME, mode, RequestAttributes.SCOPE_REQUEST);
        }
    }

    public static CrudNullFieldMode current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        Object value = attributes.getAttribute(ATTRIBUTE_NAME, RequestAttributes.SCOPE_REQUEST);
        return value instanceof CrudNullFieldMode ? (CrudNullFieldMode) value : null;
    }
}
