package com.entloom.crud.starter.config.module;

import com.entloom.crud.annotations.EntCrudCommandAction;
import com.entloom.crud.annotations.EntCrudQueryHandler;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationDomain;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.core.capability.exporting.ExportHandler;
import com.entloom.crud.core.capability.exporting.ExportResult;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.importing.ImportHandler;
import com.entloom.crud.core.capability.importing.ImportResult;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.capability.command.scene.CommandActionSceneHandler;
import com.entloom.crud.core.capability.command.scene.CommandActionRegistration;
import com.entloom.crud.core.capability.command.scene.CommandSceneHandler;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.capability.query.scene.QueryDetailSceneHandler;
import com.entloom.crud.core.capability.query.scene.QueryFindOneSceneHandler;
import com.entloom.crud.core.capability.query.scene.QueryListSceneHandler;
import com.entloom.crud.core.capability.query.scene.QueryPageSceneHandler;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.runtime.router.DefaultCommandRouter;
import com.entloom.crud.core.runtime.router.DefaultQueryRouter;
import com.entloom.crud.core.runtime.router.CommandRouter;
import com.entloom.crud.core.runtime.router.QueryRouter;
import com.entloom.crud.core.runtime.scene.SceneHandlerRegistry;
import com.entloom.crud.core.util.RouteKeyFactory;
import com.entloom.crud.core.capability.stats.StatsResult;
import com.entloom.crud.core.capability.stats.StatsPayloadCustomizer;
import com.entloom.crud.core.capability.stats.StatsPayloadCustomizerRegistry;
import com.entloom.crud.core.capability.stats.StatsSceneHandler;
import com.entloom.crud.core.capability.stats.StatsSpec;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;

/**
 * 启动期 SceneHandler 自动注册器。
 */
public class SceneHandlerRegistrar implements InitializingBean {
    private final ApplicationContext applicationContext;
    private final QueryRouter queryRouter;
    private final CommandRouter commandRouter;
    private final SceneHandlerRegistry<StatsSpec, StatsResult> statsSceneHandlerRegistry;
    private final SceneHandlerRegistry<ImportSpec, ImportResult> importSceneHandlerRegistry;
    private final SceneHandlerRegistry<ExportSpec, ExportResult> exportSceneHandlerRegistry;
    private final StatsPayloadCustomizerRegistry statsPayloadCustomizerRegistry;

    public SceneHandlerRegistrar(
        ApplicationContext applicationContext,
        QueryRouter queryRouter,
        CommandRouter commandRouter,
        SceneHandlerRegistry<StatsSpec, StatsResult> statsSceneHandlerRegistry,
        SceneHandlerRegistry<ImportSpec, ImportResult> importSceneHandlerRegistry,
        SceneHandlerRegistry<ExportSpec, ExportResult> exportSceneHandlerRegistry,
        StatsPayloadCustomizerRegistry statsPayloadCustomizerRegistry
    ) {
        this.applicationContext = applicationContext;
        this.queryRouter = queryRouter;
        this.commandRouter = commandRouter;
        this.statsSceneHandlerRegistry = statsSceneHandlerRegistry;
        this.importSceneHandlerRegistry = importSceneHandlerRegistry;
        this.exportSceneHandlerRegistry = exportSceneHandlerRegistry;
        this.statsPayloadCustomizerRegistry = statsPayloadCustomizerRegistry;
    }

    @Override
    public void afterPropertiesSet() {
        registerQueryHandlers();
        registerCommandHandlers();
        registerStatsHandlers();
        registerImportHandlers();
        registerExportHandlers();
        registerStatsPayloadCustomizers();
    }

