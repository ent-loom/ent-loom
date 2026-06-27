package com.entloom.crud.core.capability.importing;

import com.entloom.crud.core.foundation.taskfile.FileWriteRequest;

/**
 * 导入错误文件生成器。
 */
public interface ImportErrorFileWriter {
    FileWriteRequest writeErrorFile(ImportSpec spec, ImportResult result);
}
