package com.entloom.crud.starter;

import com.entloom.crud.api.enums.CrudReadResultMode;
import com.entloom.crud.core.capability.command.gateway.CommandGateway;
import com.entloom.crud.core.capability.query.gateway.QueryGateway;
import com.entloom.crud.core.capability.stats.StatsGateway;
import com.entloom.crud.core.idempotency.IdempotencyPolicy;
import com.entloom.crud.starter.config.CrudProperties;
import com.entloom.crud.starter.config.CrudAutoConfiguration;
import com.entloom.crud.starter.support.StarterJdbcTestSupportConfiguration;
import com.entloom.crud.starter.web.assembler.CrudCommandSpecAssembler;
import com.entloom.crud.starter.web.assembler.CrudQuerySpecAssembler;
import com.entloom.crud.starter.web.assembler.CrudStatsSpecAssembler;
import com.entloom.crud.starter.web.controller.EntCrudCommandController;
import com.entloom.crud.starter.web.controller.EntCrudQueryController;
import com.entloom.crud.starter.web.controller.EntCrudStatsController;
import com.entloom.crud.starter.web.error.CrudHttpExceptionTranslator;
import com.entloom.crud.starter.web.facade.EntCrudCommandFacade;
import com.entloom.crud.starter.web.facade.EntCrudQueryFacade;
import com.entloom.crud.starter.web.facade.EntCrudStatsFacade;
import com.entloom.crud.starter.web.support.CrudRequestSupport;
import com.entloom.crud.starter.web.support.CrudResponseBuilder;
import com.entloom.crud.starter.web.assembler.CrudSchemaAssembler;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starter 配置 Key、默认 Bean 与条件装配合同。
 */
class CrudStarterConfigurationContractTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(StarterJdbcTestSupportConfiguration.class, CrudAutoConfiguration.class)
        .withPropertyValues(
            "entloom.crud.controller.enabled=true",
            "entloom.crud.import-export.storage-directory=target/entloom-crud-configuration-contract"
        );

    @Test
    void starter_configuration_keys_should_bind_to_typed_properties() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("entloom.crud.sql-log.mode", "FULL");
        values.put("entloom.crud.sql-log.sample-rate", "0.75");
        values.put("entloom.crud.sql-log.output", "BOTH");
        values.put("entloom.crud.sql-log.pretty", "true");
        values.put("entloom.crud.controller.enabled", "true");
        values.put("entloom.crud.controller.base-path", "/internal/crud");
        values.put("entloom.crud.controller.default-timezone", "UTC");
        values.put("entloom.crud.controller.default-read-result-mode", "ENTITY");
        values.put("entloom.crud.query.enabled", "false");
        values.put("entloom.crud.command.enabled", "false");
        values.put("entloom.crud.import.enabled", "false");
        values.put("entloom.crud.export.enabled", "true");
        values.put("entloom.crud.import-export.storage-directory", "target/typed-crud");
        values.put("entloom.crud.import-export.retention-hours", "12");
        values.put("entloom.crud.import-export.max-file-bytes", "1048576");
        values.put("entloom.crud.idempotency.mode", "REQUIRED");

        CrudProperties properties = new Binder(new MapConfigurationPropertySource(values))
            .bind("entloom.crud", Bindable.of(CrudProperties.class))
            .get();

        Assertions.assertEquals(CrudProperties.SqlLog.Mode.FULL, properties.getSqlLog().getMode());
        Assertions.assertEquals(0.75d, properties.getSqlLog().getSampleRate());
        Assertions.assertEquals(CrudProperties.SqlLog.Output.BOTH, properties.getSqlLog().getOutput());
        Assertions.assertTrue(properties.getSqlLog().isPretty());
        Assertions.assertTrue(properties.getController().isEnabled());
        Assertions.assertEquals("/internal/crud", properties.getController().getBasePath());
        Assertions.assertEquals("UTC", properties.getController().getDefaultTimezone());
        Assertions.assertEquals(CrudReadResultMode.ENTITY, properties.getController().getDefaultReadResultMode());
        Assertions.assertFalse(properties.getQuery().isEnabled());
        Assertions.assertFalse(properties.getCommand().isEnabled());
        Assertions.assertFalse(properties.getImport().isEnabled());
        Assertions.assertTrue(properties.getExport().isEnabled());
        Assertions.assertEquals("target/typed-crud", properties.getImportExport().getStorageDirectory());
        Assertions.assertEquals(12L, properties.getImportExport().getRetentionHours());
        Assertions.assertEquals(1048576L, properties.getImportExport().getMaxFileBytes());
        Assertions.assertEquals(IdempotencyPolicy.Mode.REQUIRED, properties.getIdempotency().getMode());
    }

    @Test
    void default_configuration_should_register_contract_beans_without_http_controllers() {
        new ApplicationContextRunner()
            .withUserConfiguration(StarterJdbcTestSupportConfiguration.class, CrudAutoConfiguration.class)
            .withPropertyValues(
                "entloom.crud.controller.enabled=false",
                "entloom.crud.import-export.storage-directory=target/ent-loom-crud-default-contract"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(CrudProperties.class);
                assertThat(context).hasSingleBean(CrudResponseBuilder.class);
                assertThat(context).hasSingleBean(CrudHttpExceptionTranslator.class);
                assertThat(context).hasSingleBean(CrudRequestSupport.class);
                assertThat(context).hasSingleBean(CrudSchemaAssembler.class);
                assertThat(context).hasSingleBean(CrudQuerySpecAssembler.class);
                assertThat(context).hasSingleBean(CrudCommandSpecAssembler.class);
                assertThat(context).hasSingleBean(CrudStatsSpecAssembler.class);
                assertThat(context).hasSingleBean(QueryGateway.class);
                assertThat(context).hasSingleBean(CommandGateway.class);
                assertThat(context).hasSingleBean(StatsGateway.class);
                assertThat(context).hasSingleBean(EntCrudQueryFacade.class);
                assertThat(context).hasSingleBean(EntCrudCommandFacade.class);
                assertThat(context).hasSingleBean(EntCrudStatsFacade.class);
                assertThat(context).doesNotHaveBean(EntCrudQueryController.class);
                assertThat(context).doesNotHaveBean(EntCrudCommandController.class);
                assertThat(context).doesNotHaveBean(EntCrudStatsController.class);
            });
    }

    @Test
    void disabling_query_and_command_should_keep_stats_controller_condition() {
        contextRunner
            .withPropertyValues(
                "entloom.crud.query.enabled=false",
                "entloom.crud.command.enabled=false",
                "entloom.crud.import.enabled=false",
                "entloom.crud.export.enabled=false"
            )
            .run(context -> {
                assertThat(context).doesNotHaveBean(QueryGateway.class);
                assertThat(context).doesNotHaveBean(CrudQuerySpecAssembler.class);
                assertThat(context).doesNotHaveBean(EntCrudQueryFacade.class);
                assertThat(context).doesNotHaveBean(EntCrudQueryController.class);
                assertThat(context).doesNotHaveBean(CommandGateway.class);
                assertThat(context).doesNotHaveBean(CrudCommandSpecAssembler.class);
                assertThat(context).doesNotHaveBean(EntCrudCommandFacade.class);
                assertThat(context).doesNotHaveBean(EntCrudCommandController.class);
                assertThat(context).hasSingleBean(StatsGateway.class);
                assertThat(context).hasSingleBean(EntCrudStatsFacade.class);
                assertThat(context).hasSingleBean(EntCrudStatsController.class);
            });
    }

    @Test
    void business_response_builder_should_override_default_bean() {
        CrudResponseBuilder businessResponseBuilder = new CrudResponseBuilder();
        contextRunner
            .withBean(CrudResponseBuilder.class, () -> businessResponseBuilder)
            .run(context -> assertThat(context.getBean(CrudResponseBuilder.class)).isSameAs(businessResponseBuilder));
    }

    @Test
    void starter_configuration_should_have_no_legacy_spring_config_package() {
        assertThat(CrudProperties.class.getPackage().getName())
            .isEqualTo("com.entloom.crud.starter.config");
        assertThat(CrudAutoConfiguration.class.getPackage().getName())
            .isEqualTo("com.entloom.crud.starter.config");
        assertThat(ClassUtils.isPresent(
            "com.entloom.crud.spring.config.CrudProperties",
            getClass().getClassLoader()
        )).isFalse();
    }
}
