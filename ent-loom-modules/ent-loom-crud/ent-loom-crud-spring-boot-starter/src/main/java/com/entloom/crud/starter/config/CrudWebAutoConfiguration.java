package com.entloom.crud.starter.config;

import com.entloom.crud.core.capability.command.gateway.CommandGateway;
import com.entloom.crud.core.capability.exporting.ExportGateway;
import com.entloom.crud.core.capability.importing.ImportGateway;
import com.entloom.crud.core.capability.query.gateway.QueryGateway;
import com.entloom.crud.core.foundation.taskfile.FileService;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.crud.core.runtime.router.CommandActionSceneResolver;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.spring.config.CrudProperties;
import com.entloom.crud.core.capability.stats.StatsGateway;
import com.entloom.crud.core.capability.stats.StatsPayloadCustomizerRegistry;
import com.entloom.crud.starter.web.assembler.CrudCommandSpecAssembler;
import com.entloom.crud.starter.web.assembler.CrudImportExportResponseAssembler;
import com.entloom.crud.starter.web.assembler.CrudImportExportSpecAssembler;
import com.entloom.crud.starter.web.assembler.CrudQuerySpecAssembler;
import com.entloom.crud.starter.web.assembler.CrudSchemaAssembler;
import com.entloom.crud.starter.web.assembler.CrudStringFilterPolicy;
import com.entloom.crud.starter.web.assembler.CrudStatsSpecAssembler;
import com.entloom.crud.starter.web.controller.EntCrudCommandController;
import com.entloom.crud.starter.web.controller.EntCrudExportController;
import com.entloom.crud.starter.web.controller.EntCrudImportController;
import com.entloom.crud.starter.web.controller.EntCrudQueryController;
import com.entloom.crud.starter.web.controller.EntCrudStatsController;
import com.entloom.crud.starter.web.error.CrudHttpExceptionTranslator;
import com.entloom.crud.starter.web.facade.EntCrudCommandFacade;
import com.entloom.crud.starter.web.facade.EntCrudExportFacade;
import com.entloom.crud.starter.web.facade.EntCrudImportFacade;
import com.entloom.crud.starter.web.facade.EntCrudQueryFacade;
import com.entloom.crud.starter.web.facade.EntCrudStatsFacade;
import com.entloom.crud.starter.web.registry.CrudViewTypeRegistrar;
import com.entloom.crud.starter.web.registry.ExposedEntityRegistry;
import com.entloom.crud.starter.web.registry.ExposedViewTypeRegistry;
import com.entloom.crud.starter.web.support.CrudResponseDateFormatAdvice;
import com.entloom.crud.starter.web.support.CrudResponseDateFormatter;
import com.entloom.crud.starter.web.support.CrudRequestSupport;
import com.entloom.crud.starter.web.support.CrudResponseBuilder;
import com.entloom.crud.starter.web.time.BuiltinCrudTimePresetResolver;
import com.entloom.crud.starter.web.time.CrudTimeFilterResolver;
import com.entloom.crud.starter.web.time.CrudTimePresetResolver;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;

/**
 * starter Web 层自动配置。
 */
@Configuration
@ConditionalOnClass(EntCrudQueryController.class)
public class CrudWebAutoConfiguration {
    private static final String CONTROLLER_ENABLED_PROPERTY = "entloom.crud.controller.enabled";
    private static final String QUERY_ENABLED_PROPERTY = "entloom.crud.query.enabled";
    private static final String COMMAND_ENABLED_PROPERTY = "entloom.crud.command.enabled";
    private static final String IMPORT_ENABLED_PROPERTY = "entloom.crud.import.enabled";
    private static final String EXPORT_ENABLED_PROPERTY = "entloom.crud.export.enabled";
    private static final String IMPORT_EXPORT_ENABLED_EXPRESSION =
        "${" + IMPORT_ENABLED_PROPERTY + ":true} or ${" + EXPORT_ENABLED_PROPERTY + ":true}";

