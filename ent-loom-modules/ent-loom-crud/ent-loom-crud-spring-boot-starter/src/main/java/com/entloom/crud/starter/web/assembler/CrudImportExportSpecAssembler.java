package com.entloom.crud.starter.web.assembler;

import com.entloom.crud.api.model.CrudExportHttpRequest;
import com.entloom.crud.api.model.CrudExportRenderOptions;
import com.entloom.crud.api.model.CrudImportHttpRequest;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.capability.exporting.ExportRenderOptions;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.importing.ImportMode;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.starter.web.support.CrudRequestSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Import / Export HTTP 请求组装器。
 */
public class CrudImportExportSpecAssembler {
    private final CrudRequestSupport requestSupport;

    public CrudImportExportSpecAssembler(CrudRequestSupport requestSupport) {
        this.requestSupport = requestSupport;
    }

    public ExportSpec assembleExport(
        String routeEntity,
        String scene,
        CrudExportHttpRequest request,
        SubjectContext subject
    ) {
        CrudExportHttpRequest actual = request == null ? new CrudExportHttpRequest() : request;
        actual.setEntityCodes(requestSupport.normalizeEntityCodes(routeEntity, actual.getEntityCodes()));
        List<Class<?>> entityClasses = requestSupport.resolveEntityClasses(actual.getEntityCodes(), routeEntity);
        return ExportSpec.builder()
            .scene(requestSupport.normalizeScene(scene))
            .rootType(entityClasses.get(0))
            .entityClasses(entityClasses)
            .subject(subject)
            .format(actual.getFormat())
            .fileName(actual.getFileName())
            .taskId(actual.getTaskId())
            .async(actual.isAsync())
            .fields(actual.getFields())
            .filters(actual.getFilters())
            .sorts(actual.getSorts())
            .time(actual.getTime())
            .renderOptions(renderOptions(actual.getRenderOptions()))
            .page(actual.getPage())
            .limit(actual.getLimit())
            .payload(sanitizedAttributes(actual.getAttributes(), actual.getExtraFields()))
            .attributes(sanitizedAttributes(actual.getAttributes(), actual.getExtraFields()))
            .includeExecutionMeta(actual.isIncludeExecutionMeta())
            .build();
    }

    public ImportSpec assembleImport(
        String routeEntity,
        String scene,
        CrudImportHttpRequest request,
        SubjectContext subject
    ) {
        CrudImportHttpRequest actual = request == null ? new CrudImportHttpRequest() : request;
        actual.setEntityCodes(requestSupport.normalizeEntityCodes(routeEntity, actual.getEntityCodes()));
        List<Class<?>> entityClasses = requestSupport.resolveEntityClasses(actual.getEntityCodes(), routeEntity);
        return ImportSpec.builder()
            .scene(requestSupport.normalizeScene(scene))
            .rootType(entityClasses.get(0))
            .entityClasses(entityClasses)
            .subject(subject)
            .format(actual.getFormat())
            .mode(resolveMode(actual.getMode()))
            .sourceFile(sourceFile(actual.getSourceFileId()))
            .taskId(actual.getTaskId())
            .batchSize(actual.getBatchSize())
            .async(actual.isAsync())
            .payload(sanitizedAttributes(actual.getAttributes(), actual.getExtraFields()))
            .attributes(sanitizedAttributes(actual.getAttributes(), actual.getExtraFields()))
            .includeExecutionMeta(actual.isIncludeExecutionMeta())
            .build();
    }

    public String resolveRequestId(CrudExportHttpRequest request) {
        return requestSupport.resolveRequestId(request == null ? null : request.getRequestId());
    }

    public String resolveRequestId(CrudImportHttpRequest request) {
        return requestSupport.resolveRequestId(request == null ? null : request.getRequestId());
    }

    private static FileRef sourceFile(String sourceFileId) {
        if (sourceFileId == null || sourceFileId.trim().isEmpty()) {
            return null;
        }
        return FileRef.builder().fileId(sourceFileId.trim()).build();
    }

    private static ImportMode resolveMode(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return ImportMode.UPSERT;
        }
        try {
            return ImportMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("导入 mode 不支持: " + raw + "，仅支持 VALIDATE_ONLY、INSERT、UPDATE、UPSERT");
        }
    }

    private static ExportRenderOptions renderOptions(CrudExportRenderOptions source) {
        return source == null ? null : new ExportRenderOptions(source.getTimezone());
    }

    private static Map<String, Object> sanitizedAttributes(
        Map<String, Object> attributes,
        Map<String, Object> extraFields
    ) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        copyAllowed(result, attributes);
        copyAllowed(result, extraFields);
        return result;
    }

    private static void copyAllowed(Map<String, Object> target, Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() != null && !isReserved(entry.getKey())) {
                target.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private static boolean isReserved(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return "subject".equals(normalized)
            || "tenant".equals(normalized)
            || "tenantid".equals(normalized)
            || "org".equals(normalized)
            || "orgid".equals(normalized)
            || "operation".equals(normalized)
            || "op".equals(normalized)
            || "datascope".equals(normalized)
            || "grantedscope".equals(normalized)
            || "governancescope".equals(normalized)
            || "accessdecision".equals(normalized);
    }
}
