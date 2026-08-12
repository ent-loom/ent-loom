package com.entloom.crud.core.exception;

import com.entloom.crud.api.enums.CrudErrorCode;

/**
 * 查询策略不支持异常。
 */
public class UnsupportedQueryStrategyException extends CrudException {
    public UnsupportedQueryStrategyException(String message) {
        super(CrudErrorCode.UNSUPPORTED_QUERY_STRATEGY, message);
    }
}
