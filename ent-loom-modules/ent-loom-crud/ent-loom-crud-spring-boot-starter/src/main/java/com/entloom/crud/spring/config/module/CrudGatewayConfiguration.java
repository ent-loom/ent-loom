package com.entloom.crud.spring.config.module;

import com.entloom.crud.core.capability.command.engine.CommandEngine;
import com.entloom.crud.core.capability.exporting.DefaultExportColumnResolver;
import com.entloom.crud.core.capability.exporting.DefaultExportFormatRegistry;
import com.entloom.crud.core.capability.exporting.DefaultExportEngine;
import com.entloom.crud.core.capability.exporting.DefaultExportValueRenderer;
import com.entloom.crud.core.capability.exporting.ExportDictionaryResolver;
import com.entloom.crud.core.capability.exporting.ExportEngine;
import com.entloom.crud.core.capability.exporting.ExportFormatDescriptor;
import com.entloom.crud.core.capability.exporting.ExportFormatRegistry;
import com.entloom.crud.core.capability.exporting.ExportGateway;
import com.entloom.crud.core.capability.exporting.ExportGatewayImpl;
import com.entloom.crud.core.capability.exporting.ExportPayloadCustomizer;
import com.entloom.crud.core.capability.exporting.ExportPayloadCustomizerRegistry;
import com.entloom.crud.core.capability.exporting.ExportRenderOptionsResolver;
import com.entloom.crud.core.capability.exporting.ExportResult;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.importing.DefaultImportFormatRegistry;
import com.entloom.crud.core.capability.importing.DefaultImportEngine;
import com.entloom.crud.core.capability.importing.ImportEngine;
import com.entloom.crud.core.capability.importing.ImportFormatDescriptor;
import com.entloom.crud.core.capability.importing.ImportFormatRegistry;
import com.entloom.crud.core.capability.importing.ImportGateway;
import com.entloom.crud.core.capability.importing.ImportGatewayImpl;
import com.entloom.crud.core.capability.importing.ImportPayloadCustomizer;
import com.entloom.crud.core.capability.importing.ImportPayloadCustomizerRegistry;
import com.entloom.crud.core.capability.importing.ImportResult;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.capability.query.engine.QueryEngine;
import com.entloom.crud.core.execution.ExecutionPipeline;
import com.entloom.crud.core.foundation.taskfile.FileService;
import com.entloom.crud.core.foundation.taskfile.LocalFileService;
import com.entloom.crud.core.foundation.taskfile.LocalTaskService;
import com.entloom.crud.core.foundation.taskfile.TaskFileAccessGuard;
import com.entloom.crud.core.foundation.taskfile.TaskService;
import com.entloom.crud.spring.config.CrudProperties;
import com.entloom.crud.core.capability.command.gateway.CommandGateway;
import com.entloom.crud.core.capability.command.gateway.CommandGatewayImpl;
import com.entloom.crud.core.capability.query.gateway.QueryGateway;
import com.entloom.crud.core.capability.query.gateway.QueryGatewayImpl;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.permission.CrudPermissionService;
import com.entloom.crud.core.governance.scope.CrudDataScopeContributor;
import com.entloom.crud.core.governance.scope.CrudDataScopeResolver;
import com.entloom.crud.core.governance.service.CrudGovernanceService;
import com.entloom.crud.core.governance.service.DefaultCrudGovernanceService;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.crud.core.idempotency.IdempotencyManager;
import com.entloom.crud.core.idempotency.IdempotencyPolicy;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.router.CommandActionSceneResolver;
import com.entloom.crud.core.runtime.router.CommandRouter;
import com.entloom.crud.core.runtime.router.DefaultCommandRouter;
import com.entloom.crud.core.runtime.router.DefaultQueryRouter;
import com.entloom.crud.core.runtime.router.QueryRouter;
import com.entloom.crud.core.runtime.scene.SceneHandlerRegistry;
import com.entloom.crud.core.runtime.spec.CrudSpecAttributeResolver;
import com.entloom.crud.core.runtime.validation.SpecValidator;
import com.entloom.crud.core.capability.stats.StatsResult;
import com.entloom.crud.core.capability.stats.StatsPayloadCustomizerRegistry;
import com.entloom.crud.core.capability.stats.StatsSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.time.Duration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.entloom.crud.core.runtime.scene.DefaultSceneHandlerRegistry;

