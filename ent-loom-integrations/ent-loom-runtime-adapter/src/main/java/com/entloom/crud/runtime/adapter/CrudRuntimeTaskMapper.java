package com.entloom.crud.runtime.adapter;

import com.entloom.crud.api.enums.CrudOperationDomain;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.foundation.taskfile.CrudTask;
import com.entloom.crud.core.foundation.taskfile.CrudTaskContextSnapshot;
import com.entloom.crud.core.foundation.taskfile.CrudTaskStatus;
import com.entloom.crud.core.foundation.taskfile.CrudTaskType;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.runtime.core.task.RuntimeAccessDeniedException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * CRUD 任务与 runtime 任务快照之间的映射。
 *
 * <p>runtime 只接收字符串属性；实体类型、操作、场景和数据范围均使用稳定的适配器命名空间保存。</p>
 */
public final class CrudRuntimeTaskMapper {
    /** runtime 任务属性中的错误文件 ID。 */
    public static final String ERROR_FILE_ID_ATTRIBUTE = "entloom.crud.errorFileId";

    private static final String PREFIX = "entloom.crud.";
    private static final String SCENE = PREFIX + "scene";
    private static final String ROOT_TYPE = PREFIX + "rootType";
    private static final String OPERATION_DOMAIN = PREFIX + "operationDomain";
    private static final String OPERATION = PREFIX + "operation";
    private static final String GRANTED_SCOPE = PREFIX + "scope.granted.";
    private static final String GOVERNANCE_SCOPE = PREFIX + "scope.governance.";
    private static final String AUDIT = PREFIX + "audit.";
    private static final String CONTEXT = PREFIX + "context.";

    private final RuntimeSubjectContextMapper subjectMapper;
    private final CrudRuntimeFileMapper fileMapper;
    private final ClassLoader classLoader;

    public CrudRuntimeTaskMapper() {
        this(new RuntimeSubjectContextMapper(), new CrudRuntimeFileMapper());
    }

    public CrudRuntimeTaskMapper(RuntimeSubjectContextMapper subjectMapper, CrudRuntimeFileMapper fileMapper) {
        this(subjectMapper, fileMapper, defaultClassLoader());
    }

    public CrudRuntimeTaskMapper(RuntimeSubjectContextMapper subjectMapper, CrudRuntimeFileMapper fileMapper,
                                 ClassLoader classLoader) {
        if (subjectMapper == null) {
            throw new IllegalArgumentException("subjectMapper 不能为空");
        }
        if (fileMapper == null) {
            throw new IllegalArgumentException("fileMapper 不能为空");
        }
        if (classLoader == null) {
            throw new IllegalArgumentException("classLoader 不能为空");
        }
        this.subjectMapper = subjectMapper;
        this.fileMapper = fileMapper;
        this.classLoader = classLoader;
    }

    public com.entloom.runtime.contract.task.Task toRuntime(CrudTask source) {
        if (source == null) {
            throw new IllegalArgumentException("CRUD 任务不能为空");
        }
        CrudTaskContextSnapshot snapshot = source.getContextSnapshot();
        if (snapshot == null) {
            throw new IllegalArgumentException("CRUD 任务上下文快照不能为空: " + source.getTaskId());
        }
        if (snapshot.getSubject() == null) {
            throw new IllegalArgumentException("CRUD 任务主体不能为空: " + source.getTaskId());
        }
        if (snapshot.getRootType() == null) {
            throw new IllegalArgumentException("CRUD 任务 rootType 不能为空: " + source.getTaskId());
        }
        if (snapshot.getOperationKey() == null) {
            throw new IllegalArgumentException("CRUD 任务 operationKey 不能为空: " + source.getTaskId());
        }
        com.entloom.runtime.contract.context.SubjectContext runtimeSubject =
            subjectMapper.toRuntime(snapshot.getSubject());
        Map<String, String> attributes = taskAttributes(snapshot);
        if (source.getErrorFile() != null) {
            assertFileOwner(fileMapper.toRuntime(source.getErrorFile()), runtimeSubject, "errorFile");
            attributes.put(ERROR_FILE_ID_ATTRIBUTE, required(source.getErrorFile().getFileId(), "errorFileId"));
        }
        com.entloom.runtime.contract.file.FileRef sourceFile = source.getSourceFile() == null
            ? null : fileMapper.toRuntime(source.getSourceFile());
        assertFileOwner(sourceFile, runtimeSubject, "sourceFile");
        com.entloom.runtime.contract.file.FileRef resultFile = source.getResultFile() == null
            ? null : fileMapper.toRuntime(source.getResultFile());
        assertFileOwner(resultFile, runtimeSubject, "resultFile");
        return com.entloom.runtime.contract.task.Task.builder()
            .taskId(required(source.getTaskId(), "taskId"))
            .taskType(taskType(snapshot.getOperationKey().getDomain()).name())
            .subject(runtimeSubject)
            .status(toRuntimeStatus(source.getStatus()))
            .progress(source.getProgress() == null ? 0 : source.getProgress().intValue())
            .message(source.getMessage())
            .sourceFile(sourceFile)
            .resultFile(resultFile)
            .createdAt(requiredInstant(source.getCreatedAt(), "createdAt"))
            .updatedAt(requiredInstant(source.getUpdatedAt(), "updatedAt"))
            .finishedAt(source.getFinishedAt())
            .attributes(attributes)
            .build();
    }

