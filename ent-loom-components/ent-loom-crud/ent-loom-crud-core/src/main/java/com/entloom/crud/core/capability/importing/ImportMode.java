package com.entloom.crud.core.capability.importing;

/**
 * 导入写入模式。
 */
public enum ImportMode {
    /** 仅校验，不写入。 */
    VALIDATE_ONLY,
    /** 新增写入。 */
    INSERT,
    /** 更新写入。 */
    UPDATE,
    /** 按主键或业务键执行新增或更新。 */
    UPSERT
}
