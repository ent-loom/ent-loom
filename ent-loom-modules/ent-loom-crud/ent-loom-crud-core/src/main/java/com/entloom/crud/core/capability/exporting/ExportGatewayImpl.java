package com.entloom.crud.core.capability.exporting;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.ExportOperation;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.execution.ExecutionPipeline;
import com.entloom.crud.core.foundation.taskfile.CrudTask;
import com.entloom.crud.core.foundation.taskfile.CrudTaskStatus;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.core.foundation.taskfile.TaskFileAccessGuard;
import com.entloom.crud.core.foundation.taskfile.TaskService;
import com.entloom.crud.core.idempotency.CrudIdempotencyFingerprint;
import com.entloom.crud.core.idempotency.IdempotencyManager;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.runtime.scene.SceneHandler;
import com.entloom.crud.core.runtime.scene.SceneHandlerRegistry;
import com.entloom.crud.core.runtime.spec.PayloadOnlySpecMutationGuard;
import com.entloom.crud.core.util.RouteKeyFactory;
import java.util.Objects;

/**
 * 默认导出网关实现。
 */
public class ExportGatewayImpl implements ExportGateway {
    private final ExportFormatRegistry formatRegistry;
    private final ExportPayloadCustomizerRegistry payloadCustomizerRegistry;
    private final ExportEngine exportEngine;
    private final SceneHandlerRegistry<ExportSpec, ExportResult> sceneHandlerRegistry;
    private final ExecutionPipeline executionPipeline;
    private final TaskService taskService;
    private final TaskFileAccessGuard accessGuard;
    private final IdempotencyManager idempotencyManager;

    public ExportGatewayImpl(
        ExportFormatRegistry formatRegistry,
        ExportPayloadCustomizerRegistry payloadCustomizerRegistry,
        ExportEngine exportEngine,
        SceneHandlerRegistry<ExportSpec, ExportResult> sceneHandlerRegistry,
        ExecutionPipeline executionPipeline,
        TaskService taskService,
        TaskFileAccessGuard accessGuard
    ) {
        this(
            formatRegistry,
            payloadCustomizerRegistry,
            exportEngine,
            sceneHandlerRegistry,
            executionPipeline,
            taskService,
            accessGuard,
            null
        );
    }

    public ExportGatewayImpl(
        ExportFormatRegistry formatRegistry,
        ExportPayloadCustomizerRegistry payloadCustomizerRegistry,
        ExportEngine exportEngine,
        SceneHandlerRegistry<ExportSpec, ExportResult> sceneHandlerRegistry,
        ExecutionPipeline executionPipeline,
        TaskService taskService,
        TaskFileAccessGuard accessGuard,
        IdempotencyManager idempotencyManager
    ) {
        this.formatRegistry = Objects.requireNonNull(formatRegistry, "formatRegistry 不能为空");
        this.payloadCustomizerRegistry = Objects.requireNonNull(payloadCustomizerRegistry, "payloadCustomizerRegistry 不能为空");
        this.exportEngine = exportEngine == null ? new UnsupportedExportEngine() : exportEngine;
        this.sceneHandlerRegistry = sceneHandlerRegistry;
        this.executionPipeline = Objects.requireNonNull(executionPipeline, "executionPipeline 不能为空");
        this.taskService = Objects.requireNonNull(taskService, "taskService 不能为空");
        this.accessGuard = Objects.requireNonNull(accessGuard, "accessGuard 不能为空");
        this.idempotencyManager = idempotencyManager;
    }

    @Override
    public ExportResult submit(ExportSpec spec) {
        return execute(spec, ExportOperation.SUBMIT, true);
    }

    @Override
    public ExportResult preview(ExportSpec spec) {
        return execute(spec, ExportOperation.PREVIEW, true);
    }

    @Override
    public FileRef download(ExportSpec spec) {
        return executeFile(spec, ExportOperation.DOWNLOAD);
    }

    @Override
    public CrudTask status(ExportSpec spec) {
        return executeTask(spec, ExportOperation.STATUS);
    }

    @Override
    public CrudTask cancel(ExportSpec spec) {
        return executeTask(spec, ExportOperation.CANCEL);
    }

