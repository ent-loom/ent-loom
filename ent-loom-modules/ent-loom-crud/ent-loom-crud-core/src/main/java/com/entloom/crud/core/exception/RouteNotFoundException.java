package com.entloom.crud.core.exception;

import com.entloom.crud.api.enums.CrudErrorCode;

/**
 * 路由未命中异常。
 */
public class RouteNotFoundException extends CrudException {
    public RouteNotFoundException(String message) {
        super(CrudErrorCode.ROUTE_NOT_FOUND, message);
    }
}
