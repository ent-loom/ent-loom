package com.entloom.crud.core.foundation.taskfile;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.ValidationException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Import / Export 任务默认内存实现。
 */
public class InMemoryTaskService implements TaskService {
    private final ConcurrentMap<String, CrudTask> tasks = new ConcurrentHashMap<String, CrudTask>();

    @Override
    public CrudTask create(CrudTask task) {
        if (task == null) {
            throw new ValidationException("任务不能为空");
        }
        Instant now = Instant.now();
        String taskId = isBlank(task.getTaskId()) ? newTaskId() : task.getTaskId().trim();
        CrudTask created = copy(task)
            .taskId(taskId)
            .createdAt(task.getCreatedAt() == null ? now : task.getCreatedAt())
            .updatedAt(now)
            .finishedAt(isTerminal(task.getStatus()) && task.getFinishedAt() == null ? now : task.getFinishedAt())
            .build();
        CrudTask existing = tasks.putIfAbsent(taskId, created);
        if (existing != null) {
            throw new ValidationException("任务已存在: " + taskId);
        }
        return created;
    }

    @Override
    public CrudTask getRequired(String taskId) {
        CrudTask task = tasks.get(requiredTaskId(taskId));
        if (task == null) {
            throw new CrudException(CrudErrorCode.TASK_NOT_FOUND, "任务不存在: " + taskId);
        }
        return task;
    }

    @Override
    public CrudTask updateStatus(String taskId, CrudTaskStatus status, String message) {
        String key = requiredTaskId(taskId);
        return tasks.compute(key, (id, current) -> {
            if (current == null) {
                throw new CrudException(CrudErrorCode.TASK_NOT_FOUND, "任务不存在: " + key);
            }
            Instant now = Instant.now();
            return copy(current)
                .status(status == null ? current.getStatus() : status)
                .message(message)
                .updatedAt(now)
                .finishedAt(isTerminal(status) ? now : current.getFinishedAt())
                .build();
        });
    }

    @Override
    public CrudTask cancel(String taskId, String reason) {
        return updateStatus(taskId, CrudTaskStatus.CANCELED, reason == null ? "已取消" : reason);
    }

    private static CrudTask.Builder copy(CrudTask source) {
        return CrudTask.builder()
            .taskId(source.getTaskId())
            .status(source.getStatus())
            .contextSnapshot(source.getContextSnapshot())
            .sourceFile(source.getSourceFile())
            .resultFile(source.getResultFile())
            .errorFile(source.getErrorFile())
            .progress(source.getProgress())
            .message(source.getMessage())
            .createdAt(source.getCreatedAt())
            .updatedAt(source.getUpdatedAt())
            .finishedAt(source.getFinishedAt());
    }

    private static boolean isTerminal(CrudTaskStatus status) {
        return status == CrudTaskStatus.SUCCEEDED
            || status == CrudTaskStatus.FAILED
            || status == CrudTaskStatus.CANCELED;
    }

    private static String requiredTaskId(String taskId) {
        if (isBlank(taskId)) {
            throw new ValidationException("任务 ID 不能为空");
        }
        return taskId.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String newTaskId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
