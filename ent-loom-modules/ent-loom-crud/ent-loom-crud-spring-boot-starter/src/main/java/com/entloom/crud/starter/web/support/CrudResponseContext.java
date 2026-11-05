package com.entloom.crud.starter.web.support;

import com.entloom.crud.api.enums.CrudOperationKey;

/**
 * 当前请求的响应上下文。
 */
final class CrudResponseContext {
    private final CrudOperationKey operationKey;
    private final String capability;
    private final CrudResponseContext previous;
    private volatile boolean failed;

    CrudResponseContext(CrudOperationKey operationKey, String capability, CrudResponseContext previous) {
        this.operationKey = operationKey;
        this.capability = capability;
        this.previous = previous;
    }

    CrudOperationKey getOperationKey() {
        return operationKey;
    }

    String getCapability() {
        return capability;
    }

    CrudResponseContext getPrevious() {
        return previous;
    }

    boolean isFailed() {
        return failed;
    }

    void markFailed() {
        failed = true;
    }
}
