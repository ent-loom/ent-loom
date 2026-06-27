package com.entloom.crud.core.exception;

import com.entloom.crud.api.enums.CrudErrorCode;

/**
 * 详情未命中异常。
 */
public class NotFoundException extends CrudException {
    public NotFoundException(String message) {
        super(CrudErrorCode.ROUTE_NOT_FOUND, message);
    }
}
