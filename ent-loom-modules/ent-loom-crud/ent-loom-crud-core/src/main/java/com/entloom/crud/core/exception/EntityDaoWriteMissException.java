package com.entloom.crud.core.exception;

import com.entloom.crud.api.enums.CrudErrorCode;

/**
 * DAO 按主键写入未命中目标或绑定范围的稳定异常。
 */
public class EntityDaoWriteMissException extends CrudException {
    public EntityDaoWriteMissException(String message) {
        super(CrudErrorCode.WRITE_CONFLICT, message);
    }
}
