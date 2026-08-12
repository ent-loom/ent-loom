package com.entloom.crud.core.capability.exporting;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.exception.CrudException;

/**
 * Fail-closed 默认导出引擎。
 */
public class UnsupportedExportEngine implements ExportEngine {
    @Override
    public ExportResult execute(ExportSpec spec) {
        throw new CrudException(CrudErrorCode.UNSUPPORTED_OPERATION, "默认导出引擎尚未启用: " + (spec == null ? null : spec.getOperation()));
    }
}