/**
 * 路由与网关装配，对外暴露统一 Command/Query 调用入口。
 */
@Configuration
public class CrudGatewayConfiguration {
    private static final String IMPORT_EXPORT_ENABLED_EXPRESSION =
        "${entloom.crud.import.enabled:true} or ${entloom.crud.export.enabled:true}";

    /**
     * Query 路由器，默认将请求路由到默认 QueryEngine。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "entloom.crud.query.enabled", havingValue = "true", matchIfMissing = true)
    public QueryRouter queryRouter(QueryEngine defaultQueryEngine) {
        return new DefaultQueryRouter(defaultQueryEngine);
    }

    /**
     * Command 路由器，默认将请求路由到默认 CommandEngine。
     */
    @Bean
    @ConditionalOnMissingBean(CommandRouter.class)
    @ConditionalOnProperty(name = "entloom.crud.command.enabled", havingValue = "true", matchIfMissing = true)
    public DefaultCommandRouter commandRouter(CommandEngine defaultCommandEngine) {
        return new DefaultCommandRouter(defaultCommandEngine);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "entloom.crud.command.enabled", havingValue = "true", matchIfMissing = true)
    public CommandActionSceneResolver commandActionSceneResolver(CommandRouter commandRouter) {
        if (!(commandRouter instanceof CommandActionSceneResolver)) {
            throw new ValidationException("CommandRouter 不是 CommandActionSceneResolver: " + commandRouter.getClass().getName());
        }
        return (CommandActionSceneResolver) commandRouter;
    }

    @Bean
    @ConditionalOnMissingBean(name = "sceneHandlerRegistrar")
    public InitializingBean sceneHandlerRegistrar(
        ApplicationContext applicationContext,
        ObjectProvider<QueryRouter> queryRouterProvider,
        ObjectProvider<CommandRouter> commandRouterProvider,
        ObjectProvider<SceneHandlerRegistry<StatsSpec, StatsResult>> statsSceneHandlerRegistryProvider,
        ObjectProvider<SceneHandlerRegistry<ImportSpec, ImportResult>> importSceneHandlerRegistryProvider,
        ObjectProvider<SceneHandlerRegistry<ExportSpec, ExportResult>> exportSceneHandlerRegistryProvider,
        ObjectProvider<StatsPayloadCustomizerRegistry> statsPayloadCustomizerRegistryProvider
    ) {
        return SceneHandlerRegistrar.create(
            applicationContext,
            queryRouterProvider.getIfAvailable(),
            commandRouterProvider.getIfAvailable(),
            statsSceneHandlerRegistryProvider.getIfAvailable(),
            importSceneHandlerRegistryProvider.getIfAvailable(),
            exportSceneHandlerRegistryProvider.getIfAvailable(),
            statsPayloadCustomizerRegistryProvider.getIfAvailable()
        );
    }

    /**
     * 治理主链服务。
     */
    @Bean
    @ConditionalOnMissingBean
    public CrudGovernanceService crudGovernanceService(
        EntityMetaRegistry entityMetaRegistry,
        SpecValidator specValidator,
        CrudSubjectResolver crudSubjectResolver,
        CrudPermissionService crudPermissionService,
        CrudDataScopeResolver crudDataScopeResolver,
        ObjectProvider<CrudDataScopeContributor[]> crudDataScopeContributors,
        CrudGovernanceAuditRecorder crudGovernanceAuditRecorder,
        CrudSpecAttributeResolver crudSpecAttributeResolver
    ) {
        return new DefaultCrudGovernanceService(
            entityMetaRegistry,
            specValidator,
            crudSubjectResolver,
            crudPermissionService,
            crudDataScopeResolver,
            collectContributors(crudDataScopeContributors),
            crudGovernanceAuditRecorder,
            crudSpecAttributeResolver
        );
    }

    /**
     * 统一执行管线：负责 normalize -> govern -> execute -> audit 的通用阶段编排。
     */
    @Bean
    @ConditionalOnMissingBean
    public ExecutionPipeline executionPipeline(CrudGovernanceService crudGovernanceService) {
        return new ExecutionPipeline(crudGovernanceService);
    }

