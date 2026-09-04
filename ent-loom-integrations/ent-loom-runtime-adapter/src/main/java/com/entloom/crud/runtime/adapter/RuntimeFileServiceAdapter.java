package com.entloom.crud.runtime.adapter;

import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.core.foundation.taskfile.FileService;
import com.entloom.crud.core.foundation.taskfile.FileStreamWriteRequest;
import com.entloom.crud.core.foundation.taskfile.FileWriteRequest;
import com.entloom.crud.core.exception.ValidationException;
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
        try {
            return fileMapper.toCrud(delegate.save(fileMapper.toRuntime(request)));
        } catch (RuntimeException ex) {
            throw RuntimeAdapterExceptionTranslator.file(ex, "写入", null);
        }
    }

    @Override
    public FileRef save(FileStreamWriteRequest request) {
        try {
            return fileMapper.toCrud(delegate.save(fileMapper.toRuntime(request)));
        } catch (RuntimeException ex) {
            throw RuntimeAdapterExceptionTranslator.file(ex, "流式写入", null);
        }
    }

    @Override
    public FileRef getRequired(String fileId) {
        String requiredId = requiredFileId(fileId);
        try {
            return fileMapper.toCrud(delegate.getRequired(requiredId));
        } catch (RuntimeException ex) {
            throw RuntimeAdapterExceptionTranslator.metadata(ex, requiredId);
        }
    }

    @Override
    public byte[] read(FileRef fileRef) {
        String fileId = requiredFileId(fileRef);
        try {
            return delegate.read(delegate.getRequired(fileId));
        } catch (RuntimeException ex) {
            throw RuntimeAdapterExceptionTranslator.file(ex, "读取", fileId);
        }
    }

    @Override
    public InputStream openStream(FileRef fileRef) {
        String fileId = requiredFileId(fileRef);
        try {
            return delegate.openStream(delegate.getRequired(fileId));
        } catch (RuntimeException ex) {
            throw RuntimeAdapterExceptionTranslator.file(ex, "打开流", fileId);
        }
    }

    private static String requiredFileId(FileRef fileRef) {
        if (fileRef == null || fileRef.getFileId() == null || fileRef.getFileId().trim().isEmpty()) {
            throw new ValidationException("文件引用 fileId 不能为空");
        }
        return fileRef.getFileId().trim();
    }

    private static String requiredFileId(String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            throw new ValidationException("文件 ID 不能为空");
        }
        return fileId.trim();
    }
}
