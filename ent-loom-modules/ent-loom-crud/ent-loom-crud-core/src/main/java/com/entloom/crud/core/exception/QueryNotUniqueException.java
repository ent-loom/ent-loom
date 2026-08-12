package com.entloom.crud.core.exception;

import com.entloom.crud.api.enums.CrudErrorCode;

/**
 * 单条语义查询命中多条异常。
 */
public class QueryNotUniqueException extends CrudException {
    public QueryNotUniqueException(String message) {
        super(CrudErrorCode.QUERY_NOT_UNIQUE, message);
    }
}
