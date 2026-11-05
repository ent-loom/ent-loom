package com.entloom.crud.starter.web.facade;

import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.ImportOperation;
import com.entloom.crud.api.model.CrudImportData;
import com.entloom.crud.api.model.CrudImportHttpRequest;
import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.api.model.CrudTaskData;
import com.entloom.crud.core.capability.importing.ImportGateway;
import com.entloom.crud.core.capability.importing.ImportSpec;
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
 * 导入门面。
 */
@RequiredArgsConstructor
public class EntCrudImportFacade {
    private final ImportGateway importGateway;
    private final FileService fileService;
    private final CrudSubjectResolver subjectResolver;
    private final CrudImportExportSpecAssembler specAssembler;
    private final CrudImportExportResponseAssembler responseAssembler;

    public CrudResponse<CrudImportData> validate(String entity, String scene, CrudImportHttpRequest request, CrudInvocationContext context) {
        CrudImportHttpRequest actual = request == null ? new CrudImportHttpRequest() : request;
        CrudOperationKey operation = CrudOperationKey.of(ImportOperation.VALIDATE);
        CrudHttpRequestContext.bindOperation(operation);
        return CrudHttpRequestContext.completeOperation(operation, withContext(context, () -> {
            ImportSpec spec = specAssembler.assembleImport(entity, scene, actual, subjectResolver.resolveOrThrow());
            return CrudResponse.success(responseAssembler.importData(importGateway.validate(spec)));
        }));
    }

    public CrudResponse<CrudImportData> submit(String entity, String scene, CrudImportHttpRequest request, CrudInvocationContext context) {
        CrudImportHttpRequest actual = request == null ? new CrudImportHttpRequest() : request;
        CrudOperationKey operation = CrudOperationKey.of(ImportOperation.SUBMIT);
        CrudHttpRequestContext.bindOperation(operation);
        return CrudHttpRequestContext.completeOperation(operation, withContext(context, () -> {
            ImportSpec spec = specAssembler.assembleImport(entity, scene, actual, subjectResolver.resolveOrThrow());
            return CrudResponse.success(responseAssembler.importData(importGateway.submit(spec)));
        }));
    }

    public CrudResponse<CrudTaskData> status(String entity, String scene, String taskId, CrudImportHttpRequest request, CrudInvocationContext context) {
        CrudImportHttpRequest actual = request == null ? new CrudImportHttpRequest() : request;
        bindTaskId(actual, taskId);
        CrudOperationKey operation = CrudOperationKey.of(ImportOperation.STATUS);
        CrudHttpRequestContext.bindOperation(operation);
        return CrudHttpRequestContext.completeOperation(operation, withContext(context, () -> {
            ImportSpec spec = specAssembler.assembleImport(entity, scene, actual, subjectResolver.resolveOrThrow());
            return CrudResponse.success(responseAssembler.taskData(importGateway.status(spec)));
        }));
    }

    public FileDownload downloadError(String entity, String scene, String taskId, CrudImportHttpRequest request, CrudInvocationContext context) {
        CrudImportHttpRequest actual = request == null ? new CrudImportHttpRequest() : request;
        bindTaskId(actual, taskId);
        CrudOperationKey operation = CrudOperationKey.of(ImportOperation.DOWNLOAD_ERROR);
        CrudHttpRequestContext.bindOperation(operation);
        FileDownload result = withContext(context, () -> {
            ImportSpec spec = specAssembler.assembleImport(entity, scene, actual, subjectResolver.resolveOrThrow());
            FileRef file = importGateway.downloadError(spec);
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

    private void bindTaskId(CrudImportHttpRequest request, String pathTaskId) {
        if (pathTaskId != null && !pathTaskId.trim().isEmpty()) {
            request.setTaskId(pathTaskId);
        }
    }
}
