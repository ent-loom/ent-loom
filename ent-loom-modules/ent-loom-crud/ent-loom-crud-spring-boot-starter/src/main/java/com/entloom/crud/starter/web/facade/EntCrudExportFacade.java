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
import com.entloom.crud.starter.web.support.CrudHttpRequestContext;
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

    public CrudResponse<CrudExportData> preview(String entity, String scene, CrudExportHttpRequest request, CrudInvocationContext context) {
        CrudExportHttpRequest actual = request == null ? new CrudExportHttpRequest() : request;
        CrudOperationKey operation = CrudOperationKey.of(ExportOperation.PREVIEW);
        CrudHttpRequestContext.bindOperation(operation);
        return CrudHttpRequestContext.completeOperation(operation, withContext(context, () -> {
            ExportSpec spec = specAssembler.assembleExport(entity, scene, actual, subjectResolver.resolveOrThrow());
            return CrudResponse.success(responseAssembler.exportData(exportGateway.preview(spec)));
        }));
    }

    public CrudResponse<CrudExportData> submit(String entity, String scene, CrudExportHttpRequest request, CrudInvocationContext context) {
        CrudExportHttpRequest actual = request == null ? new CrudExportHttpRequest() : request;
        CrudOperationKey operation = CrudOperationKey.of(ExportOperation.SUBMIT);
        CrudHttpRequestContext.bindOperation(operation);
        return CrudHttpRequestContext.completeOperation(operation, withContext(context, () -> {
            ExportSpec spec = specAssembler.assembleExport(entity, scene, actual, subjectResolver.resolveOrThrow());
            return CrudResponse.success(responseAssembler.exportData(exportGateway.submit(spec)));
        }));
    }

    public CrudResponse<CrudTaskData> status(String entity, String scene, String taskId, CrudExportHttpRequest request, CrudInvocationContext context) {
        CrudExportHttpRequest actual = request == null ? new CrudExportHttpRequest() : request;
        bindTaskId(actual, taskId);
        CrudOperationKey operation = CrudOperationKey.of(ExportOperation.STATUS);
        CrudHttpRequestContext.bindOperation(operation);
        return CrudHttpRequestContext.completeOperation(operation, withContext(context, () -> {
            ExportSpec spec = specAssembler.assembleExport(entity, scene, actual, subjectResolver.resolveOrThrow());
            return CrudResponse.success(responseAssembler.taskData(exportGateway.status(spec)));
        }));
    }

    public FileDownload download(String entity, String scene, String taskId, CrudExportHttpRequest request, CrudInvocationContext context) {
        CrudExportHttpRequest actual = request == null ? new CrudExportHttpRequest() : request;
        bindTaskId(actual, taskId);
        CrudOperationKey operation = CrudOperationKey.of(ExportOperation.DOWNLOAD);
        CrudHttpRequestContext.bindOperation(operation);
        FileDownload result = withContext(context, () -> {
            ExportSpec spec = specAssembler.assembleExport(entity, scene, actual, subjectResolver.resolveOrThrow());
            FileRef file = exportGateway.download(spec);
            return new FileDownload(file, () -> fileService.openStream(file));
        });
        CrudHttpRequestContext.completeOperation(operation);
        return result;
    }

    private <T> T withContext(CrudInvocationContext context, java.util.function.Supplier<T> supplier) {
        CrudInvocationContext actualContext = context == null ? CrudInvocationContext.empty() : context;
        return CrudHttpRequestContext.trackOperationFailure(
            () -> CrudRequestContextHolder.withAttributes(actualContext.getAttributes(), supplier));
    }

    private void bindTaskId(CrudExportHttpRequest request, String pathTaskId) {
        if (pathTaskId != null && !pathTaskId.trim().isEmpty()) {
            request.setTaskId(pathTaskId);
        }
    }
}
