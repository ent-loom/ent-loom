package com.entloom.crud.runtime.adapter;

import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.core.foundation.taskfile.FileService;
import com.entloom.crud.core.foundation.taskfile.FileStreamWriteRequest;
import com.entloom.crud.core.foundation.taskfile.FileWriteRequest;
import java.io.InputStream;

/**
 * 使用 runtime {@code FileStore} 实现 CRUD {@code FileService}。
 */
public final class RuntimeFileServiceAdapter implements FileService {
    private final com.entloom.runtime.contract.file.FileStore delegate;
    private final CrudRuntimeFileMapper fileMapper;

    public RuntimeFileServiceAdapter(com.entloom.runtime.contract.file.FileStore delegate) {
        this(delegate, new CrudRuntimeFileMapper());
    }

    public RuntimeFileServiceAdapter(com.entloom.runtime.contract.file.FileStore delegate,
                                     CrudRuntimeFileMapper fileMapper) {
        if (delegate == null) {
            throw new IllegalArgumentException("runtime FileStore 不能为空");
        }
        if (fileMapper == null) {
            throw new IllegalArgumentException("fileMapper 不能为空");
        }
        this.delegate = delegate;
        this.fileMapper = fileMapper;
    }

    @Override
    public FileRef save(FileWriteRequest request) {
        return fileMapper.toCrud(delegate.save(fileMapper.toRuntime(request)));
    }

    @Override
    public FileRef save(FileStreamWriteRequest request) {
        return fileMapper.toCrud(delegate.save(fileMapper.toRuntime(request)));
    }

    @Override
    public FileRef getRequired(String fileId) {
        return fileMapper.toCrud(delegate.getRequired(fileId));
    }

    @Override
    public byte[] read(FileRef fileRef) {
        return delegate.read(delegate.getRequired(requiredFileId(fileRef)));
    }

    @Override
    public InputStream openStream(FileRef fileRef) {
        return delegate.openStream(delegate.getRequired(requiredFileId(fileRef)));
    }

    private static String requiredFileId(FileRef fileRef) {
        if (fileRef == null || fileRef.getFileId() == null || fileRef.getFileId().trim().isEmpty()) {
            throw new IllegalArgumentException("文件引用 fileId 不能为空");
        }
        return fileRef.getFileId().trim();
    }
}
