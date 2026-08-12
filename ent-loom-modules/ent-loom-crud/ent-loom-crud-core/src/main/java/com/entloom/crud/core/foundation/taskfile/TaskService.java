package com.entloom.crud.core.foundation.taskfile;

/**
 * Import / Export 任务服务 SPI。
 */
public interface TaskService {
    CrudTask create(CrudTask task);

    CrudTask getRequired(String taskId);

    CrudTask updateStatus(String taskId, CrudTaskStatus status, String message);

    CrudTask cancel(String taskId, String reason);
}
