package com.entloom.crud.core.exception;

import com.entloom.crud.api.enums.CrudErrorCode;

/**
 * 幂等处理中异常。
 */
public class IdempotencyInProgressException extends CrudException {
    public IdempotencyInProgressException(String message) {
        super(CrudErrorCode.IDEMPOTENCY_IN_PROGRESS, message);
    }
}
