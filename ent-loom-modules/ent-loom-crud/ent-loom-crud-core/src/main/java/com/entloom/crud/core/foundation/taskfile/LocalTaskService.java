package com.entloom.crud.core.foundation.taskfile;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.CrudOperationDomain;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
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
    private static final String ATTR_VALUE_PREFIX = "value.";
    private static final String ATTR_TYPE_PREFIX = "type.";
    private static final String ATTR_FORMAT_KEY = "__entloom_format";
    private static final String ATTR_COUNT_KEY = "__entloom_count";
    private static final String ATTR_FORMAT_VERSION = "2";
    private static final String ATTR_ENTRY_PREFIX = "entry.";

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
        writeScope(properties, "context.grantedScope.", context.getGrantedScope());
        writeScope(properties, "context.governanceScope.", context.getGovernanceScope());
        writeAttributes(properties, "context.audit.", context.getAuditContext());
        writeAttributes(properties, "context.attr.", context.getAttributes());
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
            .grantedScope(readScope(properties, "context.grantedScope."))
            .governanceScope(readScope(properties, "context.governanceScope."))
            .auditContext(readAttributes(properties, "context.audit."))
            .attributes(readAttributes(properties, "context.attr."))
            .build();
    }

    private static void writeScope(Properties properties, String prefix, CrudDataScope scope) {
        if (scope == null) {
            return;
        }
        set(properties, prefix + "explicitAll", Boolean.valueOf(scope.isExplicitAll()));
        for (Map.Entry<String, Object> entry : scope.getDimensions().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                set(properties, prefix + "dimension." + entry.getKey(), entry.getValue());
            }
        }
    }

    private static CrudDataScope readScope(Properties properties, String prefix) {
        String explicitAll = properties.getProperty(prefix + "explicitAll");
        Map<String, Object> dimensions = new LinkedHashMap<String, Object>();
        String dimensionPrefix = prefix + "dimension.";
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(dimensionPrefix)) {
                dimensions.put(name.substring(dimensionPrefix.length()), properties.getProperty(name));
            }
        }
        if (explicitAll == null && dimensions.isEmpty()) {
            return null;
        }
        return new CrudDataScope(Boolean.parseBoolean(explicitAll), dimensions);
    }

    /**
     * 任务文件只持久化可跨进程解析的标量属性，旧格式没有类型标记时按字符串兼容读取。
     */
    private static void writeAttributes(
        Properties properties,
        String prefix,
        Map<String, Object> attributes
    ) {
        if (attributes == null) {
            return;
        }
        set(properties, prefix + ATTR_FORMAT_KEY, ATTR_FORMAT_VERSION);
        int index = 0;
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || value == null) {
                continue;
            }
            String type = attributeType(value);
            String entryPrefix = prefix + ATTR_ENTRY_PREFIX + index + ".";
            set(properties, entryPrefix + "key", encodeAttributeKey(key));
            set(properties, entryPrefix + "type", type);
            set(properties, entryPrefix + "value", value);
            index++;
        }
        set(properties, prefix + ATTR_COUNT_KEY, Integer.valueOf(index));
    }

    private static Map<String, Object> readAttributes(Properties properties, String prefix) {
        if (ATTR_FORMAT_VERSION.equals(properties.getProperty(prefix + ATTR_FORMAT_KEY))) {
            return readVersionedAttributes(properties, prefix);
        }
        return readLegacyAttributes(properties, prefix);
    }

    private static Map<String, Object> readVersionedAttributes(Properties properties, String prefix) {
        Map<String, Object> attributes = new LinkedHashMap<String, Object>();
        String countValue = properties.getProperty(prefix + ATTR_COUNT_KEY, "0");
        int count;
        try {
            count = Integer.parseInt(countValue);
        } catch (NumberFormatException ex) {
            throw new CrudException(CrudErrorCode.INTERNAL_ERROR, "读取任务属性数量失败", ex);
        }
        if (count < 0) {
            throw new CrudException(CrudErrorCode.INTERNAL_ERROR, "读取任务属性数量失败: " + count);
        }
        for (int index = 0; index < count; index++) {
            String entryPrefix = prefix + ATTR_ENTRY_PREFIX + index + ".";
            String encodedKey = properties.getProperty(entryPrefix + "key");
            if (encodedKey == null) {
                throw new CrudException(CrudErrorCode.INTERNAL_ERROR, "任务属性缺少名称: " + index);
            }
            String key = decodeAttributeKey(encodedKey);
            String type = properties.getProperty(entryPrefix + "type", "string");
            attributes.put(key, decodeAttribute(key, type, properties.getProperty(entryPrefix + "value")));
        }
        return attributes;
    }

    private static Map<String, Object> readLegacyAttributes(Properties properties, String prefix) {
        Map<String, Object> attributes = new LinkedHashMap<String, Object>();
        String valuePrefix = prefix + ATTR_VALUE_PREFIX;
        String typePrefix = prefix + ATTR_TYPE_PREFIX;
        Set<String> typedValueNames = new HashSet<String>();
        for (String name : properties.stringPropertyNames()) {
            if (!name.startsWith(valuePrefix)) {
                continue;
            }
            String key = name.substring(valuePrefix.length());
            String type = properties.getProperty(typePrefix + key);
            if (!isAttributeType(type)) {
                continue;
            }
            typedValueNames.add(name);
            attributes.put(key, decodeAttribute(key, type, properties.getProperty(name)));
        }
        for (String name : properties.stringPropertyNames()) {
            if (!name.startsWith(prefix)
                || name.equals(prefix + ATTR_FORMAT_KEY)
                || name.equals(prefix + ATTR_COUNT_KEY)) {
                continue;
            }
            if (typedValueNames.contains(name)) {
                continue;
            }
            if (name.startsWith(typePrefix)
                && typedValueNames.contains(valuePrefix + name.substring(typePrefix.length()))) {
                continue;
            }
            String key = name.substring(prefix.length());
            if (!attributes.containsKey(key)) {
                attributes.put(key, properties.getProperty(name));
            }
        }
        return attributes;
    }

    private static boolean isAttributeType(String type) {
        if (type == null) {
            return false;
        }
        switch (type) {
            case "string":
            case "boolean":
            case "byte":
            case "short":
            case "integer":
            case "long":
            case "float":
            case "double":
            case "bigInteger":
            case "bigDecimal":
            case "char":
            case "instant":
            case "localDate":
            case "localDateTime":
            case "offsetDateTime":
            case "zonedDateTime":
                return true;
            default:
                return false;
        }
    }

    private static String encodeAttributeKey(String key) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeAttributeKey(String encodedKey) {
        try {
            return new String(Base64.getUrlDecoder().decode(encodedKey), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new CrudException(CrudErrorCode.INTERNAL_ERROR, "任务属性名称编码非法", ex);
        }
    }

    private static String attributeType(Object value) {
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Byte) {
            return "byte";
        }
        if (value instanceof Short) {
            return "short";
        }
        if (value instanceof Integer) {
            return "integer";
        }
        if (value instanceof Long) {
            return "long";
        }
        if (value instanceof Float) {
            return "float";
        }
        if (value instanceof Double) {
            return "double";
        }
        if (value instanceof BigInteger) {
            return "bigInteger";
        }
        if (value instanceof BigDecimal) {
            return "bigDecimal";
        }
        if (value instanceof Character) {
            return "char";
        }
        if (value instanceof Instant) {
            return "instant";
        }
        if (value instanceof LocalDate) {
            return "localDate";
        }
        if (value instanceof LocalDateTime) {
            return "localDateTime";
        }
        if (value instanceof OffsetDateTime) {
            return "offsetDateTime";
        }
        if (value instanceof ZonedDateTime) {
            return "zonedDateTime";
        }
        throw new ValidationException("任务属性不支持持久化类型: " + value.getClass().getName());
    }

    private static Object decodeAttribute(String key, String type, String value) {
        if (value == null || "string".equals(type)) {
            return value;
        }
        try {
            switch (type) {
                case "boolean":
                    return Boolean.valueOf(value);
                case "byte":
                    return Byte.valueOf(value);
                case "short":
                    return Short.valueOf(value);
                case "integer":
                    return Integer.valueOf(value);
                case "long":
                    return Long.valueOf(value);
                case "float":
                    return Float.valueOf(value);
                case "double":
                    return Double.valueOf(value);
                case "bigInteger":
                    return new BigInteger(value);
                case "bigDecimal":
                    return new BigDecimal(value);
                case "char":
                    if (value.length() != 1) {
                        throw new IllegalArgumentException("字符长度不是 1");
                    }
                    return Character.valueOf(value.charAt(0));
                case "instant":
                    return Instant.parse(value);
                case "localDate":
                    return LocalDate.parse(value);
                case "localDateTime":
                    return LocalDateTime.parse(value);
                case "offsetDateTime":
                    return OffsetDateTime.parse(value);
                case "zonedDateTime":
                    return ZonedDateTime.parse(value);
                default:
                    throw new IllegalArgumentException("未知属性类型: " + type);
            }
        } catch (RuntimeException ex) {
            throw new CrudException(CrudErrorCode.INTERNAL_ERROR, "读取任务属性失败: " + key, ex);
        }
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
        writeAttributes(properties, prefix + ATTR_PREFIX, file.getAttributes());
    }

    private static FileRef readFile(Properties properties, String prefix) {
        String fileId = properties.getProperty(prefix + "fileId");
        if (isBlank(fileId)) {
            return null;
        }
        Map<String, Object> attributes = readAttributes(properties, prefix + ATTR_PREFIX);
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
        String safeTaskId = requiredTaskId(taskId);
        Path path = rootDirectory.resolve(safeTaskId + ".properties").toAbsolutePath().normalize();
        if (!path.startsWith(rootDirectory) || !rootDirectory.equals(path.getParent())) {
            throw new ValidationException("任务 ID 不允许访问任务根目录之外的路径: " + taskId);
        }
        return path;
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
        String normalized = taskId.trim();
        if (normalized.indexOf('/') >= 0 || normalized.indexOf('\\') >= 0) {
            throw new ValidationException("任务 ID 不允许包含路径分隔符: " + taskId);
        }
        return normalized;
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
