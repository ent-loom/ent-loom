package com.entloom.crud.core.capability.dao;

/** 逐目标批量写入当前允许声明的操作形状。 */
public enum EntityDaoBatchWriteOperation {
    /** 局部更新。 */
    UPDATE,
    /** 删除或逻辑删除。 */
    DELETE
}
