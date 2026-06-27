package com.entloom.crud.starter.web.facade;

import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.ExportOperation;
import com.entloom.crud.api.model.CrudExportData;
import com.entloom.crud.api.model.CrudExportHttpRequest;
import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.api.model.CrudTaskData;
import com.entloom.crud.core.capability.exporting.ExportGateway;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.core.foundation.taskfile.FileService;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.crud.core.runtime.context.CrudInvocationContext;
import com.entloom.crud.core.runtime.context.CrudRequestContextHolder;
import com.entloom.crud.starter.web.assembler.CrudImportExportResponseAssembler;
import com.entloom.crud.starter.web.assembler.CrudImportExportSpecAssembler;
import com.entloom.crud.starter.web.support.CrudResponseBuilder;
import lombok.RequiredArgsConstructor;

/**
 * 导出门面。
 */
@RequiredArgsConstructor
public class EntCrudExportFacade {
    private final ExportGateway exportGateway;
    private final FileService fileService;
    private final CrudSubjectResolver subjectResolver;
    private final CrudImportExportSpecAssembler specAssembler;
    private final CrudImportExportResponseAssembler responseAssembler;
    private final CrudResponseBuilder responseBuilder;

    public CrudResponse<CrudExportData> preview(String entity, String scene, CrudExportHttpRequest request, CrudInvocationContext context) {
        CrudExportHttpRequest actual = request == null ? new CrudExportHttpRequest() : request;
        responseBuilder.bind(specAssembler.resolveRequestId(actual), CrudOperationKey.of(ExportOperation.PREVIEW));
        return withContext(context, () -> {
            ExportSpec spec = specAssembler.assembleExport(entity, scene, actual, subjectResolver.resolveOrThrow());
            return responseBuilder.success(responseAssembler.exportData(exportGateway.preview(spec)));
        });
    }

    public CrudResponse<CrudExportData> submit(String entity, String scene, CrudExportHttpRequest request, CrudInvocationContext context) {
        CrudExportHttpRequest actual = request == null ? new CrudExportHttpRequest() : request;
        responseBuilder.bind(specAssembler.resolveRequestId(actual), CrudOperationKey.of(ExportOperation.SUBMIT));
        return withContext(context, () -> {
            ExportSpec spec = specAssembler.assembleExport(entity, scene, actual, subjectResolver.resolveOrThrow());
            return responseBuilder.success(responseAssembler.exportData(exportGateway.submit(spec)));
        });
    }

    public CrudResponse<CrudTaskData> status(String entity, String scene, String taskId, CrudExportHttpRequest request, CrudInvocationContext context) {
        CrudExportHttpRequest actual = request == null ? new CrudExportHttpRequest() : request;
        bindTaskId(actual, taskId);
        responseBuilder.bind(specAssembler.resolveRequestId(actual), CrudOperationKey.of(ExportOperation.STATUS));
        return withContext(context, () -> {
            ExportSpec spec = specAssembler.assembleExport(entity, scene, actual, subjectResolver.resolveOrThrow());
            return responseBuilder.success(responseAssembler.taskData(exportGateway.status(spec)));
        });
    }

    public FileDownload download(String entity, String scene, String taskId, CrudExportHttpRequest request, CrudInvocationContext context) {
        CrudExportHttpRequest actual = request == null ? new CrudExportHttpRequest() : request;
        bindTaskId(actual, taskId);
        responseBuilder.bind(specAssembler.resolveRequestId(actual), CrudOperationKey.of(ExportOperation.DOWNLOAD));
        try {
            return withContext(context, () -> {
                ExportSpec spec = specAssembler.assembleExport(entity, scene, actual, subjectResolver.resolveOrThrow());
                FileRef file = exportGateway.download(spec);
                return new FileDownload(file, fileService.read(file));
            });
        } finally {
            responseBuilder.clear();
        }
    }

    private <T> T withContext(CrudInvocationContext context, java.util.function.Supplier<T> supplier) {
        CrudInvocationContext actualContext = context == null ? CrudInvocationContext.empty() : context;
        return CrudRequestContextHolder.withAttributes(actualContext.getAttributes(), supplier);
    }

    private void bindTaskId(CrudExportHttpRequest request, String pathTaskId) {
        if (pathTaskId != null && !pathTaskId.trim().isEmpty()) {
            request.setTaskId(pathTaskId);
        }
    }
}
