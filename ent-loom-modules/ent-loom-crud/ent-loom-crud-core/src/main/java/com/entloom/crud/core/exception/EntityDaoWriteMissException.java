package com.entloom.crud.core.exception;

/**
 * DAO 按主键写入未命中目标或绑定范围的稳定异常。
 */
public class EntityDaoWriteMissException extends RouteNotFoundException {
    public EntityDaoWriteMissException(String message) {
        super(message);
    }
}
