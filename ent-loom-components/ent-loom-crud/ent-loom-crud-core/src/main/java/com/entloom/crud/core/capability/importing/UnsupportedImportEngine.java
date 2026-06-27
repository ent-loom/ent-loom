package com.entloom.crud.core.capability.importing;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.exception.CrudException;

/**
 * Fail-closed 默认导入引擎。
 */
public class UnsupportedImportEngine implements ImportEngine {
    @Override
    public ImportResult execute(ImportSpec spec) {
        throw new CrudException(CrudErrorCode.UNSUPPORTED_OPERATION, "默认导入引擎尚未启用: " + (spec == null ? null : spec.getOperation()));
    }
}