    private ExportResult execute(ExportSpec spec, ExportOperation operation, boolean requireFormat) {
        return executionPipeline.execute(
            () -> prepare(spec, operation),
            current -> executionPipeline.governExport(current),
            (requestSpec, governedSpec, governance) -> {
                ExportSpec prepared = prepareGoverned(governedSpec, requireFormat);
                if (operation != ExportOperation.SUBMIT || idempotencyManager == null
                    || isBlank(prepared.getIdempotencyKey())) {
                    return dispatch(prepared);
                }
                String storageKey = idempotencyManager.buildStorageKey(
                    governance.getSubject().getTenantId(),
                    RouteKeyFactory.buildExportRouteKey(prepared),
                    prepared.getScene(),
                    prepared.getIdempotencyKey().trim()
                );
                return idempotencyManager.executeWithIdempotency(
                    storageKey,
                    CrudIdempotencyFingerprint.forExport(prepared),
                    () -> dispatch(prepared)
                );
            }
        );
    }

    private CrudTask executeTask(ExportSpec spec, ExportOperation operation) {
        return executionPipeline.execute(
            () -> prepare(spec, operation),
            current -> executionPipeline.governExport(current),
            (requestSpec, governedSpec, governance) -> {
                CrudTask task;
                if (operation == ExportOperation.CANCEL) {
                    task = taskService.getRequired(requireTaskId(governedSpec));
                    accessGuard.assertTaskAccessible(task, governedSpec);
                    return taskService.cancel(task.getTaskId(), "导出任务取消");
                }
                task = taskService.getRequired(requireTaskId(governedSpec));
                accessGuard.assertTaskAccessible(task, governedSpec);
                return task;
            }
        );
    }

    private FileRef executeFile(ExportSpec spec, ExportOperation operation) {
        return executionPipeline.execute(
            () -> prepare(spec, operation),
            current -> executionPipeline.governExport(current),
            (requestSpec, governedSpec, governance) -> {
                CrudTask task = taskService.getRequired(requireTaskId(governedSpec));
                return resolveDownloadFile(task, governedSpec);
            }
        );
    }

    private FileRef resolveDownloadFile(CrudTask task, ExportSpec spec) {
        if (task.getStatus() != CrudTaskStatus.SUCCEEDED || task.getResultFile() == null) {
            throw new CrudException(CrudErrorCode.DOWNLOAD_NOT_READY, "导出文件尚未就绪: " + task.getTaskId());
        }
        accessGuard.assertDownloadableFile(task, spec, task.getResultFile(), "EXPORT_RESULT");
        return task.getResultFile();
    }

    private String requireTaskId(ExportSpec spec) {
        if (spec == null || spec.getTaskId() == null || spec.getTaskId().trim().isEmpty()) {
            throw new ValidationException("导出任务 ID 不能为空");
        }
        return spec.getTaskId().trim();
    }

    private ExportSpec prepare(ExportSpec spec, ExportOperation operation) {
        if (spec == null) {
            throw new ValidationException("导出请求规范(spec)不能为空");
        }
        return spec.toBuilder().operation(operation).build();
    }

    private ExportSpec prepareGoverned(ExportSpec spec, boolean requireFormat) {
        ExportSpec prepared = applyPayloadCustomizers(spec);
        if (requireFormat) {
            formatRegistry.getRequired(prepared.getFormat());
        }
        return prepared;
    }

    private ExportSpec applyPayloadCustomizers(ExportSpec spec) {
        return PayloadOnlySpecMutationGuard.mergeExportPayload(spec, payloadCustomizerRegistry.customize(spec));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private ExportResult dispatch(ExportSpec spec) {
        CrudRouteKey routeKey = RouteKeyFactory.buildExportRoute(spec);
        SceneHandler<ExportSpec, ExportResult> handler = sceneHandlerRegistry == null ? null : sceneHandlerRegistry.resolveOrNull(routeKey);
        if (handler != null) {
            return handler.handle(spec, current -> exportEngine.execute(current));
        }
        if (!RouteKeyFactory.normalizeScene(spec.getScene()).isEmpty()) {
            throw new CrudException(CrudErrorCode.SCENE_NOT_FOUND, "导出场景未命中: " + routeKey);
        }
        return exportEngine.execute(spec);
    }

}
