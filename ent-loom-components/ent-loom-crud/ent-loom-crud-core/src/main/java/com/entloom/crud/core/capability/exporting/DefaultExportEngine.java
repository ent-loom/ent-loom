package com.entloom.crud.core.capability.exporting;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.ExportOperation;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.core.capability.query.engine.QueryEngine;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 默认导出引擎。
 */
public class DefaultExportEngine implements ExportEngine {
    private static final int DEFAULT_EXPORT_LIMIT = 1000;
    private static final int DEFAULT_PREVIEW_LIMIT = 20;
    private static final int MAX_SYNC_ROWS = 10000;
    private static final EngineCapability CAPABILITY = EngineCapability.builder("default-export-engine")
        .operations(ExportOperation.PREVIEW, ExportOperation.SUBMIT)
        .features(EngineFeature.EXPORT_EXECUTION)
        .build();

    private final QueryEngine queryEngine;
    private final ExportFormatRegistry formatRegistry;
    private final FileService fileService;
    private final TaskService taskService;
    private final EntityMetaRegistry entityMetaRegistry;
    private final ExportColumnResolver columnResolver;
    private final ExportValueRenderer valueRenderer;
    private final ExportRenderOptionsResolver renderOptionsResolver;

    public DefaultExportEngine(
        QueryEngine queryEngine,
        ExportFormatRegistry formatRegistry,
        FileService fileService,
        TaskService taskService,
        EntityMetaRegistry entityMetaRegistry
    ) {
        this(
            queryEngine,
            formatRegistry,
            fileService,
            taskService,
            entityMetaRegistry,
            new DefaultExportColumnResolver(),
            new DefaultExportValueRenderer(),
            new ExportRenderOptionsResolver()
        );
    }

    public DefaultExportEngine(
        QueryEngine queryEngine,
        ExportFormatRegistry formatRegistry,
        FileService fileService,
        TaskService taskService,
        EntityMetaRegistry entityMetaRegistry,
        String applicationDefaultTimezone
    ) {
        this(
            queryEngine,
            formatRegistry,
            fileService,
            taskService,
            entityMetaRegistry,
            new DefaultExportColumnResolver(),
            new DefaultExportValueRenderer(),
            new ExportRenderOptionsResolver(applicationDefaultTimezone)
        );
    }

    public DefaultExportEngine(
        QueryEngine queryEngine,
        ExportFormatRegistry formatRegistry,
        FileService fileService,
        TaskService taskService,
        EntityMetaRegistry entityMetaRegistry,
        ExportColumnResolver columnResolver,
        ExportValueRenderer valueRenderer,
        ExportRenderOptionsResolver renderOptionsResolver
    ) {
        this.queryEngine = Objects.requireNonNull(queryEngine, "queryEngine 不能为空");
        this.formatRegistry = Objects.requireNonNull(formatRegistry, "formatRegistry 不能为空");
        this.fileService = Objects.requireNonNull(fileService, "fileService 不能为空");
        this.taskService = Objects.requireNonNull(taskService, "taskService 不能为空");
        this.entityMetaRegistry = Objects.requireNonNull(entityMetaRegistry, "entityMetaRegistry 不能为空");
        this.columnResolver = Objects.requireNonNull(columnResolver, "columnResolver 不能为空");
        this.valueRenderer = Objects.requireNonNull(valueRenderer, "valueRenderer 不能为空");
        this.renderOptionsResolver = Objects.requireNonNull(renderOptionsResolver, "renderOptionsResolver 不能为空");
    }

    @Override
    public EngineCapability capability() {
        return CAPABILITY;
    }

    @Override
    public ExportResult execute(ExportSpec spec) {
        if (spec == null) {
            throw new ValidationException("导出请求规范(spec)不能为空");
        }
        capability().requireOperation(spec.getOperationKey());
        capability().requireFeature(EngineFeature.EXPORT_EXECUTION, "数据导出执行");
        if (spec.getOperation() == ExportOperation.PREVIEW) {
            return preview(spec);
        }
        if (spec.getOperation() == ExportOperation.SUBMIT) {
            return submit(spec);
        }
        throw new CrudException(CrudErrorCode.UNSUPPORTED_OPERATION, "导出引擎不处理任务操作: " + spec.getOperation());
    }

    private ExportResult preview(ExportSpec spec) {
        formatRegistry.getRequired(spec.getFormat());
        int limit = boundedLimit(spec.getLimit(), DEFAULT_PREVIEW_LIMIT, DEFAULT_PREVIEW_LIMIT);
        ExportTable table = buildTable(spec, limit);
        return ExportResult.builder()
            .accepted(true)
            .async(false)
            .totalRows(table.getRows().size())
            .columns(table.getColumns())
            .previewRows(table.getRows())
            .build();
    }

    private ExportResult submit(ExportSpec spec) {
        ExportFormatDescriptor descriptor = formatRegistry.getRequired(spec.getFormat());
        int limit = boundedLimit(spec.getLimit(), DEFAULT_EXPORT_LIMIT, MAX_SYNC_ROWS);
        ExportTable table = buildTable(spec, limit);
        FileWriteRequest request = withFileAttributes(
            descriptor.getWriter().write(spec, table),
            "EXPORT_RESULT",
            descriptor.getFormat()
        );
        FileRef file = fileService.save(request);
        CrudTask task = taskService.create(CrudTask.builder()
            .status(CrudTaskStatus.SUCCEEDED)
            .contextSnapshot(CrudTaskContextSnapshot.fromSpec(spec, spec.getOperationKey()))
            .resultFile(file)
            .progress(Integer.valueOf(100))
            .message("导出完成")
            .finishedAt(Instant.now())
            .build());
        return ExportResult.builder()
            .accepted(true)
            .async(false)
            .task(task)
            .file(file)
            .totalRows(table.getRows().size())
            .columns(table.getColumns())
            .build();
    }

