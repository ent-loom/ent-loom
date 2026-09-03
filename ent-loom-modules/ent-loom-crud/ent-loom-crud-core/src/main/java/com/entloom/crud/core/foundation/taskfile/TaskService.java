package com.entloom.crud.core.foundation.taskfile;

/**
 * Import / Export 任务服务 SPI。
 *
 * <p>任务创建时必须处于 {@link CrudTaskStatus#PENDING}。状态只允许按
 * {@code PENDING -> RUNNING/FAILED/CANCELED} 和
 * {@code RUNNING -> SUCCEEDED/FAILED/CANCELED} 流转；终态不能再次覆盖。</p>
 */
public interface TaskService {
    CrudTask create(CrudTask task);

    CrudTask getRequired(String taskId);

    CrudTask updateStatus(String taskId, CrudTaskStatus status, String message);

    CrudTask cancel(String taskId, String reason);
}
