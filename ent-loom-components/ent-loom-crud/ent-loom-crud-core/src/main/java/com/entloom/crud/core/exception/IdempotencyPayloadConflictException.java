package com.entloom.crud.core.exception;

import com.entloom.crud.api.enums.CrudErrorCode;

/**
 * 幂等载荷冲突异常。
 */
public class IdempotencyPayloadConflictException extends CrudException {
    public IdempotencyPayloadConflictException(String message) {
        super(CrudErrorCode.IDEMPOTENCY_PAYLOAD_CONFLICT, message);
    }
}