    @Bean
    @ConditionalOnMissingBean
    public ExposedEntityRegistry exposedEntityRegistry(CrudProperties properties, EntityMetaRegistry entityMetaRegistry) {
        ExposedEntityRegistry registry = new ExposedEntityRegistry(entityMetaRegistry);
        registry.setIncludeEntities(properties.getController().getIncludeEntities());
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public ExposedViewTypeRegistry exposedViewTypeRegistry(List<CrudViewTypeRegistrar> registrars) {
        ExposedViewTypeRegistry registry = new ExposedViewTypeRegistry();
        for (CrudViewTypeRegistrar registrar : registrars == null ? Collections.<CrudViewTypeRegistrar>emptyList() : registrars) {
            registrar.register(registry);
        }
        return registry;
    }

    /**
     * 创建 CRUD 专用 ObjectMapper：优先复制业务 objectMapper，再叠加 CRUD 兼容能力。
     */
    @Bean("crudObjectMapper")
    @ConditionalOnMissingBean(name = "crudObjectMapper")
    public ObjectMapper crudObjectMapper(
        CrudProperties properties,
        @Qualifier("objectMapper") ObjectProvider<ObjectMapper> businessObjectMapperProvider
    ) {
        ObjectMapper baseMapper = businessObjectMapperProvider.getIfAvailable();
        ObjectMapper objectMapper = baseMapper == null ? new ObjectMapper() : baseMapper.copy();
        if (baseMapper == null) {
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            objectMapper.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
            objectMapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
            objectMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
            objectMapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);
            objectMapper.setDateFormat(new StdDateFormat().withColonInTimeZone(true));
            objectMapper.setTimeZone(TimeZone.getTimeZone("UTC"));
        }

        ZoneId defaultZoneId = resolveDefaultZone(properties == null ? null : properties.getController().getDefaultTimezone());
        TimeZone mapperTimeZone = objectMapper.getDeserializationConfig().getTimeZone();
        if (mapperTimeZone == null) {
            mapperTimeZone = TimeZone.getTimeZone("UTC");
        }
        SimpleModule dateCompatModule = new SimpleModule();
        dateCompatModule.addDeserializer(java.util.Date.class, new CrudDateCompatDeserializer(mapperTimeZone, defaultZoneId));
        objectMapper.registerModule(dateCompatModule);
        return objectMapper;
    }

