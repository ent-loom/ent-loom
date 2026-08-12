package com.entloom.crud.starter.web.support;

import com.entloom.crud.api.enums.CrudOperationKey;

/**
 * 当前请求的响应上下文。
 */
final class CrudResponseContext {
    private final String requestId;
    private final CrudOperationKey operationKey;
    private final String capability;

    CrudResponseContext(String requestId, CrudOperationKey operationKey, String capability) {
        this.requestId = requestId;
        this.operationKey = operationKey;
        this.capability = capability;
    }

    String getRequestId() {
        return requestId;
    }

    CrudOperationKey getOperationKey() {
        return operationKey;
    }

    String getCapability() {
        return capability;
    }
}
