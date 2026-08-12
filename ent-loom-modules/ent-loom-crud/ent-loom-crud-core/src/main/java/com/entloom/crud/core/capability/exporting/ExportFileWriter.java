package com.entloom.crud.core.capability.exporting;

import com.entloom.crud.core.foundation.taskfile.FileStreamWriteRequest;

/**
 * 导出文件生成器。
 */
public interface ExportFileWriter {
    FileStreamWriteRequest write(ExportSpec spec, ExportTable table);
}
