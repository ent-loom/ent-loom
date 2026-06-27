package com.entloom.crud.api.enums;

import java.util.Locale;

/**
 * 导出操作类型。
 */
public enum ExportOperation implements CrudScopedOperation {
    /** 提交导出任务。 */
    SUBMIT,
    /** 下载导出文件。 */
    DOWNLOAD,
    /** 查询导出任务状态。 */
    STATUS,
    /** 取消导出任务。 */
    CANCEL,
    /** 预览导出数据。 */
    PREVIEW;

    @Override
    public CrudOperationDomain domain() {
        return CrudOperationDomain.EXPORT;
    }

    public static ExportOperation from(String raw) {
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
