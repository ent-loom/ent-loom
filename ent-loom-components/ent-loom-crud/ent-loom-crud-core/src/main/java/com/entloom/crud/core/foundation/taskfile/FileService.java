package com.entloom.crud.core.foundation.taskfile;

/**
 * Import / Export 文件服务 SPI。
 */
public interface FileService {
    FileRef save(FileWriteRequest request);

    FileRef getRequired(String fileId);

    byte[] read(FileRef fileRef);
}
