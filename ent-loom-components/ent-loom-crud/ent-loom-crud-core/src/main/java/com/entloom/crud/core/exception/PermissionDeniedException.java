package com.entloom.crud.core.exception;

import com.entloom.crud.api.enums.CrudErrorCode;

/**
 * 访问权限拒绝异常。
 */
public class PermissionDeniedException extends CrudException {
    public PermissionDeniedException(String message) {
        super(CrudErrorCode.PERMISSION_DENIED, message);
    }
}
