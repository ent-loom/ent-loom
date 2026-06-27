package com.entloom.crud.core.capability.importing;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.ImportOperation;
import com.entloom.crud.core.capability.command.engine.CommandEngine;
import com.entloom.crud.core.capability.command.spec.BatchCommand;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.foundation.taskfile.CrudTask;
import com.entloom.crud.core.foundation.taskfile.CrudTaskContextSnapshot;
import com.entloom.crud.core.foundation.taskfile.CrudTaskStatus;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.core.foundation.taskfile.FileService;
import com.entloom.crud.core.foundation.taskfile.FileWriteRequest;
import com.entloom.crud.core.foundation.taskfile.TaskService;
import com.entloom.crud.core.runtime.engine.EngineCapability;
import com.entloom.crud.core.runtime.engine.EngineFeature;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 默认导入引擎。
 */
public class DefaultImportEngine implements ImportEngine {
    private static final EngineCapability CAPABILITY = EngineCapability.builder("default-import-engine")
        .operations(ImportOperation.VALIDATE, ImportOperation.SUBMIT)
        .features(EngineFeature.IMPORT_EXECUTION)
        .build();

    private final ImportFormatRegistry formatRegistry;
    private final FileService fileService;
    private final TaskService taskService;
    private final CommandEngine commandEngine;
    private final EntityMetaRegistry entityMetaRegistry;

    public DefaultImportEngine(
        ImportFormatRegistry formatRegistry,
        FileService fileService,
        TaskService taskService,
        CommandEngine commandEngine,
        EntityMetaRegistry entityMetaRegistry
    ) {
        this.formatRegistry = Objects.requireNonNull(formatRegistry, "formatRegistry 不能为空");
        this.fileService = Objects.requireNonNull(fileService, "fileService 不能为空");
        this.taskService = Objects.requireNonNull(taskService, "taskService 不能为空");
        this.commandEngine = Objects.requireNonNull(commandEngine, "commandEngine 不能为空");
        this.entityMetaRegistry = Objects.requireNonNull(entityMetaRegistry, "entityMetaRegistry 不能为空");
    }

    @Override
    public EngineCapability capability() {
        return CAPABILITY;
    }

    @Override
    public ImportResult execute(ImportSpec spec) {
        if (spec == null) {
            throw new ValidationException("导入请求规范(spec)不能为空");
        }
        capability().requireOperation(spec.getOperationKey());
        capability().requireFeature(EngineFeature.IMPORT_EXECUTION, "数据导入执行");
        if (spec.getOperation() == ImportOperation.VALIDATE || spec.getOperation() == ImportOperation.SUBMIT) {
            return validateAndMaybeWrite(spec);
        }
        throw new CrudException(CrudErrorCode.UNSUPPORTED_OPERATION, "导入引擎不处理任务操作: " + spec.getOperation());
    }

    private ImportResult validateAndMaybeWrite(ImportSpec spec) {
        ImportFormatDescriptor descriptor = formatRegistry.getRequired(spec.getFormat());
        EntityMeta meta = requireMeta(spec.getRootType());
        FileRef sourceRef = resolveSourceFile(spec);
        ImportParsedTable table = descriptor.getParser().parse(spec, fileService.read(sourceRef));
        List<ImportRowError> errors = validateHeaders(meta, table.getHeaders());
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (errors.isEmpty()) {
            rows = validateRows(meta, spec, table, errors);
        }
        int totalRows = table.getRows().size();
        int failedRows = countFailedRows(errors, totalRows);
        int validRows = Math.max(0, totalRows - failedRows);
        boolean write = spec.getOperation() == ImportOperation.SUBMIT && spec.getMode() != ImportMode.VALIDATE_ONLY;
        FileRef errorFile = null;
        int inserted = 0;
        int updated = 0;
        CrudTaskStatus status = CrudTaskStatus.SUCCEEDED;
        String message = errors.isEmpty() ? "导入校验通过" : "导入存在行错误";
        if (!errors.isEmpty()) {
            ImportResult invalidResult = buildResult(null, null, totalRows, validRows, failedRows, 0, 0, errors, spec.isAsync());
            errorFile = fileService.save(withFileAttributes(
                descriptor.getErrorFileWriter().writeErrorFile(spec, invalidResult),
                "IMPORT_ERROR",
                descriptor.getFormat()
            ));
        } else if (write) {
            WriteCounts counts = writeRows(spec, meta, rows);
            inserted = counts.inserted;
            updated = counts.updated;
            message = "导入完成";
        }
        CrudTask task = taskService.create(CrudTask.builder()
            .status(status)
            .contextSnapshot(CrudTaskContextSnapshot.fromSpec(spec, spec.getOperationKey()))
            .sourceFile(sourceRef)
            .errorFile(errorFile)
            .progress(Integer.valueOf(100))
            .message(message)
            .finishedAt(Instant.now())
            .build());
        return buildResult(task, errorFile, totalRows, validRows, failedRows, inserted, updated, errors, spec.isAsync());
    }

