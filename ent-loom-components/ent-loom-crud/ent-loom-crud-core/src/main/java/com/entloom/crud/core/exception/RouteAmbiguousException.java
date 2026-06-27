package com.entloom.crud.core.exception;

import com.entloom.crud.api.enums.CrudErrorCode;

/**
 * 路由歧义异常。
 */
public class RouteAmbiguousException extends CrudException {
    public RouteAmbiguousException(String message) {
        super(CrudErrorCode.ROUTE_AMBIGUOUS, message);
    }
}
