package com.entloom.crud.core.capability.importing;

import com.entloom.crud.core.foundation.taskfile.FileStreamWriteRequest;

/**
 * 导入错误文件生成器。
 */
public interface ImportErrorFileWriter {
    FileStreamWriteRequest writeErrorFile(ImportSpec spec, ImportResult result);
}