    private FileRef resolveSourceFile(ImportSpec spec) {
        FileRef source = spec.getSourceFile();
        if (source == null || isBlank(source.getFileId())) {
            throw new ValidationException("导入 sourceFile.fileId 不能为空");
        }
        return fileService.getRequired(source.getFileId());
    }

    private EntityMeta requireMeta(Class<?> rootType) {
        if (rootType == null) {
            throw new ValidationException("导入 rootType 不能为空");
        }
        EntityMeta meta = entityMetaRegistry.getEntityMeta(rootType);
        if (meta == null) {
            throw new ValidationException("实体元数据不存在: " + rootType.getName());
        }
        return meta;
    }

    private List<ImportRowError> validateHeaders(EntityMeta meta, List<String> headers) {
        List<ImportRowError> errors = new ArrayList<ImportRowError>();
        Set<String> seen = new LinkedHashSet<String>();
        if (headers == null || headers.isEmpty()) {
            errors.add(new ImportRowError(0, null, "HEADER_REQUIRED", "导入文件表头不能为空"));
            return errors;
        }
        for (String raw : headers) {
            String field = normalizeField(raw);
            if (!seen.add(field)) {
                errors.add(new ImportRowError(0, field, "DUPLICATE_HEADER", "导入表头重复: " + field));
                continue;
            }
            EntityFieldMeta fieldMeta = meta.resolveFieldMeta(field);
            if (fieldMeta == null || fieldMeta.isRelation() || !meta.getAllowedFields().contains(field)) {
                errors.add(new ImportRowError(0, field, "FIELD_NOT_ALLOWED", "导入字段不在白名单内: " + field));
            }
            if (field.equals(meta.getLogicDeleteField())) {
                errors.add(new ImportRowError(0, field, "FIELD_NOT_ALLOWED", "导入字段不允许包含逻辑删除字段: " + field));
            }
        }
        return errors;
    }

