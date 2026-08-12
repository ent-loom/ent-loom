package com.entloom.crud.api.enums;

import java.util.Locale;

/**
 * 导入操作类型。
 */
public enum ImportOperation implements CrudScopedOperation {
    /** 校验导入文件。 */
    VALIDATE,
    /** 提交导入任务。 */
    SUBMIT,
    /** 确认写入。 */
    COMMIT,
    /** 取消导入任务。 */
    CANCEL,
    /** 查询导入任务状态。 */
    STATUS,
    /** 下载导入错误文件。 */
    DOWNLOAD_ERROR;

    @Override
    public CrudOperationDomain domain() {
        return CrudOperationDomain.IMPORT;
    }

    public static ImportOperation from(String raw) {
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