    @SuppressWarnings("rawtypes")
    private void registerQueryHandlers() {
        Map<String, QueryPageSceneHandler> pageHandlers = applicationContext.getBeansOfType(QueryPageSceneHandler.class);
        Map<String, QueryListSceneHandler> listHandlers = applicationContext.getBeansOfType(QueryListSceneHandler.class);
        Map<String, QueryFindOneSceneHandler> findOneHandlers = applicationContext.getBeansOfType(QueryFindOneSceneHandler.class);
        Map<String, QueryDetailSceneHandler> detailHandlers = applicationContext.getBeansOfType(QueryDetailSceneHandler.class);
        if (pageHandlers.isEmpty() && listHandlers.isEmpty() && findOneHandlers.isEmpty() && detailHandlers.isEmpty()) {
            return;
        }
        if (!(queryRouter instanceof DefaultQueryRouter)) {
            throw new ValidationException("存在 QuerySceneHandler，但 QueryRouter 不是 DefaultQueryRouter");
        }
        DefaultQueryRouter router = (DefaultQueryRouter) queryRouter;
        for (QueryPageSceneHandler handler : pageHandlers.values()) {
            Class<?> beanClass = ClassUtils.getUserClass(handler);
            validateQueryRouteKeys(handler.routeKeys(), QueryOperation.PAGE, beanClass);
            validateQueryAnnotation(handler.routeKeys(), handler.defaultStrategy(), beanClass);
            router.registerPageSceneHandler(handler);
        }
        for (QueryListSceneHandler handler : listHandlers.values()) {
            Class<?> beanClass = ClassUtils.getUserClass(handler);
            validateQueryRouteKeys(handler.routeKeys(), QueryOperation.LIST, beanClass);
            validateQueryAnnotation(handler.routeKeys(), handler.defaultStrategy(), beanClass);
            router.registerListSceneHandler(handler);
        }
        for (QueryFindOneSceneHandler handler : findOneHandlers.values()) {
            Class<?> beanClass = ClassUtils.getUserClass(handler);
            validateQueryRouteKeys(handler.routeKeys(), QueryOperation.FIND_ONE, beanClass);
            validateQueryAnnotation(handler.routeKeys(), handler.defaultStrategy(), beanClass);
            router.registerFindOneSceneHandler(handler);
        }
        for (QueryDetailSceneHandler handler : detailHandlers.values()) {
            Class<?> beanClass = ClassUtils.getUserClass(handler);
            validateQueryRouteKeys(handler.routeKeys(), QueryOperation.DETAIL, beanClass);
            validateQueryAnnotation(handler.routeKeys(), handler.defaultStrategy(), beanClass);
            router.registerDetailSceneHandler(handler);
        }
    }

    @SuppressWarnings("rawtypes")
    private void registerCommandHandlers() {
        Map<String, CommandSceneHandler> handlers = applicationContext.getBeansOfType(CommandSceneHandler.class);
        Map<String, CommandActionSceneHandler> actions = applicationContext.getBeansOfType(CommandActionSceneHandler.class);
        if (handlers.isEmpty() && actions.isEmpty()) {
            return;
        }
        if (!(commandRouter instanceof DefaultCommandRouter)) {
            throw new ValidationException("存在 CommandSceneHandler，但 CommandRouter 不是 DefaultCommandRouter");
        }
        DefaultCommandRouter router = (DefaultCommandRouter) commandRouter;
        for (CommandSceneHandler handler : handlers.values()) {
            Class<?> beanClass = ClassUtils.getUserClass(handler);
            CommandOperation operation = handler.operation();
            if (operation == null) {
                throw new ValidationException("Command handler operation 不能为空: " + beanClass.getName());
            }
            validateCommandRouteKeys(handler.routeKeys(), operation, beanClass);
            router.registerSceneHandler(handler);
        }
        for (CommandActionSceneHandler action : actions.values()) {
            router.registerActionSceneHandler(resolveCommandActionRegistration(action));
        }
    }

    private void registerStatsHandlers() {
        Map<String, StatsSceneHandler> handlers = applicationContext.getBeansOfType(StatsSceneHandler.class);
        if (handlers.isEmpty()) {
            return;
        }
        if (statsSceneHandlerRegistry == null) {
            throw new ValidationException("存在 StatsSceneHandler，但 StatsSceneHandlerRegistry 未装配");
        }
        for (StatsSceneHandler handler : handlers.values()) {
            Class<?> beanClass = ClassUtils.getUserClass(handler);
            validateStatsRouteKeys(handler.routeKeys(), beanClass);
            statsSceneHandlerRegistry.register(handler);
        }
    }

    private void registerImportHandlers() {
        Map<String, ImportHandler> handlers = applicationContext.getBeansOfType(ImportHandler.class);
        if (handlers.isEmpty()) {
            return;
        }
        if (importSceneHandlerRegistry == null) {
            throw new ValidationException("存在 ImportHandler，但 ImportSceneHandlerRegistry 未装配");
        }
        for (ImportHandler handler : handlers.values()) {
            Class<?> beanClass = ClassUtils.getUserClass(handler);
            validateDomainRouteKeys(handler.routeKeys(), CrudOperationDomain.IMPORT, beanClass, "Import handler");
            importSceneHandlerRegistry.register(handler);
        }
    }

    private void registerExportHandlers() {
        Map<String, ExportHandler> handlers = applicationContext.getBeansOfType(ExportHandler.class);
        if (handlers.isEmpty()) {
            return;
        }
        if (exportSceneHandlerRegistry == null) {
            throw new ValidationException("存在 ExportHandler，但 ExportSceneHandlerRegistry 未装配");
        }
        for (ExportHandler handler : handlers.values()) {
            Class<?> beanClass = ClassUtils.getUserClass(handler);
            validateDomainRouteKeys(handler.routeKeys(), CrudOperationDomain.EXPORT, beanClass, "Export handler");
            exportSceneHandlerRegistry.register(handler);
        }
    }