    private ExportTable buildTable(ExportSpec spec, int limit) {
        EntityMeta meta = requireMeta(spec.getRootType());
        List<ExportColumn> columns = columnResolver.resolve(spec, meta, entityMetaRegistry.getRelationGraph(spec.getRootType()));
        ExportRenderOptions renderOptions = renderOptionsResolver.resolve(spec);
        List<Map<String, Object>> rawRows = readRows(spec, sourceFields(columns), limit);
        List<Map<String, Object>> displayRows = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> rawRow : rawRows) {
            displayRows.add(valueRenderer.renderRow(columns, rawRow, renderOptions));
        }
        return new ExportTable(columns, displayRows);
    }

    private List<String> sourceFields(List<ExportColumn> columns) {
        Set<String> fields = new LinkedHashSet<String>();
        for (ExportColumn column : columns) {
            if (column != null && column.getSourceField() != null && !column.getSourceField().trim().isEmpty()) {
                fields.add(column.getSourceField());
            }
        }
        return new ArrayList<String>(fields);
    }

    private List<Map<String, Object>> readRows(ExportSpec spec, List<String> fields, int limit) {
        QuerySpec<CrudRecord> querySpec = QuerySpec.<CrudRecord>builder()
            .op(QueryOperation.LIST)
            .scene(spec.getScene())
            .rootType(spec.getRootType())
            .entityClasses(spec.getEntityClasses())
            .subject(spec.getSubject())
            .attributes(spec.getAttributes())
            .grantedScope(spec.getGrantedScope())
            .governanceScope(spec.getGovernanceScope())
            .accessDecision(spec.getAccessDecision())
            .filters(spec.getFilters())
            .sorts(spec.getSorts())
            .time(spec.getTime())
            .page(normalizePage(spec.getPage(), limit))
            .limit(Integer.valueOf(limit))
            .selectFields(fields)
            .resultType(CrudRecord.class)
            .build();
        List<CrudRecord> records = queryEngine.list(querySpec);
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (records == null) {
            return rows;
        }
        for (Object record : records) {
            rows.add(toRow(record, fields));
        }
        return rows;
    }

    private EntityMeta requireMeta(Class<?> rootType) {
        if (rootType == null) {
            throw new ValidationException("导出 rootType 不能为空");
        }
        EntityMeta meta = entityMetaRegistry.getEntityMeta(rootType);
        if (meta == null) {
            throw new ValidationException("实体元数据不存在: " + rootType.getName());
        }
        return meta;
    }

    private static int boundedLimit(Integer requested, int defaultLimit, int maxLimit) {
        int limit = requested == null || requested.intValue() <= 0 ? defaultLimit : requested.intValue();
        if (limit > maxLimit) {
            throw new CrudException(CrudErrorCode.SYNC_LIMIT_EXCEEDED, "导出行数超过同步上限: " + maxLimit);
        }
        return limit;
    }

    private static PageRequest normalizePage(PageRequest source, int limit) {
        if (source == null) {
            return new PageRequest(1, limit);
        }
        Integer page = source.getPage() <= 0 ? Integer.valueOf(1) : Integer.valueOf(source.getPage());
        Integer pageLimit = source.getLimit() <= 0 ? Integer.valueOf(limit) : Integer.valueOf(source.getLimit());
        return new PageRequest(page, pageLimit);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toRow(Object source, List<String> fields) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        Map<String, Object> map = null;
        if (source instanceof CrudRecord) {
            map = ((CrudRecord) source).asMap();
        } else if (source instanceof Map<?, ?>) {
            map = (Map<String, Object>) source;
        }
        for (String field : fields) {
            values.put(field, map == null ? readProperty(source, field) : map.get(field));
        }
        return values;
    }

    private static Object readProperty(Object source, String field) {
        if (source == null || field == null) {
            return null;
        }
        String suffix = field.substring(0, 1).toUpperCase(Locale.ROOT) + field.substring(1);
        try {
            Method getter = source.getClass().getMethod("get" + suffix);
            return getter.invoke(source);
        } catch (Exception ignore) {
            try {
                Method getter = source.getClass().getMethod("is" + suffix);
                return getter.invoke(source);
            } catch (Exception ignored) {
                try {
                    Field declaredField = source.getClass().getDeclaredField(field);
                    declaredField.setAccessible(true);
                    return declaredField.get(source);
                } catch (Exception ignoredAgain) {
                    return null;
                }
            }
        }
    }

    private static FileWriteRequest withFileAttributes(FileWriteRequest request, String purpose, String format) {
        if (request == null) {
            throw new ValidationException("导出文件写入请求不能为空");
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

    private static String normalizeField(String field) {
        if (field == null || field.trim().isEmpty()) {
            throw new ValidationException("字段名不能为空");
        }
        return field.trim();
    }
}
