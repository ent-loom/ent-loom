package com.entloom.crud.core.capability.exporting;

import com.entloom.crud.core.foundation.taskfile.CrudTask;
import com.entloom.crud.core.foundation.taskfile.FileRef;

/**
 * 导出网关。
 */
public interface ExportGateway {
    ExportResult submit(ExportSpec spec);

    ExportResult preview(ExportSpec spec);

    FileRef download(ExportSpec spec);

    CrudTask status(ExportSpec spec);

    CrudTask cancel(ExportSpec spec);
}
