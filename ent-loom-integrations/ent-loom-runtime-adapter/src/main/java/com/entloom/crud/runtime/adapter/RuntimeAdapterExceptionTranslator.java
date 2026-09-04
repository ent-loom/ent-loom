package com.entloom.crud.runtime.adapter;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.runtime.contract.file.FileExpiredException;
import com.entloom.runtime.contract.file.FileNotFoundException;
import com.entloom.runtime.contract.file.FileStorageException;
import com.entloom.runtime.contract.task.TaskAlreadyExistsException;
import com.entloom.runtime.contract.task.TaskNotFoundException;
import com.entloom.runtime.core.task.InvalidTaskStateException;
import com.entloom.runtime.core.task.RuntimeAccessDeniedException;

/** 将 runtime 边界异常收敛为 CRUD 文件/任务异常语义。 */
final class RuntimeAdapterExceptionTranslator {
    private RuntimeAdapterExceptionTranslator() {
    }

    static RuntimeException file(Throwable error, String operation, String fileId) {
        if (error instanceof CrudException) {
            return (CrudException) error;
        }
        if (error instanceof FileNotFoundException) {
            return new CrudException(CrudErrorCode.FILE_NOT_FOUND,
                "文件不存在: " + fileId, error);
        }
        if (error instanceof FileExpiredException) {
            return new CrudException(CrudErrorCode.FILE_EXPIRED,
                "文件已过期: " + fileId, error);
        }
        if (error instanceof RuntimeAccessDeniedException) {
            return new CrudException(CrudErrorCode.PERMISSION_DENIED,
                "无权访问文件: " + fileId, error);
        }
        if (error instanceof FileStorageException) {
            return new CrudException(CrudErrorCode.FILE_SERVICE_UNAVAILABLE,
                "文件" + operation + "失败: " + fileId, error);
        }
        if (error instanceof IllegalArgumentException) {
            return validation(error.getMessage(), error);
        }
        return new CrudException(CrudErrorCode.FILE_SERVICE_UNAVAILABLE,
            "文件" + operation + "失败: " + fileId, error);
    }

    static RuntimeException metadata(Throwable error, String fileId) {
        if (error instanceof CrudException) {
            return (CrudException) error;
        }
        if (error instanceof FileNotFoundException) {
            return new CrudException(CrudErrorCode.FILE_NOT_FOUND,
                "文件不存在: " + fileId, error);
        }
        if (error instanceof FileExpiredException) {
            return new CrudException(CrudErrorCode.FILE_EXPIRED,
                "文件已过期: " + fileId, error);
        }
        if (error instanceof FileStorageException) {
            return new CrudException(CrudErrorCode.FILE_SERVICE_UNAVAILABLE,
                "获取文件元数据失败: " + fileId, error);
        }
        return new CrudException(CrudErrorCode.FILE_METADATA_INVALID,
            "文件元数据非法: " + fileId, error);
    }

    static RuntimeException task(Throwable error, String operation, String taskId) {
        if (error instanceof CrudException) {
            return (CrudException) error;
        }
        if (error instanceof FileNotFoundException
            || error instanceof FileExpiredException
            || error instanceof FileStorageException) {
            return file(error, operation, taskId);
        }
        if (error instanceof RuntimeAccessDeniedException) {
            return new CrudException(CrudErrorCode.PERMISSION_DENIED,
                "无权操作任务: " + taskId, error);
        }
        if (error instanceof TaskNotFoundException) {
            return new CrudException(CrudErrorCode.TASK_NOT_FOUND,
                "任务不存在: " + taskId, error);
        }
        if (error instanceof TaskAlreadyExistsException) {
            return validation("任务已存在: " + taskId, error);
        }
        if (error instanceof InvalidTaskStateException || error instanceof IllegalArgumentException) {
            String message = error.getMessage() == null ? "任务" + operation + "失败" : error.getMessage();
            return validation(message, error);
        }
        return new CrudException(CrudErrorCode.INTERNAL_ERROR,
            "任务" + operation + "失败: " + taskId, error);
    }

    private static ValidationException validation(String message, Throwable cause) {
        ValidationException exception = new ValidationException(message);
        exception.initCause(cause);
        return exception;
    }
}
