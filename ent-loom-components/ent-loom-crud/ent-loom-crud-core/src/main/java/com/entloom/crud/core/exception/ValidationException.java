package com.entloom.crud.core.exception;

import com.entloom.crud.api.enums.CrudErrorCode;

/**
 * 参数校验异常。
 */
public class ValidationException extends CrudException {
    public ValidationException(String message) {
        super(CrudErrorCode.VALIDATION_ERROR, message);
    }
}
