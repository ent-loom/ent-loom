package com.entloom.crud.core.foundation.taskfile;

import java.io.InputStream;

/**
 * Import / Export 文件服务 SPI。
 */
public interface FileService {
    FileRef save(FileWriteRequest request);

    FileRef save(FileStreamWriteRequest request);

    FileRef getRequired(String fileId);

    byte[] read(FileRef fileRef);

    InputStream openStream(FileRef fileRef);
}
