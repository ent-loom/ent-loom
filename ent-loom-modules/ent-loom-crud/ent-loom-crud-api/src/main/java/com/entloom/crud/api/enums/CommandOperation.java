package com.entloom.crud.api.enums;

import java.util.Locale;

/**
 * 命令操作类型。
 */
public enum CommandOperation implements CrudScopedOperation {
    /** 新增。 */
    CREATE,
    /** 更新。 */
    UPDATE,
    /** 删除。 */
    DELETE,
    /** 按主键保存或更新。 */
    SAVE_OR_UPDATE,
    /** 批量新增。 */
    CREATE_BATCH,
    /** 批量更新。 */
    UPDATE_BATCH,
    /** 批量删除。 */
    DELETE_BATCH,
    /** 批量保存或更新。 */
    SAVE_OR_UPDATE_BATCH,
    /** 业务动作。 */
    ACTION;

    @Override
    public CrudOperationDomain domain() {
        return CrudOperationDomain.COMMAND;
    }

    public static CommandOperation from(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
