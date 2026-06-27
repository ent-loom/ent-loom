package com.entloom.crud.core.capability.exporting;

import com.entloom.crud.core.foundation.taskfile.FileWriteRequest;

/**
 * 导出文件生成器。
 */
public interface ExportFileWriter {
    FileWriteRequest write(ExportSpec spec, ExportTable table);
}
