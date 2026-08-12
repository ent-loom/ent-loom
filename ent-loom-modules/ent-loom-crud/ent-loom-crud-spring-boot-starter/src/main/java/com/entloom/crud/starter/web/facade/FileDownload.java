package com.entloom.crud.starter.web.facade;

import com.entloom.crud.core.foundation.taskfile.FileRef;
import java.io.InputStream;

/**
 * HTTP 文件下载数据。
 */
public final class FileDownload {
    private final FileRef file;
    private final StreamFactory streamFactory;

    public FileDownload(FileRef file, byte[] content) {
        this.file = file;
        final byte[] copied = content == null ? new byte[0] : java.util.Arrays.copyOf(content, content.length);
        this.streamFactory = new StreamFactory() {
            @Override
            public InputStream openStream() {
                return new java.io.ByteArrayInputStream(copied);
            }
        };
    }

    public FileDownload(FileRef file, StreamFactory streamFactory) {
        this.file = file;
        this.streamFactory = streamFactory;
    }

    public FileRef getFile() {
        return file;
    }

    public byte[] getContent() {
        try (InputStream inputStream = openStream()) {
            return org.springframework.util.StreamUtils.copyToByteArray(inputStream);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("读取下载文件失败", ex);
        }
    }

    public InputStream openStream() {
        if (streamFactory == null) {
            return new java.io.ByteArrayInputStream(new byte[0]);
        }
        return streamFactory.openStream();
    }

    public interface StreamFactory {
        InputStream openStream();
    }
}