    public CrudTask toCrud(com.entloom.runtime.contract.task.Task source,
                           Function<String, FileRef> fileResolver) {
        if (source == null) {
            throw new IllegalArgumentException("runtime 任务不能为空");
        }
        CrudTaskContextSnapshot snapshot = toCrudSnapshot(source);
        if (!taskType(snapshot.getOperationKey().getDomain()).name().equals(source.getTaskType())) {
            throw new IllegalArgumentException("runtime 任务类型与 CRUD 操作域不一致: " + source.getTaskId());
        }
        CrudTask.Builder builder = CrudTask.builder()
            .taskId(source.getTaskId())
            .status(toCrudStatus(source.getStatus()))
            .contextSnapshot(snapshot)
            .sourceFile(source.getSourceFile() == null ? null : fileMapper.toCrud(source.getSourceFile()))
            .resultFile(source.getResultFile() == null ? null : fileMapper.toCrud(source.getResultFile()))
            .progress(Integer.valueOf(source.getProgress()))
            .message(source.getMessage())
            .createdAt(source.getCreatedAt())
            .updatedAt(source.getUpdatedAt())
            .finishedAt(source.getFinishedAt());
        String errorFileId = source.getAttributes().get(ERROR_FILE_ID_ATTRIBUTE);
        if (errorFileId != null) {
            if (fileResolver == null) {
                throw new IllegalArgumentException("映射错误文件时 fileResolver 不能为空");
            }
            FileRef errorFile = fileResolver.apply(errorFileId);
            if (errorFile == null) {
                throw new IllegalArgumentException("错误文件不存在: " + errorFileId);
            }
            builder.errorFile(errorFile);
        }
        return builder.build();
    }

    public com.entloom.runtime.contract.task.TaskStatus toRuntimeStatus(CrudTaskStatus status) {
        if (status == null) {
            return com.entloom.runtime.contract.task.TaskStatus.CREATED;
        }
        switch (status) {
            case PENDING:
                return com.entloom.runtime.contract.task.TaskStatus.CREATED;
            case RUNNING:
                return com.entloom.runtime.contract.task.TaskStatus.RUNNING;
            case SUCCEEDED:
                return com.entloom.runtime.contract.task.TaskStatus.SUCCEEDED;
            case FAILED:
                return com.entloom.runtime.contract.task.TaskStatus.FAILED;
            case CANCELED:
                return com.entloom.runtime.contract.task.TaskStatus.CANCELED;
            case EXPIRED:
                throw new IllegalArgumentException("runtime 任务暂不支持 EXPIRED 状态");
            default:
                throw new IllegalArgumentException("未知 CRUD 任务状态: " + status);
        }
    }

