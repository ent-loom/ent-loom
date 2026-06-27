package com.entloom.crud.core.runtime.spec;

import com.entloom.crud.api.enums.CrudOperationKey;

/**
 * 暴露结构化 CRUD operation key 的 spec。
 */
public interface OperationKeySpec {
    CrudOperationKey getOperationKey();
}
