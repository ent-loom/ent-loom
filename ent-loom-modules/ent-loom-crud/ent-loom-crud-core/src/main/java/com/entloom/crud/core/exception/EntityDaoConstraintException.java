package com.entloom.crud.core.exception;

import com.entloom.crud.api.enums.CrudErrorCode;

/**
 * DAO 写入违反数据库唯一键等持久化约束的稳定异常。
 */
public class EntityDaoConstraintException extends CrudException {
    public EntityDaoConstraintException(String message, Throwable cause) {
        super(CrudErrorCode.ROW_VALIDATION_FAILED, message, cause);
    }
}
