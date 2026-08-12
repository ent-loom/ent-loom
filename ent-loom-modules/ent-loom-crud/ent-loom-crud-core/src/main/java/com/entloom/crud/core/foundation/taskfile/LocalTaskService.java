package com.entloom.crud.core.foundation.taskfile;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.CrudOperationDomain;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.ValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 默认本地磁盘任务服务。
 */
public class LocalTaskService implements TaskService {
    private static final String SOURCE_PREFIX = "sourceFile.";
    private static final String RESULT_PREFIX = "resultFile.";
    private static final String ERROR_PREFIX = "errorFile.";
    private static final String ATTR_PREFIX = "attr.";

    private final Path rootDirectory;

    public LocalTaskService(String rootDirectory) {
        String root = rootDirectory == null || rootDirectory.trim().isEmpty()
            ? Paths.get(System.getProperty("java.io.tmpdir"), "entloom-crud", "tasks").toString()
            : rootDirectory.trim();
        this.rootDirectory = Paths.get(root).toAbsolutePath().normalize();
        ensureDirectory();
    }

    @Override
    public CrudTask create(CrudTask task) {
        if (task == null) {
            throw new ValidationException("任务不能为空");
        }
        Instant now = Instant.now();
        String taskId = isBlank(task.getTaskId()) ? newId() : task.getTaskId().trim();
        Path path = taskPath(taskId);
        if (Files.exists(path)) {
            throw new ValidationException("任务已存在: " + taskId);
        }
        CrudTask created = copy(task)
            .taskId(taskId)
            .createdAt(task.getCreatedAt() == null ? now : task.getCreatedAt())
            .updatedAt(now)
            .finishedAt(isTerminal(task.getStatus()) && task.getFinishedAt() == null ? now : task.getFinishedAt())
            .build();
        store(created);
        return created;
    }

    @Override
    public CrudTask getRequired(String taskId) {
        String id = requiredTaskId(taskId);
        Path path = taskPath(id);
        if (!Files.exists(path)) {
            throw new CrudException(CrudErrorCode.TASK_NOT_FOUND, "任务不存在: " + id);
        }
        return load(path);
    }

    @Override
    public CrudTask updateStatus(String taskId, CrudTaskStatus status, String message) {
        CrudTask current = getRequired(taskId);
        Instant now = Instant.now();
        CrudTask updated = copy(current)
            .status(status == null ? current.getStatus() : status)
            .message(message)
            .updatedAt(now)
            .finishedAt(isTerminal(status) ? now : current.getFinishedAt())
            .build();
        store(updated);
        return updated;
    }

    @Override
    public CrudTask cancel(String taskId, String reason) {
        return updateStatus(taskId, CrudTaskStatus.CANCELED, reason == null ? "已取消" : reason);
    }

    private void store(CrudTask task) {
        Properties properties = new Properties();
        set(properties, "taskId", task.getTaskId());
        set(properties, "status", task.getStatus() == null ? null : task.getStatus().name());
        set(properties, "progress", task.getProgress());
        set(properties, "message", task.getMessage());
        set(properties, "createdAt", task.getCreatedAt());
        set(properties, "updatedAt", task.getUpdatedAt());
        set(properties, "finishedAt", task.getFinishedAt());
        writeContext(properties, task.getContextSnapshot());
        writeFile(properties, SOURCE_PREFIX, task.getSourceFile());
        writeFile(properties, RESULT_PREFIX, task.getResultFile());
        writeFile(properties, ERROR_PREFIX, task.getErrorFile());
        try (OutputStream output = Files.newOutputStream(taskPath(task.getTaskId()))) {
            properties.store(output, "ent-loom-crud task metadata");
        } catch (IOException ex) {
            throw new CrudException(CrudErrorCode.INTERNAL_ERROR, "写入任务元数据失败: " + task.getTaskId(), ex);
        }
    }