    private void registerStatsPayloadCustomizers() {
        Map<String, StatsPayloadCustomizer> customizers = applicationContext.getBeansOfType(StatsPayloadCustomizer.class);
        if (customizers.isEmpty()) {
            return;
        }
        if (statsPayloadCustomizerRegistry == null) {
            throw new ValidationException("存在 StatsPayloadCustomizer，但 StatsPayloadCustomizerRegistry 未装配");
        }
        for (StatsPayloadCustomizer customizer : customizers.values()) {
            Class<?> beanClass = ClassUtils.getUserClass(customizer);
            validateStatsRouteKeys(customizer.routeKeys(), beanClass);
            statsPayloadCustomizerRegistry.register(customizer);
        }
    }

    private void validateQueryRouteKeys(Set<CrudRouteKey> routeKeys, QueryOperation op, Class<?> beanClass) {
        if (routeKeys == null || routeKeys.isEmpty()) {
            throw new ValidationException("routeKeys 不能为空: " + beanClass.getName());
        }
        for (CrudRouteKey routeKey : routeKeys) {
            if (routeKey == null) {
                throw new ValidationException("routeKey 不能为空: " + beanClass.getName());
            }
            if (!CrudOperationKey.of(op).equals(routeKey.getOperationKey())) {
                throw new ValidationException(
                    "Query handler op 不匹配: expected=" + CrudOperationKey.of(op) + ", actual=" + routeKey.getOperationKey() + ", bean=" + beanClass.getName()
                );
            }
        }
    }

    private void validateCommandRouteKeys(Set<CrudRouteKey> routeKeys, CommandOperation op, Class<?> beanClass) {
        if (routeKeys == null || routeKeys.isEmpty()) {
            throw new ValidationException("routeKeys 不能为空: " + beanClass.getName());
        }
        for (CrudRouteKey routeKey : routeKeys) {
            if (routeKey == null) {
                throw new ValidationException("routeKey 不能为空: " + beanClass.getName());
            }
            if (!CrudOperationKey.of(op).equals(routeKey.getOperationKey())) {
                throw new ValidationException(
                    "Command handler op 不匹配: expected=" + CrudOperationKey.of(op) + ", actual=" + routeKey.getOperationKey() + ", bean=" + beanClass.getName()
                );
            }
            if (op == CommandOperation.ACTION && routeKey.getScene().isEmpty()) {
                throw new ValidationException("Command ACTION handler 不允许空 scene: " + beanClass.getName());
            }
        }
    }

    private void validateStatsRouteKeys(Set<CrudRouteKey> routeKeys, Class<?> beanClass) {
        if (routeKeys == null || routeKeys.isEmpty()) {
            throw new ValidationException("routeKeys 不能为空: " + beanClass.getName());
        }
        for (CrudRouteKey routeKey : routeKeys) {
            if (routeKey == null) {
                throw new ValidationException("routeKey 不能为空: " + beanClass.getName());
            }
            if (routeKey.getOperationKey() == null || routeKey.getOperationKey().getDomain() != CrudOperationDomain.STATS) {
                throw new ValidationException(
                    "Stats handler op 不匹配: expected=STATS/*, actual=" + routeKey.getOperationKey() + ", bean=" + beanClass.getName()
                );
            }
        }
    }

    private void validateDomainRouteKeys(Set<CrudRouteKey> routeKeys, CrudOperationDomain domain, Class<?> beanClass, String label) {
        if (routeKeys == null || routeKeys.isEmpty()) {
            throw new ValidationException("routeKeys 不能为空: " + beanClass.getName());
        }
        for (CrudRouteKey routeKey : routeKeys) {
            if (routeKey == null) {
                throw new ValidationException("routeKey 不能为空: " + beanClass.getName());
            }
            if (!domain.equals(routeKey.getOperationKey().getDomain())) {
                throw new ValidationException(
                    label + " op 不匹配: expectedDomain=" + domain + ", actual=" + routeKey.getOperationKey() + ", bean=" + beanClass.getName()
                );
            }
        }
    }


