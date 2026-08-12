package com.entloom.crud.starter.web.support;

import com.entloom.crud.api.enums.CrudOperationKey;

/**
 * 当前线程的 CRUD 响应上下文。
 */
final class CrudResponseContextHolder {
    private static final ThreadLocal<CrudResponseContext> HOLDER = new ThreadLocal<CrudResponseContext>();

    private CrudResponseContextHolder() {
    }

    static void bind(String requestId, CrudOperationKey operationKey, String capability) {
        HOLDER.set(new CrudResponseContext(requestId, operationKey, capability));
    }

    static CrudResponseContext clear() {
        CrudResponseContext context = HOLDER.get();
        HOLDER.remove();
        return context;
    }
}
