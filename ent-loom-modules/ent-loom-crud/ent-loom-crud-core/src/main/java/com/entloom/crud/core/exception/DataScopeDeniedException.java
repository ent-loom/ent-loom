package com.entloom.crud.core.exception;

import com.entloom.crud.api.enums.CrudErrorCode;

/**
 * 数据范围拒绝异常。
 */
public class DataScopeDeniedException extends CrudException {
    public DataScopeDeniedException(String message) {
        super(CrudErrorCode.DATA_SCOPE_DENIED, message);
    }
}
