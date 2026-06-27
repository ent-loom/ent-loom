package com.entloom.crud.starter.web.facade;

import com.entloom.crud.core.foundation.taskfile.FileRef;

/**
 * HTTP 文件下载数据。
 */
public final class FileDownload {
    private final FileRef file;
    private final byte[] content;

    public FileDownload(FileRef file, byte[] content) {
        this.file = file;
        this.content = content == null ? new byte[0] : java.util.Arrays.copyOf(content, content.length);
    }

    public FileRef getFile() {
        return file;
    }

    public byte[] getContent() {
        return java.util.Arrays.copyOf(content, content.length);
    }
}