    public CrudTaskStatus toCrudStatus(com.entloom.runtime.contract.task.TaskStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("runtime 任务状态不能为空");
        }
        switch (status) {
            case CREATED:
                return CrudTaskStatus.PENDING;
            case RUNNING:
                return CrudTaskStatus.RUNNING;
            case SUCCEEDED:
                return CrudTaskStatus.SUCCEEDED;
            case FAILED:
                return CrudTaskStatus.FAILED;
            case CANCELED:
                return CrudTaskStatus.CANCELED;
            default:
                throw new IllegalArgumentException("未知 runtime 任务状态: " + status);
        }
    }

    private CrudTaskContextSnapshot toCrudSnapshot(com.entloom.runtime.contract.task.Task source) {
        Map<String, String> attributes = source.getAttributes();
        String domain = required(attributes.get(OPERATION_DOMAIN), "operationDomain");
        String operation = required(attributes.get(OPERATION), "operation");
        CrudOperationKey operationKey;
        try {
            operationKey = CrudOperationKey.of(CrudOperationDomain.valueOf(domain), operation);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("runtime 任务 operationKey 无效", ex);
        }
        CrudTaskContextSnapshot.Builder builder = CrudTaskContextSnapshot.builder()
            .scene(attributes.get(SCENE))
            .rootType(resolveRootType(attributes.get(ROOT_TYPE)))
            .operationKey(operationKey)
            .subject(subjectMapper.toCrud(source.getSubject()))
            .grantedScope(readScope(attributes, GRANTED_SCOPE))
            .governanceScope(readScope(attributes, GOVERNANCE_SCOPE))
            .auditContext(readPrefixedAttributes(attributes, AUDIT))
            .attributes(readPrefixedAttributes(attributes, CONTEXT));
        return builder.build();
    }

    private Map<String, String> taskAttributes(CrudTaskContextSnapshot snapshot) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        put(result, SCENE, snapshot.getScene());
        put(result, ROOT_TYPE, snapshot.getRootType().getName());
        put(result, OPERATION_DOMAIN, snapshot.getOperationKey().getDomain().name());
        put(result, OPERATION, snapshot.getOperationKey().getOperation());
        writeScope(result, GRANTED_SCOPE, snapshot.getGrantedScope());
        writeScope(result, GOVERNANCE_SCOPE, snapshot.getGovernanceScope());
        writeAttributes(result, AUDIT, snapshot.getAuditContext());
        writeAttributes(result, CONTEXT, snapshot.getAttributes());
        return result;
    }

    private static void writeScope(Map<String, String> target, String prefix, CrudDataScope scope) {
        if (scope == null) {
            return;
        }
        target.put(prefix + "explicitAll", RuntimeAttributeCodec.encode(Boolean.valueOf(scope.isExplicitAll())));
        for (Map.Entry<String, Object> entry : scope.getDimensions().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                target.put(prefix + "dimension." + entry.getKey(), RuntimeAttributeCodec.encode(entry.getValue()));
            }
        }
    }

    private static void writeAttributes(Map<String, String> target, String prefix, Map<String, Object> source) {
        if (source == null) {
            return;
        }
        Map<String, String> encoded = RuntimeAttributeCodec.toRuntimeAttributes(source);
        for (Map.Entry<String, String> entry : encoded.entrySet()) {
            target.put(prefix + entry.getKey(), entry.getValue());
        }
    }

    private static CrudDataScope readScope(Map<String, String> source, String prefix) {
        String explicitAll = source.get(prefix + "explicitAll");
        Map<String, Object> dimensions = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = entry.getKey();
            String dimensionPrefix = prefix + "dimension.";
            if (key.startsWith(dimensionPrefix)) {
                dimensions.put(key.substring(dimensionPrefix.length()), RuntimeAttributeCodec.decode(entry.getValue()));
            }
        }
        if (explicitAll == null && dimensions.isEmpty()) {
            return null;
        }
        Object decoded = RuntimeAttributeCodec.decode(explicitAll);
        boolean all = decoded instanceof Boolean ? ((Boolean) decoded).booleanValue() : Boolean.parseBoolean(explicitAll);
        return new CrudDataScope(all, dimensions);
    }

    private static Map<String, Object> readPrefixedAttributes(Map<String, String> source, String prefix) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.put(entry.getKey().substring(prefix.length()), RuntimeAttributeCodec.decode(entry.getValue()));
            }
        }
        return result;
    }

    private CrudTaskType taskType(CrudOperationDomain domain) {
        if (domain == CrudOperationDomain.IMPORT) {
            return CrudTaskType.IMPORT;
        }
        if (domain == CrudOperationDomain.EXPORT) {
            return CrudTaskType.EXPORT;
        }
        throw new IllegalArgumentException("runtime 适配器不支持该 CRUD 操作域: " + domain);
    }

    private Class<?> resolveRootType(String name) {
        String typeName = required(name, "rootType");
        try {
            return Class.forName(typeName, false, classLoader);
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException("无法解析任务 rootType: " + typeName, ex);
        }
    }

    private static void put(Map<String, String> target, String key, Object value) {
        if (value != null) {
            target.put(key, String.valueOf(value));
        }
    }

    private static void assertFileOwner(
        com.entloom.runtime.contract.file.FileRef file,
        com.entloom.runtime.contract.context.SubjectContext subject,
        String name
    ) {
        if (file != null && !file.getOwner().sameSubject(subject)) {
            throw new RuntimeAccessDeniedException("任务" + name + "主体与任务主体不一致");
        }
    }

    private static Instant requiredInstant(Instant value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("任务" + name + "不能为空");
        }
        return value;
    }

    private static String required(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value.trim();
    }

    private static ClassLoader defaultClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader == null ? CrudRuntimeTaskMapper.class.getClassLoader() : loader;
    }
}
