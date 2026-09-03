package com.entloom.crud.runtime.adapter;

import com.entloom.crud.core.foundation.taskfile.CrudTask;
import com.entloom.crud.core.foundation.taskfile.CrudTaskStatus;
import com.entloom.crud.core.foundation.taskfile.TaskService;
import com.entloom.runtime.core.task.DefaultTaskLifecycleService;
import com.entloom.runtime.core.task.TaskLifecycleService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 使用 runtime {@code TaskStore} 实现 CRUD {@code TaskService}。
 *
 * <p>runtime 只保存 source/result 文件；CRUD 的 errorFile ID 放在 runtime 任务扩展属性中。</p>
 */
public final class RuntimeTaskServiceAdapter implements TaskService {
    private final TaskLifecycleService lifecycleService;
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
        this.lifecycleService = new DefaultTaskLifecycleService(delegate, clock);
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
        this(new DefaultTaskLifecycleService(delegate, clock), fileStore, fileMapper, taskMapper, clock);
    }

    /**
     * 使用已配置的 runtime 生命周期服务实现 CRUD 任务 SPI。
     *
     * <p>调用方可以在这里注入持久化或并发控制实现；适配器不再直接写入 TaskStore。</p>
     */
    public RuntimeTaskServiceAdapter(
        TaskLifecycleService lifecycleService,
        com.entloom.runtime.contract.file.FileStore fileStore
    ) {
        this(lifecycleService, fileStore, new RuntimeSubjectContextMapper(), Clock.systemUTC());
    }

    public RuntimeTaskServiceAdapter(
        TaskLifecycleService lifecycleService,
        com.entloom.runtime.contract.file.FileStore fileStore,
        RuntimeSubjectContextMapper subjectMapper,
        Clock clock
    ) {
        if (subjectMapper == null) {
            throw new IllegalArgumentException("subjectMapper 不能为空");
        }
        if (lifecycleService == null) {
            throw new IllegalArgumentException("runtime TaskLifecycleService 不能为空");
        }
        if (fileStore == null) {
            throw new IllegalArgumentException("runtime FileStore 不能为空");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock 不能为空");
        }
        this.lifecycleService = lifecycleService;
        this.fileStore = fileStore;
        this.fileMapper = new CrudRuntimeFileMapper(subjectMapper);
        this.taskMapper = new CrudRuntimeTaskMapper(subjectMapper, this.fileMapper);
        this.clock = clock;
    }

    public RuntimeTaskServiceAdapter(
        TaskLifecycleService lifecycleService,
        com.entloom.runtime.contract.file.FileStore fileStore,
        CrudRuntimeFileMapper fileMapper,
        CrudRuntimeTaskMapper taskMapper,
        Clock clock
    ) {
        if (lifecycleService == null) {
            throw new IllegalArgumentException("runtime TaskLifecycleService 不能为空");
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
        this.lifecycleService = lifecycleService;
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
        return fromRuntime(lifecycleService.create(taskMapper.toRuntime(normalized)));
    }

    @Override
    public CrudTask getRequired(String taskId) {
        return fromRuntime(lifecycleService.getRequired(requiredTaskId(taskId)));
    }

    @Override
    public CrudTask updateStatus(String taskId, CrudTaskStatus status, String message) {
        String requiredId = requiredTaskId(taskId);
        com.entloom.runtime.contract.task.Task runtimeCurrent = lifecycleService.getRequired(requiredId);
        CrudTask current = fromRuntime(runtimeCurrent);
        CrudTaskStatus actualStatus = status == null ? current.getStatus() : status;
        if (current.getStatus() == actualStatus) {
            if (actualStatus == CrudTaskStatus.RUNNING && message != null) {
                return fromRuntime(lifecycleService.updateMessage(requiredId, message));
            }
            return current;
        }
        switch (actualStatus) {
            case PENDING:
                throw new IllegalStateException("不能将任务回退为 PENDING: " + requiredId);
            case RUNNING:
                return fromRuntime(lifecycleService.start(requiredId,
                    message == null ? "开始执行" : message));
            case SUCCEEDED:
                return fromRuntime(lifecycleService.succeed(requiredId,
                    runtimeCurrent.getResultFile(), message));
            case FAILED:
                return fromRuntime(lifecycleService.fail(requiredId, message));
            case CANCELED:
                return fromRuntime(lifecycleService.cancel(requiredId, message));
            default:
                throw new IllegalArgumentException("未知 CRUD 任务状态: " + actualStatus);
        }
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