    @Bean
    @ConditionalOnMissingBean
    public CrudResponseDateFormatter crudResponseDateFormatter(
        CrudProperties properties,
        @Qualifier("crudObjectMapper") ObjectMapper objectMapper
    ) {
        return new CrudResponseDateFormatter(
            objectMapper,
            properties == null ? null : properties.getController().getDefaultTimezone()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public CrudResponseDateFormatAdvice crudResponseDateFormatAdvice(CrudResponseDateFormatter dateFormatter) {
        return new CrudResponseDateFormatAdvice(dateFormatter);
    }

    private ZoneId resolveDefaultZone(String timezone) {
        if (timezone == null || timezone.trim().isEmpty()) {
            return ZoneId.of(CrudProperties.Controller.DEFAULT_TIMEZONE);
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception ignore) {
            return ZoneId.of(CrudProperties.Controller.DEFAULT_TIMEZONE);
        }
    }


    @Bean
    @ConditionalOnMissingBean
    public CrudHttpExceptionTranslator crudHttpExceptionTranslator() {
        return new CrudHttpExceptionTranslator(crudResponseBuilder());
    }

    @Bean
    @ConditionalOnMissingBean
    public CrudResponseBuilder crudResponseBuilder() {
        return new CrudResponseBuilder();
    }

    @Bean
    @ConditionalOnMissingBean
    public CrudRequestSupport crudRequestSupport(
            ExposedEntityRegistry exposedEntityRegistry,
            ExposedViewTypeRegistry exposedViewTypeRegistry
    ) {
        return new CrudRequestSupport(exposedEntityRegistry, exposedViewTypeRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public CrudSchemaAssembler crudSchemaAssembler(EntityMetaRegistry entityMetaRegistry) {
        return new CrudSchemaAssembler(entityMetaRegistry);
    }

    @Bean
    public CrudTimePresetResolver crudBuiltinTimePresetResolver() {
        return new BuiltinCrudTimePresetResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public CrudTimeFilterResolver crudTimeFilterResolver(
            CrudProperties properties,
            List<CrudTimePresetResolver> presetResolvers
    ) {
        return new CrudTimeFilterResolver(
                properties.getController().getDefaultTimezone(),
                properties.getController().getDefaultTimeField(),
                presetResolvers
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = QUERY_ENABLED_PROPERTY, havingValue = "true", matchIfMissing = true)
    public CrudQuerySpecAssembler crudQuerySpecAssembler(
            CrudRequestSupport requestSupport,
            CrudTimeFilterResolver timeFilterResolver,
            @Qualifier("crudObjectMapper") ObjectMapper objectMapper,
            CrudProperties properties,
            CrudStringFilterPolicy stringFilterPolicy
    ) {
        return new CrudQuerySpecAssembler(
            requestSupport,
            timeFilterResolver,
            objectMapper,
            properties.getController().getDefaultReadResultMode(),
            stringFilterPolicy
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public CrudStatsSpecAssembler crudStatsSpecAssembler(
            CrudRequestSupport requestSupport,
            CrudTimeFilterResolver timeFilterResolver,
            @Qualifier("crudObjectMapper") ObjectMapper objectMapper,
            CrudStringFilterPolicy stringFilterPolicy,
            StatsPayloadCustomizerRegistry statsPayloadCustomizerRegistry
    ) {
        return new CrudStatsSpecAssembler(
            requestSupport,
            timeFilterResolver,
            objectMapper,
            stringFilterPolicy,
            statsPayloadCustomizerRegistry
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public CrudStringFilterPolicy crudStringFilterPolicy(CrudProperties properties) {
        CrudProperties.StringFilter config = properties.getController().getStringFilter();
        CrudStringFilterPolicy.LikeMode likeMode = CrudStringFilterPolicy.LikeMode.valueOf(config.getDefaultLikeMode().name());
        return new CrudStringFilterPolicy(
            config.isDefaultLikeEnabled(),
            likeMode,
            config.getDefaultLikeExcludeFields()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = COMMAND_ENABLED_PROPERTY, havingValue = "true", matchIfMissing = true)
    public CrudCommandSpecAssembler crudCommandSpecAssembler(
            CrudRequestSupport requestSupport,
            CommandActionSceneResolver actionSceneResolver,
            EntityMetaRegistry metaRegistry,
            @Qualifier("crudObjectMapper") ObjectMapper objectMapper
    ) {
        return new CrudCommandSpecAssembler(requestSupport, actionSceneResolver, metaRegistry, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression(IMPORT_EXPORT_ENABLED_EXPRESSION)
    public CrudImportExportSpecAssembler crudImportExportSpecAssembler(CrudRequestSupport requestSupport) {
        return new CrudImportExportSpecAssembler(requestSupport);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression(IMPORT_EXPORT_ENABLED_EXPRESSION)
    public CrudImportExportResponseAssembler crudImportExportResponseAssembler() {
        return new CrudImportExportResponseAssembler();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(CrudSubjectResolver.class)
    @ConditionalOnProperty(name = QUERY_ENABLED_PROPERTY, havingValue = "true", matchIfMissing = true)
    public EntCrudQueryFacade entCrudQueryFacade(
            QueryGateway queryGateway,
            CrudSubjectResolver crudSubjectResolver,
            CrudQuerySpecAssembler querySpecAssembler,
            CrudResponseBuilder crudResponseBuilder,
            CrudSchemaAssembler crudSchemaAssembler
    ) {
        return new EntCrudQueryFacade(queryGateway, crudSubjectResolver, querySpecAssembler, crudResponseBuilder, crudSchemaAssembler);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(CrudSubjectResolver.class)
    public EntCrudStatsFacade entCrudStatsFacade(
            StatsGateway statsGateway,
            CrudSubjectResolver crudSubjectResolver,
            CrudStatsSpecAssembler crudStatsSpecAssembler,
            CrudResponseBuilder crudResponseBuilder,
            CrudSchemaAssembler crudSchemaAssembler
    ) {
        return new EntCrudStatsFacade(statsGateway, crudSubjectResolver, crudStatsSpecAssembler, crudResponseBuilder, crudSchemaAssembler);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(CrudSubjectResolver.class)
    @ConditionalOnProperty(name = COMMAND_ENABLED_PROPERTY, havingValue = "true", matchIfMissing = true)
    public EntCrudCommandFacade entCrudCommandFacade(
            CommandGateway commandGateway,
            CrudSubjectResolver crudSubjectResolver,
            CrudCommandSpecAssembler commandSpecAssembler,
            CrudResponseBuilder crudResponseBuilder
    ) {
        return new EntCrudCommandFacade(commandGateway, crudSubjectResolver, commandSpecAssembler, crudResponseBuilder);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(CrudSubjectResolver.class)
    @ConditionalOnProperty(name = EXPORT_ENABLED_PROPERTY, havingValue = "true", matchIfMissing = true)
    public EntCrudExportFacade entCrudExportFacade(
            ExportGateway exportGateway,
            FileService fileService,
            CrudSubjectResolver crudSubjectResolver,
            CrudImportExportSpecAssembler specAssembler,
            CrudImportExportResponseAssembler responseAssembler,
            CrudResponseBuilder crudResponseBuilder
    ) {
        return new EntCrudExportFacade(
            exportGateway,
            fileService,
            crudSubjectResolver,
            specAssembler,
            responseAssembler,
            crudResponseBuilder
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(CrudSubjectResolver.class)
    @ConditionalOnProperty(name = IMPORT_ENABLED_PROPERTY, havingValue = "true", matchIfMissing = true)
    public EntCrudImportFacade entCrudImportFacade(
            ImportGateway importGateway,
            FileService fileService,
            CrudSubjectResolver crudSubjectResolver,
            CrudImportExportSpecAssembler specAssembler,
            CrudImportExportResponseAssembler responseAssembler,
            CrudResponseBuilder crudResponseBuilder
    ) {
        return new EntCrudImportFacade(
            importGateway,
            fileService,
            crudSubjectResolver,
            specAssembler,
            responseAssembler,
            crudResponseBuilder
        );
    }

    @Bean
    @ConditionalOnExpression("${entloom.crud.controller.enabled:false} and ${entloom.crud.query.enabled:true}")
    @ConditionalOnBean(EntCrudQueryFacade.class)
    public EntCrudQueryController entCrudQueryController(
            EntCrudQueryFacade entCrudQueryFacade
    ) {
        return new EntCrudQueryController(entCrudQueryFacade);
    }

    @Bean
    @ConditionalOnExpression("${entloom.crud.controller.enabled:false} and ${entloom.crud.command.enabled:true}")
    @ConditionalOnBean(EntCrudCommandFacade.class)
    public EntCrudCommandController entCrudCommandController(
            EntCrudCommandFacade entCrudCommandFacade
    ) {
        return new EntCrudCommandController(entCrudCommandFacade);
    }

    @Bean
    @ConditionalOnExpression("${entloom.crud.controller.enabled:false} and ${entloom.crud.export.enabled:true}")
    @ConditionalOnBean(EntCrudExportFacade.class)
    public EntCrudExportController entCrudExportController(EntCrudExportFacade entCrudExportFacade) {
        return new EntCrudExportController(entCrudExportFacade);
    }

    @Bean
    @ConditionalOnExpression("${entloom.crud.controller.enabled:false} and ${entloom.crud.import.enabled:true}")
    @ConditionalOnBean(EntCrudImportFacade.class)
    public EntCrudImportController entCrudImportController(EntCrudImportFacade entCrudImportFacade) {
        return new EntCrudImportController(entCrudImportFacade);
    }

    @Bean
    @ConditionalOnProperty(name = CONTROLLER_ENABLED_PROPERTY, havingValue = "true")
    @ConditionalOnBean(EntCrudStatsFacade.class)
    public EntCrudStatsController entCrudStatsController(EntCrudStatsFacade entCrudStatsFacade) {
        return new EntCrudStatsController(entCrudStatsFacade);
    }
}