    private CrudTask load(Path path) {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException ex) {
            throw new CrudException(CrudErrorCode.INTERNAL_ERROR, "读取任务元数据失败", ex);
        }
        return CrudTask.builder()
            .taskId(properties.getProperty("taskId"))
            .status(CrudTaskStatus.valueOf(properties.getProperty("status", CrudTaskStatus.PENDING.name())))
            .progress(integerValue(properties.getProperty("progress")))
            .message(properties.getProperty("message"))
            .createdAt(instantValue(properties.getProperty("createdAt")))
            .updatedAt(instantValue(properties.getProperty("updatedAt")))
            .finishedAt(instantValue(properties.getProperty("finishedAt")))
            .contextSnapshot(readContext(properties))
            .sourceFile(readFile(properties, SOURCE_PREFIX))
            .resultFile(readFile(properties, RESULT_PREFIX))
            .errorFile(readFile(properties, ERROR_PREFIX))
            .build();
    }

    private static void writeContext(Properties properties, CrudTaskContextSnapshot context) {
        if (context == null) {
            return;
        }
        set(properties, "context.scene", context.getScene());
        set(properties, "context.rootType", context.getRootType() == null ? null : context.getRootType().getName());
        set(properties, "context.operationDomain", context.getOperationKey() == null ? null : context.getOperationKey().getDomain().name());
        set(properties, "context.operation", context.getOperationKey() == null ? null : context.getOperationKey().getOperation());
        SubjectContext subject = context.getSubject();
        if (subject != null) {
            set(properties, "context.subjectId", subject.getSubjectId());
            set(properties, "context.tenantId", subject.getTenantId());
            set(properties, "context.orgId", subject.getOrgId());
        }
    }

    private static CrudTaskContextSnapshot readContext(Properties properties) {
        String rootTypeName = properties.getProperty("context.rootType");
        Class<?> rootType = null;
        if (!isBlank(rootTypeName)) {
            try {
                rootType = Class.forName(rootTypeName);
            } catch (ClassNotFoundException ignored) {
                rootType = null;
            }
        }
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId(properties.getProperty("context.subjectId"));
        subject.setTenantId(properties.getProperty("context.tenantId"));
        subject.setOrgId(properties.getProperty("context.orgId"));
        CrudOperationKey operationKey = null;
        String domain = properties.getProperty("context.operationDomain");
        String operation = properties.getProperty("context.operation");
        if (!isBlank(domain) && !isBlank(operation)) {
            operationKey = CrudOperationKey.of(CrudOperationDomain.valueOf(domain), operation);
        }
        return CrudTaskContextSnapshot.builder()
            .scene(properties.getProperty("context.scene"))
            .rootType(rootType)
            .operationKey(operationKey)
            .subject(subject)
            .build();
    }

    private static void writeFile(Properties properties, String prefix, FileRef file) {
        if (file == null) {
            return;
        }
        set(properties, prefix + "fileId", file.getFileId());
        set(properties, prefix + "fileName", file.getFileName());
        set(properties, prefix + "contentType", file.getContentType());
        set(properties, prefix + "size", file.getSize());
        set(properties, prefix + "storageType", file.getStorageType() == null ? null : file.getStorageType().name());
        set(properties, prefix + "storageKey", file.getStorageKey());
        set(properties, prefix + "expiresAt", file.getExpiresAt());
        for (Map.Entry<String, Object> entry : file.getAttributes().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                set(properties, prefix + ATTR_PREFIX + entry.getKey(), entry.getValue());
            }
        }
    }

    private static FileRef readFile(Properties properties, String prefix) {
        String fileId = properties.getProperty(prefix + "fileId");
        if (isBlank(fileId)) {
            return null;
        }
        Map<String, Object> attributes = new HashMap<String, Object>();
        String attrPrefix = prefix + ATTR_PREFIX;
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(attrPrefix)) {
                attributes.put(name.substring(attrPrefix.length()), properties.getProperty(name));
            }
        }
        return FileRef.builder()
            .fileId(fileId)
            .fileName(properties.getProperty(prefix + "fileName"))
            .contentType(properties.getProperty(prefix + "contentType"))
            .size(longValue(properties.getProperty(prefix + "size")))
            .storageType(CrudFileStorageType.valueOf(properties.getProperty(prefix + "storageType", CrudFileStorageType.LOCAL.name())))
            .storageKey(properties.getProperty(prefix + "storageKey"))
            .expiresAt(instantValue(properties.getProperty(prefix + "expiresAt")))
            .attributes(attributes)
            .build();
    }

    private Path taskPath(String taskId) {
        return rootDirectory.resolve(taskId + ".properties").toAbsolutePath().normalize();
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(rootDirectory);
        } catch (IOException ex) {
            throw new CrudException(CrudErrorCode.INTERNAL_ERROR, "初始化本地任务目录失败: " + rootDirectory, ex);
        }
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

    private static void set(Properties properties, String key, Object value) {
        if (value != null) {
            properties.setProperty(key, String.valueOf(value));
        }
    }

    private static Integer integerValue(String value) {
        return isBlank(value) ? null : Integer.valueOf(value);
    }

    private static Long longValue(String value) {
        return isBlank(value) ? null : Long.valueOf(value);
    }

    private static Instant instantValue(String value) {
        return isBlank(value) ? null : Instant.parse(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
