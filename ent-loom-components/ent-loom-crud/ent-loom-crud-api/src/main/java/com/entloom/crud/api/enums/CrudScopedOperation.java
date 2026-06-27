package com.entloom.crud.api.enums;

/**
 * 绑定操作域的 CRUD operation。
 */
public interface CrudScopedOperation {
    CrudOperationDomain domain();

    default String code() {
//        if (!(this instanceof Enum<?>)) {
//            throw new IllegalStateException("CrudScopedOperation code must be overridden by non-enum implementations");
//        }
        return ((Enum<?>) this).name();
    }
}