    private void validateQueryAnnotation(Set<CrudRouteKey> routeKeys, Object defaultStrategy, Class<?> beanClass) {
        EntCrudQueryHandler annotation = AnnotationUtils.findAnnotation(beanClass, EntCrudQueryHandler.class);
        if (annotation == null) {
            return;
        }
        Class<?>[] entityClasses = annotation.entityClasses();
        if (entityClasses == null || entityClasses.length == 0) {
            throw new ValidationException("@EntCrudQueryHandler.entityClasses 不能为空: " + beanClass.getName());
        }
        Set<String> expectedScenes = new HashSet<String>();
        if (annotation.scenes() != null) {
            for (String scene : annotation.scenes()) {
                String normalized = RouteKeyFactory.normalizeScene(scene);
                if (!normalized.isEmpty()) {
                    expectedScenes.add(normalized);
                }
            }
        }
        if (expectedScenes.isEmpty()) {
            throw new ValidationException("@EntCrudQueryHandler.scenes 不能为空: " + beanClass.getName());
        }
        for (CrudRouteKey routeKey : routeKeys) {
            if (!expectedScenes.contains(routeKey.getScene())) {
                throw new ValidationException("注解 scenes 与 routeKeys 冲突: bean=" + beanClass.getName() + ", scene=" + routeKey.getScene());
            }
            validateEntitiesMatch(routeKey, entityClasses, beanClass, "@EntCrudQueryHandler");
        }
        if (!annotation.defaultStrategy().equals(defaultStrategy)) {
            throw new ValidationException("注解 defaultStrategy 与 handler.defaultStrategy 冲突: " + beanClass.getName());
        }
    }

    private <P, R> CommandActionRegistration<P, R> resolveCommandActionRegistration(CommandActionSceneHandler<P, R> handler) {
        Class<?> beanClass = AopProxyUtils.ultimateTargetClass(handler);
        EntCrudCommandAction annotation = AnnotationUtils.findAnnotation(beanClass, EntCrudCommandAction.class);
        if (annotation == null) {
            throw new ValidationException("ACTION 处理器必须声明 @EntCrudCommandAction: " + beanClass.getName());
        }
        String expectedScene = RouteKeyFactory.normalizeScene(annotation.scene());
        if (expectedScene.isEmpty()) {
            throw new ValidationException("@EntCrudCommandAction.scene 不能为空: " + beanClass.getName());
        }
        ResolvableType handlerType = ResolvableType.forClass(beanClass).as(CommandActionSceneHandler.class);
        if (handlerType.hasUnresolvableGenerics()) {
            throw new ValidationException("ACTION 处理器必须声明明确的请求与响应泛型: " + beanClass.getName());
        }
        Class<P> requestType = resolveActionType(handlerType.getGeneric(0), beanClass);
        Class<R> responseType = resolveActionType(handlerType.getGeneric(1), beanClass);
        return new CommandActionRegistration<P, R>(annotation.entityClass(), expectedScene, requestType, responseType, handler);
    }

    @SuppressWarnings("unchecked")
    private <T> Class<T> resolveActionType(ResolvableType type, Class<?> beanClass) {
        Class<?> resolved = type.resolve();
        if (resolved == null) {
            throw new ValidationException("无法解析 ACTION 处理器泛型: " + beanClass.getName());
        }
        return (Class<T>) resolved;
    }

    private void validateEntitiesMatch(CrudRouteKey routeKey, Class<?>[] entityClasses, Class<?> beanClass, String annotationName) {
        if (routeKey.getEntityTypeNames().size() != entityClasses.length) {
            throw new ValidationException(annotationName + ".entityClasses 与 routeKeys 冲突: " + beanClass.getName());
        }
        String[] expected = new String[entityClasses.length];
        for (int i = 0; i < entityClasses.length; i++) {
            expected[i] = entityClasses[i].getName();
        }
        if (!Arrays.equals(expected, routeKey.getEntityTypeNames().toArray(new String[0]))) {
            throw new ValidationException(annotationName + ".entityClasses 与 routeKeys 冲突: " + beanClass.getName());
        }
    }

    public static SceneHandlerRegistrar create(
        ApplicationContext applicationContext,
        QueryRouter queryRouter,
        CommandRouter commandRouter,
        SceneHandlerRegistry<StatsSpec, StatsResult> statsSceneHandlerRegistry,
        SceneHandlerRegistry<ImportSpec, ImportResult> importSceneHandlerRegistry,
        SceneHandlerRegistry<ExportSpec, ExportResult> exportSceneHandlerRegistry,
        StatsPayloadCustomizerRegistry statsPayloadCustomizerRegistry
    ) {
        return new SceneHandlerRegistrar(
            applicationContext,
            queryRouter,
            commandRouter,
            statsSceneHandlerRegistry,
            importSceneHandlerRegistry,
            exportSceneHandlerRegistry,
            statsPayloadCustomizerRegistry
        );
    }
}
