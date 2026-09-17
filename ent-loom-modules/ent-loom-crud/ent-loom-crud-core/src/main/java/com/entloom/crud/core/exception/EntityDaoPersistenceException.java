package com.entloom.crud.core.exception;

import com.entloom.crud.api.enums.CrudErrorCode;

/**
 * DAO 持久化不变量或执行结果异常。
 */
public class EntityDaoPersistenceException extends CrudException {
    public EntityDaoPersistenceException(String message) {
        super(CrudErrorCode.INTERNAL_ERROR, message);
    }

    public EntityDaoPersistenceException(String message, Throwable cause) {
        super(CrudErrorCode.INTERNAL_ERROR, message, cause);
    }
}