    private List<Map<String, Object>> validateRows(
        EntityMeta meta,
        ImportSpec spec,
        ImportParsedTable table,
        List<ImportRowError> errors
    ) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        boolean requireId = spec.getMode() == ImportMode.UPDATE || spec.getMode() == ImportMode.UPSERT;
        String idField = meta.getIdField();
        for (ImportParsedTable.ImportParsedRow parsedRow : table.getRows()) {
            Map<String, Object> converted = new LinkedHashMap<String, Object>();
            Map<String, Object> values = parsedRow.getValues();
            for (String rawField : table.getHeaders()) {
                String field = normalizeField(rawField);
                EntityFieldMeta fieldMeta = meta.resolveFieldMeta(field);
                if (fieldMeta == null || fieldMeta.isRelation()) {
                    continue;
                }
                Object rawValue = values.get(rawField);
                try {
                    Object convertedValue = convertValue(rawValue, fieldMeta.getJavaType());
                    if (!fieldMeta.isNullable() && isEmptyValue(convertedValue)) {
                        errors.add(new ImportRowError(parsedRow.getRowNumber(), field, "REQUIRED", "字段不能为空: " + field));
                    }
                    converted.put(field, convertedValue);
                } catch (Exception ex) {
                    errors.add(new ImportRowError(parsedRow.getRowNumber(), field, "TYPE_MISMATCH", "字段类型转换失败: " + field));
                }
            }
            if (requireId && !isBlank(idField) && isEmptyValue(converted.get(idField))) {
                errors.add(new ImportRowError(parsedRow.getRowNumber(), idField, "ID_REQUIRED", "更新或 UPSERT 导入必须提供主键字段: " + idField));
            }
            rows.add(converted);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private WriteCounts writeRows(ImportSpec spec, EntityMeta meta, List<Map<String, Object>> rows) {
        WriteCounts counts = new WriteCounts();
        if (rows == null || rows.isEmpty()) {
            return counts;
        }
        ImportWritePlan plan = buildWritePlan(spec, meta, rows);
        int batchSize = resolveBatchSize(spec);
        for (int from = 0; from < plan.items.size(); from += batchSize) {
            int to = Math.min(from + batchSize, plan.items.size());
            BatchCommand<Map<String, Object>> batch = BatchCommand.of(plan.items.subList(from, to));
            CommandSpec<BatchCommand<Map<String, Object>>> commandSpec = CommandSpec.<BatchCommand<Map<String, Object>>>builder()
                .op(plan.batchOperation)
                .scene(spec.getScene())
                .rootType(spec.getRootType())
                .entityClasses(spec.getEntityClasses())
                .subject(spec.getSubject())
                .attributes(spec.getAttributes())
                .grantedScope(spec.getGrantedScope())
                .governanceScope(spec.getGovernanceScope())
                .accessDecision(spec.getAccessDecision())
                .payload(batch)
                .resultType(Map.class)
                .build();
            Object result = commandEngine.action(commandSpec);
            counts.addAll(countBatchResult(result, to - from, plan.childOperation));
        }
        return counts;
    }

    private static ImportWritePlan buildWritePlan(ImportSpec spec, EntityMeta meta, List<Map<String, Object>> rows) {
        CommandOperation childOperation = toChildOperation(spec.getMode());
        CommandOperation batchOperation = toBatchOperation(spec.getMode());
        List<WriteCommand<Map<String, Object>>> items = new ArrayList<WriteCommand<Map<String, Object>>>();
        for (Map<String, Object> row : rows) {
            Object id = isBlank(meta.getIdField()) ? null : row.get(meta.getIdField());
            items.add(new WriteCommand<Map<String, Object>>(childOperation, id, row));
        }
        return new ImportWritePlan(batchOperation, childOperation, items);
    }

    private static CommandOperation toChildOperation(ImportMode mode) {
        if (mode == ImportMode.INSERT) {
            return CommandOperation.CREATE;
        }
        if (mode == ImportMode.UPDATE) {
            return CommandOperation.UPDATE;
        }
        return CommandOperation.SAVE_OR_UPDATE;
    }

    private static CommandOperation toBatchOperation(ImportMode mode) {
        if (mode == ImportMode.INSERT) {
            return CommandOperation.CREATE_BATCH;
        }
        if (mode == ImportMode.UPDATE) {
            return CommandOperation.UPDATE_BATCH;
        }
        return CommandOperation.SAVE_OR_UPDATE_BATCH;
    }

    @SuppressWarnings("unchecked")
    private static WriteCounts countBatchResult(Object result, int fallbackRows, CommandOperation fallbackOperation) {
        WriteCounts counts = new WriteCounts();
        Map<String, Object> resultMap = result instanceof Map<?, ?> ? (Map<String, Object>) result : null;
        Object items = resultMap == null ? null : resultMap.get("items");
        if (items instanceof List<?>) {
            for (Object item : (List<?>) items) {
                if (!(item instanceof Map<?, ?>)) {
                    continue;
                }
                Object actual = ((Map<?, ?>) item).get("operation");
                CommandOperation operation = CommandOperation.from(String.valueOf(actual));
                counts.add(operation == null ? fallbackOperation : operation);
            }
            return counts;
        }
        for (int i = 0; i < fallbackRows; i++) {
            counts.add(fallbackOperation);
        }
        return counts;
    }

    private static int resolveBatchSize(ImportSpec spec) {
        Integer batchSize = spec.getBatchSize();
        return batchSize == null || batchSize.intValue() <= 0 ? 200 : Math.min(batchSize.intValue(), 1000);
    }

    private static Object convertValue(Object value, Class<?> targetType) {
        if (isEmptyValue(value)) {
            return null;
        }
        if (targetType == null || targetType.isInstance(value)) {
            return value;
        }
        String text = String.valueOf(value).trim();
        if (targetType == String.class) {
            return text;
        }
        if (targetType == Long.class || targetType == long.class) {
            return Long.valueOf(text);
        }
        if (targetType == Integer.class || targetType == int.class) {
            return Integer.valueOf(text);
        }
        if (targetType == Short.class || targetType == short.class) {
            return Short.valueOf(text);
        }
        if (targetType == Double.class || targetType == double.class) {
            return Double.valueOf(text);
        }
        if (targetType == Float.class || targetType == float.class) {
            return Float.valueOf(text);
        }
        if (targetType == BigDecimal.class) {
            return new BigDecimal(text);
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.valueOf(text);
        }
        if (targetType == LocalDate.class) {
            return LocalDate.parse(text);
        }
        if (targetType == LocalDateTime.class) {
            return LocalDateTime.parse(text);
        }
        if (java.util.Date.class.isAssignableFrom(targetType)) {
            return java.util.Date.from(LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant());
        }
        return value;
    }

    private static FileWriteRequest withFileAttributes(FileWriteRequest request, String purpose, String format) {
        if (request == null) {
            throw new ValidationException("导入错误文件写入请求不能为空");
        }
        Map<String, Object> attributes = request.getAttributes();
        attributes.put("purpose", purpose);
        attributes.put("format", format);
        return FileWriteRequest.builder()
            .fileName(request.getFileName())
            .contentType(request.getContentType())
            .content(request.getContent())
            .attributes(attributes)
            .build();
    }

    private static ImportResult buildResult(
        CrudTask task,
        FileRef errorFile,
        int totalRows,
        int validRows,
        int failedRows,
        int insertedRows,
        int updatedRows,
        List<ImportRowError> rowErrors,
        boolean async
    ) {
        return ImportResult.builder()
            .accepted(true)
            .async(async)
            .task(task)
            .errorFile(errorFile)
            .totalRows(totalRows)
            .validRows(validRows)
            .failedRows(failedRows)
            .insertedRows(insertedRows)
            .updatedRows(updatedRows)
            .rowErrors(rowErrors)
            .build();
    }

    private static int countFailedRows(List<ImportRowError> errors, int totalRows) {
        Set<Integer> rows = new LinkedHashSet<Integer>();
        for (ImportRowError error : errors) {
            if (error.getRowNumber() == 0) {
                return totalRows;
            }
            rows.add(Integer.valueOf(error.getRowNumber()));
        }
        return rows.size();
    }

    private static String normalizeField(String field) {
        if (isBlank(field)) {
            throw new ValidationException("字段名不能为空");
        }
        return field.trim();
    }

    private static boolean isEmptyValue(Object value) {
        return value == null || (value instanceof String && ((String) value).trim().isEmpty());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class WriteCounts {
        private int inserted;
        private int updated;

        private void add(CommandOperation operation) {
            if (operation == CommandOperation.UPDATE) {
                updated++;
            } else {
                inserted++;
            }
        }

        private void addAll(WriteCounts other) {
            if (other != null) {
                inserted += other.inserted;
                updated += other.updated;
            }
        }
    }

    private static final class ImportWritePlan {
        private final CommandOperation batchOperation;
        private final CommandOperation childOperation;
        private final List<WriteCommand<Map<String, Object>>> items;

        private ImportWritePlan(
            CommandOperation batchOperation,
            CommandOperation childOperation,
            List<WriteCommand<Map<String, Object>>> items
        ) {
            this.batchOperation = batchOperation;
            this.childOperation = childOperation;
            this.items = items;
        }
    }
}
