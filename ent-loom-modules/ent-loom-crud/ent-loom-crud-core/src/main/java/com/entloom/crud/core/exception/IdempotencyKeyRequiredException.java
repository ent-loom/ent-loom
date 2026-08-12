package com.entloom.crud.core.exception;

import com.entloom.crud.api.enums.CrudErrorCode;

/**
 * 幂等键缺失异常。
 */
public class IdempotencyKeyRequiredException extends CrudException {
    public IdempotencyKeyRequiredException(String message) {
        super(CrudErrorCode.IDEMPOTENCY_KEY_REQUIRED, message);
    }
}
