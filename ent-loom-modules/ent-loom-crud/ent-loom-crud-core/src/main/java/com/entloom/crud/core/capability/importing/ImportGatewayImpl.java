package com.entloom.crud.core.capability.importing;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.ImportOperation;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.execution.ExecutionPipeline;
import com.entloom.crud.core.foundation.taskfile.CrudTask;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.core.foundation.taskfile.TaskFileAccessGuard;
import com.entloom.crud.core.foundation.taskfile.TaskService;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.runtime.scene.SceneHandler;
import com.entloom.crud.core.runtime.scene.SceneHandlerRegistry;
import com.entloom.crud.core.runtime.spec.PayloadOnlySpecMutationGuard;
import com.entloom.crud.core.util.RouteKeyFactory;
import java.util.Objects;

/**
 * 默认导入网关实现。
 */
public class ImportGatewayImpl implements ImportGateway {
    private final ImportFormatRegistry formatRegistry;
    private final ImportPayloadCustomizerRegistry payloadCustomizerRegistry;
    private final ImportEngine importEngine;
    private final SceneHandlerRegistry<ImportSpec, ImportResult> sceneHandlerRegistry;
    private final ExecutionPipeline executionPipeline;
    private final TaskService taskService;
    private final TaskFileAccessGuard accessGuard;

    public ImportGatewayImpl(
        ImportFormatRegistry formatRegistry,
        ImportPayloadCustomizerRegistry payloadCustomizerRegistry,
        ImportEngine importEngine,
        SceneHandlerRegistry<ImportSpec, ImportResult> sceneHandlerRegistry,
        ExecutionPipeline executionPipeline,
        TaskService taskService,
        TaskFileAccessGuard accessGuard
    ) {
        this.formatRegistry = Objects.requireNonNull(formatRegistry, "formatRegistry 不能为空");
        this.payloadCustomizerRegistry = Objects.requireNonNull(payloadCustomizerRegistry, "payloadCustomizerRegistry 不能为空");
        this.importEngine = importEngine == null ? new UnsupportedImportEngine() : importEngine;
        this.sceneHandlerRegistry = sceneHandlerRegistry;
        this.executionPipeline = Objects.requireNonNull(executionPipeline, "executionPipeline 不能为空");
        this.taskService = Objects.requireNonNull(taskService, "taskService 不能为空");
        this.accessGuard = Objects.requireNonNull(accessGuard, "accessGuard 不能为空");
    }

    @Override
    public ImportResult validate(ImportSpec spec) {
        return execute(spec, ImportOperation.VALIDATE, true);
    }

    @Override
    public ImportResult submit(ImportSpec spec) {
        return execute(spec, ImportOperation.SUBMIT, true);
    }

    @Override
    public ImportResult commit(ImportSpec spec) {
        return execute(spec, ImportOperation.COMMIT, false);
    }

    @Override
    public CrudTask status(ImportSpec spec) {
        return executeTask(spec, ImportOperation.STATUS);
    }

    @Override
    public CrudTask cancel(ImportSpec spec) {
        return executeTask(spec, ImportOperation.CANCEL);
    }

    @Override
    public FileRef downloadError(ImportSpec spec) {
        return executeFile(spec, ImportOperation.DOWNLOAD_ERROR);
    }

    private ImportResult execute(ImportSpec spec, ImportOperation operation, boolean requireFormat) {
        return executionPipeline.execute(
            () -> prepare(spec, operation),
            current -> executionPipeline.governImport(current),
            (requestSpec, governedSpec, governance) -> dispatch(prepareGoverned(governedSpec, requireFormat))
        );
    }

    private CrudTask executeTask(ImportSpec spec, ImportOperation operation) {
        return executionPipeline.execute(
            () -> prepare(spec, operation),
            current -> executionPipeline.governImport(current),
            (requestSpec, governedSpec, governance) -> {
                CrudTask task;
                if (operation == ImportOperation.CANCEL) {
                    task = taskService.getRequired(requireTaskId(governedSpec));
                    accessGuard.assertTaskAccessible(task, governedSpec);
                    return taskService.cancel(task.getTaskId(), "导入任务取消");
                }
                task = taskService.getRequired(requireTaskId(governedSpec));
                accessGuard.assertTaskAccessible(task, governedSpec);
                return task;
            }
        );
    }

    private FileRef executeFile(ImportSpec spec, ImportOperation operation) {
        return executionPipeline.execute(
            () -> prepare(spec, operation),
            current -> executionPipeline.governImport(current),
            (requestSpec, governedSpec, governance) -> {
                CrudTask task = taskService.getRequired(requireTaskId(governedSpec));
                accessGuard.assertTaskAccessible(task, governedSpec);
                return resolveErrorFile(task);
            }
        );
    }

    private FileRef resolveErrorFile(CrudTask task) {
        if (task.getErrorFile() == null) {
            throw new CrudException(CrudErrorCode.DOWNLOAD_NOT_READY, "导入错误文件尚未就绪: " + task.getTaskId());
        }
        accessGuard.assertDownloadableFile(task.getErrorFile(), "IMPORT_ERROR");
        return task.getErrorFile();
    }

    private String requireTaskId(ImportSpec spec) {
        if (spec == null || spec.getTaskId() == null || spec.getTaskId().trim().isEmpty()) {
            throw new ValidationException("导入任务 ID 不能为空");
        }
        return spec.getTaskId().trim();
    }

    private ImportSpec prepare(ImportSpec spec, ImportOperation operation) {
        if (spec == null) {
            throw new ValidationException("导入请求规范(spec)不能为空");
        }
        return spec.toBuilder().operation(operation).build();
    }

    private ImportSpec prepareGoverned(ImportSpec spec, boolean requireFormat) {
        ImportSpec prepared = applyPayloadCustomizers(spec);
        if (requireFormat) {
            formatRegistry.getRequired(prepared.getFormat());
        }
        return prepared;
    }

    private ImportSpec applyPayloadCustomizers(ImportSpec spec) {
        return PayloadOnlySpecMutationGuard.mergeImportPayload(spec, payloadCustomizerRegistry.customize(spec));
    }

    private ImportResult dispatch(ImportSpec spec) {
        CrudRouteKey routeKey = RouteKeyFactory.buildImportRoute(spec);
        SceneHandler<ImportSpec, ImportResult> handler = sceneHandlerRegistry == null ? null : sceneHandlerRegistry.resolveOrNull(routeKey);
        if (handler != null) {
            return handler.handle(spec, current -> importEngine.execute(current));
        }
        if (!RouteKeyFactory.normalizeScene(spec.getScene()).isEmpty()) {
            throw new CrudException(CrudErrorCode.SCENE_NOT_FOUND, "导入场景未命中: " + routeKey);
        }
        return importEngine.execute(spec);
    }

}
