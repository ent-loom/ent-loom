package com.entloom.crud.core.capability.importing;

import com.entloom.crud.core.foundation.taskfile.CrudTask;
import com.entloom.crud.core.foundation.taskfile.FileRef;

/**
 * 导入网关。
 */
public interface ImportGateway {
    ImportResult validate(ImportSpec spec);

    ImportResult submit(ImportSpec spec);

    ImportResult commit(ImportSpec spec);

    CrudTask status(ImportSpec spec);

    CrudTask cancel(ImportSpec spec);

    FileRef downloadError(ImportSpec spec);
}