    /**
     * Query 网关，统一承接查询入口并附加治理主链。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "entloom.crud.query.enabled", havingValue = "true", matchIfMissing = true)
    public QueryGateway queryGateway(
        QueryRouter queryRouter,
        ExecutionPipeline executionPipeline
    ) {
        return new QueryGatewayImpl(queryRouter, executionPipeline);
    }

    /**
     * Command 网关，统一承接命令入口并附加 Spec/幂等策略校验。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "entloom.crud.command.enabled", havingValue = "true", matchIfMissing = true)
    public CommandGateway commandGateway(
        CommandRouter commandRouter,
        IdempotencyManager idempotencyManager,
        IdempotencyPolicy idempotencyPolicy,
        ExecutionPipeline executionPipeline
    ) {
        return new CommandGatewayImpl(commandRouter, idempotencyManager, idempotencyPolicy, executionPipeline);
    }

    @Bean
    @ConditionalOnMissingBean(ImportFormatRegistry.class)
    @ConditionalOnProperty(name = "entloom.crud.import.enabled", havingValue = "true", matchIfMissing = true)
    public ImportFormatRegistry importFormatRegistry(ObjectProvider<ImportFormatDescriptor[]> descriptors) {
        return new DefaultImportFormatRegistry(Arrays.asList(nullToEmpty(descriptors.getIfAvailable(), ImportFormatDescriptor.class)));
    }

    @Bean
    @ConditionalOnMissingBean(ExportFormatRegistry.class)
    @ConditionalOnProperty(name = "entloom.crud.export.enabled", havingValue = "true", matchIfMissing = true)
    public ExportFormatRegistry exportFormatRegistry(ObjectProvider<ExportFormatDescriptor[]> descriptors) {
        return new DefaultExportFormatRegistry(Arrays.asList(nullToEmpty(descriptors.getIfAvailable(), ExportFormatDescriptor.class)));
    }

    @Bean
    @ConditionalOnMissingBean(name = "importSceneHandlerRegistry")
    @ConditionalOnProperty(name = "entloom.crud.import.enabled", havingValue = "true", matchIfMissing = true)
    public SceneHandlerRegistry<ImportSpec, ImportResult> importSceneHandlerRegistry() {
        return new DefaultSceneHandlerRegistry<ImportSpec, ImportResult>();
    }

    @Bean
    @ConditionalOnMissingBean(name = "exportSceneHandlerRegistry")
    @ConditionalOnProperty(name = "entloom.crud.export.enabled", havingValue = "true", matchIfMissing = true)
    public SceneHandlerRegistry<ExportSpec, ExportResult> exportSceneHandlerRegistry() {
        return new DefaultSceneHandlerRegistry<ExportSpec, ExportResult>();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "entloom.crud.import.enabled", havingValue = "true", matchIfMissing = true)
    public ImportPayloadCustomizerRegistry importPayloadCustomizerRegistry(ObjectProvider<ImportPayloadCustomizer[]> customizers) {
        return new ImportPayloadCustomizerRegistry(Arrays.asList(nullToEmpty(customizers.getIfAvailable(), ImportPayloadCustomizer.class)));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "entloom.crud.export.enabled", havingValue = "true", matchIfMissing = true)
    public ExportPayloadCustomizerRegistry exportPayloadCustomizerRegistry(ObjectProvider<ExportPayloadCustomizer[]> customizers) {
        return new ExportPayloadCustomizerRegistry(Arrays.asList(nullToEmpty(customizers.getIfAvailable(), ExportPayloadCustomizer.class)));
    }

    @Bean
    @ConditionalOnMissingBean(FileService.class)
    @ConditionalOnExpression(IMPORT_EXPORT_ENABLED_EXPRESSION)
    public FileService crudFileService(CrudProperties properties) {
        CrudProperties.ImportExport importExport = properties.getImportExport();
        return new LocalFileService(
            importExport.getStorageDirectory() + "/files",
            importExport.getMaxFileBytes(),
            Duration.ofHours(importExport.getRetentionHours())
        );
    }

    @Bean
    @ConditionalOnMissingBean(TaskService.class)
    @ConditionalOnExpression(IMPORT_EXPORT_ENABLED_EXPRESSION)
    public TaskService crudTaskService(CrudProperties properties) {
        return new LocalTaskService(properties.getImportExport().getStorageDirectory() + "/tasks");
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression(IMPORT_EXPORT_ENABLED_EXPRESSION)
    public TaskFileAccessGuard taskFileAccessGuard() {
        return new TaskFileAccessGuard();
    }

    @Bean
    @ConditionalOnMissingBean(ImportEngine.class)
    @ConditionalOnProperty(name = "entloom.crud.import.enabled", havingValue = "true", matchIfMissing = true)
    public ImportEngine defaultImportEngine(
        ImportFormatRegistry importFormatRegistry,
        FileService fileService,
        TaskService taskService,
        CommandEngine commandEngine,
        EntityMetaRegistry entityMetaRegistry
    ) {
        return new DefaultImportEngine(importFormatRegistry, fileService, taskService, commandEngine, entityMetaRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(ExportEngine.class)
    @ConditionalOnProperty(name = "entloom.crud.export.enabled", havingValue = "true", matchIfMissing = true)
    public ExportEngine defaultExportEngine(
        QueryEngine queryEngine,
        ExportFormatRegistry exportFormatRegistry,
        FileService fileService,
        TaskService taskService,
        EntityMetaRegistry entityMetaRegistry,
        CrudProperties properties,
        ObjectProvider<ExportDictionaryResolver> exportDictionaryResolverProvider
    ) {
        return new DefaultExportEngine(
            queryEngine,
            exportFormatRegistry,
            fileService,
            taskService,
            entityMetaRegistry,
            new DefaultExportColumnResolver(),
            new DefaultExportValueRenderer(exportDictionaryResolverProvider.getIfAvailable()),
            new ExportRenderOptionsResolver(properties == null ? null : properties.getController().getDefaultTimezone())
        );
    }

    @Bean
    @ConditionalOnMissingBean(ImportGateway.class)
    @ConditionalOnProperty(name = "entloom.crud.import.enabled", havingValue = "true", matchIfMissing = true)
    public ImportGateway importGateway(
        ImportFormatRegistry importFormatRegistry,
        ImportPayloadCustomizerRegistry importPayloadCustomizerRegistry,
        ObjectProvider<ImportEngine> importEngineProvider,
        @Qualifier("importSceneHandlerRegistry") SceneHandlerRegistry<ImportSpec, ImportResult> importSceneHandlerRegistry,
        ExecutionPipeline executionPipeline,
        TaskService taskService,
        TaskFileAccessGuard taskFileAccessGuard
    ) {
        return new ImportGatewayImpl(
            importFormatRegistry,
            importPayloadCustomizerRegistry,
            importEngineProvider.getIfAvailable(),
            importSceneHandlerRegistry,
            executionPipeline,
            taskService,
            taskFileAccessGuard
        );
    }

    @Bean
    @ConditionalOnMissingBean(ExportGateway.class)
    @ConditionalOnProperty(name = "entloom.crud.export.enabled", havingValue = "true", matchIfMissing = true)
    public ExportGateway exportGateway(
        ExportFormatRegistry exportFormatRegistry,
        ExportPayloadCustomizerRegistry exportPayloadCustomizerRegistry,
        ObjectProvider<ExportEngine> exportEngineProvider,
        @Qualifier("exportSceneHandlerRegistry") SceneHandlerRegistry<ExportSpec, ExportResult> exportSceneHandlerRegistry,
        ExecutionPipeline executionPipeline,
        TaskService taskService,
        TaskFileAccessGuard taskFileAccessGuard
    ) {
        return new ExportGatewayImpl(
            exportFormatRegistry,
            exportPayloadCustomizerRegistry,
            exportEngineProvider.getIfAvailable(),
            exportSceneHandlerRegistry,
            executionPipeline,
            taskService,
            taskFileAccessGuard
        );
    }

    /**
     * 收集可用的数据范围贡献器。
     */
    private List<CrudDataScopeContributor> collectContributors(ObjectProvider<CrudDataScopeContributor[]> provider) {
        List<CrudDataScopeContributor> contributors = new ArrayList<CrudDataScopeContributor>();
        CrudDataScopeContributor[] values = provider.getIfAvailable();
        if (values == null) {
            return contributors;
        }
        for (CrudDataScopeContributor contributor : values) {
            if (contributor != null) {
                contributors.add(contributor);
            }
        }
        return contributors;
    }

    @SuppressWarnings("unchecked")
    private <T> T[] nullToEmpty(T[] values, Class<T> type) {
        if (values != null) {
            return values;
        }
        return (T[]) java.lang.reflect.Array.newInstance(type, 0);
    }
}
