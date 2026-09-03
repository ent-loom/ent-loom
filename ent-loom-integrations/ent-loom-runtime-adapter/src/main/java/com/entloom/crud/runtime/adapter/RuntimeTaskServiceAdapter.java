package com.entloom.crud.runtime.adapter;

import com.entloom.crud.core.foundation.taskfile.CrudTask;
import com.entloom.crud.core.foundation.taskfile.CrudTaskStatus;
import com.entloom.crud.core.foundation.taskfile.TaskService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 使用 runtime {@code TaskStore} 实现 CRUD {@code TaskService}。
 *
 * <p>runtime 只保存 source/result 文件；CRUD 的 errorFile ID 放在 runtime 任务扩展属性中。</p>
 */
public final class RuntimeTaskServiceAdapter implements TaskService {
    private final com.entloom.runtime.contract.task.TaskStore delegate;
    private final com.entloom.runtime.contract.file.FileStore fileStore;
    private final CrudRuntimeTaskMapper taskMapper;
    private final CrudRuntimeFileMapper fileMapper;
    private final Clock clock;

    public RuntimeTaskServiceAdapter(
        com.entloom.runtime.contract.task.TaskStore delegate,
        com.entloom.runtime.contract.file.FileStore fileStore
    ) {
        this(delegate, fileStore, new RuntimeSubjectContextMapper(), Clock.systemUTC());
    }

    public RuntimeTaskServiceAdapter(
        com.entloom.runtime.contract.task.TaskStore delegate,
        com.entloom.runtime.contract.file.FileStore fileStore,
        RuntimeSubjectContextMapper subjectMapper,
        Clock clock
    ) {
        if (subjectMapper == null) {
            throw new IllegalArgumentException("subjectMapper 不能为空");
        }
        if (delegate == null) {
            throw new IllegalArgumentException("runtime TaskStore 不能为空");
        }
        if (fileStore == null) {
            throw new IllegalArgumentException("runtime FileStore 不能为空");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock 不能为空");
        }
        this.delegate = delegate;
        this.fileStore = fileStore;
        this.fileMapper = new CrudRuntimeFileMapper(subjectMapper);
        this.taskMapper = new CrudRuntimeTaskMapper(subjectMapper, this.fileMapper);
        this.clock = clock;
    }

    public RuntimeTaskServiceAdapter(
        com.entloom.runtime.contract.task.TaskStore delegate,
        com.entloom.runtime.contract.file.FileStore fileStore,
        CrudRuntimeFileMapper fileMapper,
        CrudRuntimeTaskMapper taskMapper,
        Clock clock
    ) {
        if (delegate == null) {
            throw new IllegalArgumentException("runtime TaskStore 不能为空");
        }
        if (fileStore == null) {
            throw new IllegalArgumentException("runtime FileStore 不能为空");
        }
        if (fileMapper == null) {
            throw new IllegalArgumentException("fileMapper 不能为空");
        }
        if (taskMapper == null) {
            throw new IllegalArgumentException("taskMapper 不能为空");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock 不能为空");
        }
        this.delegate = delegate;
        this.fileStore = fileStore;
        this.fileMapper = fileMapper;
        this.taskMapper = taskMapper;
        this.clock = clock;
    }

    @Override
    public CrudTask create(CrudTask task) {
        if (task == null) {
            throw new IllegalArgumentException("CRUD 任务不能为空");
        }
        Instant now = clock.instant();
        CrudTask normalized = copy(task)
            .taskId(isBlank(task.getTaskId()) ? newTaskId() : task.getTaskId().trim())
            .createdAt(task.getCreatedAt() == null ? now : task.getCreatedAt())
            .updatedAt(now)
            .finishedAt(isTerminal(task.getStatus()) && task.getFinishedAt() == null ? now : task.getFinishedAt())
            .build();
        return fromRuntime(delegate.create(taskMapper.toRuntime(normalized)));
    }

    @Override
    public CrudTask getRequired(String taskId) {
        return fromRuntime(delegate.getRequired(requiredTaskId(taskId)));
    }

    @Override
    public CrudTask updateStatus(String taskId, CrudTaskStatus status, String message) {
        String requiredId = requiredTaskId(taskId);
        CrudTask current = getRequired(requiredId);
        Instant now = clock.instant();
        CrudTaskStatus actualStatus = status == null ? current.getStatus() : status;
        CrudTask updated = copy(current)
            .status(actualStatus)
            .message(message)
            .updatedAt(now)
            .finishedAt(isTerminal(actualStatus) ? now : current.getFinishedAt())
            .build();
        return fromRuntime(delegate.save(taskMapper.toRuntime(updated)));
    }

    @Override
    public CrudTask cancel(String taskId, String reason) {
        return updateStatus(taskId, CrudTaskStatus.CANCELED, reason == null ? "已取消" : reason);
    }

    private CrudTask fromRuntime(com.entloom.runtime.contract.task.Task task) {
        return taskMapper.toCrud(task, fileId -> fileMapper.toCrud(fileStore.getRequired(fileId)));
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
            throw new IllegalArgumentException("任务 ID 不能为空");
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
