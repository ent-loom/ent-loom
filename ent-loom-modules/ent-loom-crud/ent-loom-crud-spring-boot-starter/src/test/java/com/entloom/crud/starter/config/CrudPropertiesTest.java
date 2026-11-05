package com.entloom.crud.starter.config;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudIdPolicy;
import com.entloom.crud.api.enums.CrudNullFieldMode;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.enums.SortDirection;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class CrudPropertiesTest {
    @Test
    void 访问策略同时使用配置和JavaBean时拒绝() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
            .withUserConfiguration(CrudPropertiesConfiguration.class)
            .withBean(CrudAccessProperties.class, CrudAccessProperties::new)
            .withPropertyValues("ent.loom.crud.access.identities.operator.id-type=LONG")
            .run(context -> org.assertj.core.api.Assertions.assertThat(context).hasFailed()
                .getFailure().hasMessageContaining("不能同时使用应用配置和 CrudAccessProperties Bean"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"defaults.id-policy", "sql-log.mode", "sql-log.output",
        "controller.default-read-result-mode", "controller.default-null-field-mode",
        "controller.string-filter.default-like-mode", "query.default-sort.time-direction",
        "query.default-sort.id-direction", "command.create-scope-field-validation-mode", "idempotency.mode",
        "governance.permission-rules[0].decision", "access.identities.customer.id-type"})
    void 显式空枚举与空白枚举应在启动时拒绝并定位配置(String property) {
        String key = "ent.loom.crud." + property;
        for (String value : Arrays.asList("", " ")) {
            new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(CrudPropertiesConfiguration.class)
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource("空枚举", Map.of(key, value))))
                .run(context -> {
                    org.assertj.core.api.Assertions.assertThat(context).hasFailed();
                    org.assertj.core.api.Assertions.assertThat(context.getStartupFailure()).hasStackTraceContaining(key);
                });
        }
    }

    @Test
    void 省略枚举沿用默认且连字符枚举继续绑定() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
            .withUserConfiguration(CrudPropertiesConfiguration.class)
            .withPropertyValues("ent.loom.crud.command.create-scope-field-validation-mode=strict-resources")
            .run(context -> {
                org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();
                CrudProperties properties = context.getBean(CrudProperties.class);
                Assertions.assertEquals(CrudProperties.SqlLog.Mode.SAFE, properties.getSqlLog().getMode());
                Assertions.assertEquals(CrudNullFieldMode.OMIT, properties.getController().getDefaultNullFieldMode());
                Assertions.assertEquals("STRICT_RESOURCES", properties.getCommand().getCreateScopeFieldValidationMode().name());
                Assertions.assertEquals(java.time.Duration.ofHours(48), properties.getIdempotency().getRetention());
            });
    }

    @ParameterizedTest
    @ValueSource(strings = {"1s", "1000ms", "30m", "PT2S"})
    void 幂等保留时长接受至少一秒的整秒值(String value) {
        CrudProperties properties = new Binder(new MapConfigurationPropertySource(
            Map.of("ent.loom.crud.idempotency.retention", value)))
            .bind("ent.loom.crud", Bindable.of(CrudProperties.class)).get();
        Assertions.assertTrue(properties.getIdempotency().getRetention().getSeconds() >= 1);
        Assertions.assertEquals(0, properties.getIdempotency().getRetention().getNano());
    }

    @Test
    void 无单位时长和大小保留Boot原生单位且幂等校验在转换后执行() {
        CrudProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
            "ent.loom.crud.idempotency.retention", "1000",
            "ent.loom.crud.import-export.retention", "48",
            "ent.loom.crud.import-export.max-file-size", "50")))
            .bind("ent.loom.crud", Bindable.of(CrudProperties.class)).get();
        Assertions.assertEquals(java.time.Duration.ofSeconds(1), properties.getIdempotency().getRetention());
        Assertions.assertEquals(java.time.Duration.ofMillis(48), properties.getImportExport().getRetention());
        Assertions.assertEquals(50, properties.getImportExport().getMaxFileSize().toBytes());
    }

    @Test
    void 根配置与子配置共享绑定实例() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
            .withUserConfiguration(CrudPropertiesConfiguration.class)
            .withPropertyValues("ent.loom.crud.access.identities.customer.id-type=long",
                "ent.loom.crud.example.enabled=true", "ent.loom.crud.idempotency.retention=30m",
                "ent.loom.crud.import-export.retention=2d", "ent.loom.crud.import-export.max-file-size=2MB")
            .run(context -> {
                org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();
                CrudProperties properties = context.getBean(CrudProperties.class);
                Assertions.assertSame(properties.getAccess(), context.getBean(CrudAccessProperties.class));
                Assertions.assertSame(properties.getExample(), context.getBean(CrudExampleProperties.class));
                Assertions.assertTrue(properties.getExample().isEnabled());
                Assertions.assertTrue(properties.getAccess().getIdentities().containsKey("customer"));
                Assertions.assertEquals(java.time.Duration.ofMinutes(30), properties.getIdempotency().getRetention());
                Assertions.assertEquals(java.time.Duration.ofDays(2), properties.getImportExport().getRetention());
                Assertions.assertEquals(2 * 1024 * 1024, properties.getImportExport().getMaxFileSize().toBytes());
            });
    }

    @ParameterizedTest
    @ValueSource(strings = {"enabeld=true", "access.unknown=true", "example.enabeld=true",
        "idempotency.retention-hours=48", "import-export.max-file-bytes=100"})
    void 未知配置与已删除配置应在启动时定位报错(String property) {
        String key = "ent.loom.crud." + property.substring(0, property.indexOf('='));
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
            .withUserConfiguration(CrudPropertiesConfiguration.class)
            .withPropertyValues("ent.loom.crud." + property)
            .run(context -> {
                org.assertj.core.api.Assertions.assertThat(context).hasFailed();
                org.assertj.core.api.Assertions.assertThat(context.getStartupFailure()).hasStackTraceContaining(key);
            });
    }

    @ParameterizedTest
    @ValueSource(strings = {"idempotency.retention=0ms", "idempotency.retention=-1h",
        "idempotency.retention=48", "idempotency.retention=999ms", "idempotency.retention=1500ms",
        "idempotency.retention=PT1.000000001S",
        "import-export.retention=bad", "import-export.retention=0ms", "import-export.max-file-size=0B",
        "import-export.max-file-size=-1MB", "sql-log.sample-rate=-0.1", "sql-log.sample-rate=1.1",
        "sql-log.sample-rate=NaN", "sql-log.sample-rate=Infinity", "relation.max-depth=0", "relation.max-expand-edges=-1"})
    void 显式越界配置不能静默回退默认值(String property) {
        Map<String, String> values = Map.of("ent.loom.crud." + property.substring(0, property.indexOf('=')),
            property.substring(property.indexOf('=') + 1));
        Assertions.assertThrows(org.springframework.boot.context.properties.bind.BindException.class, () ->
            new Binder(new MapConfigurationPropertySource(values)).bind("ent.loom.crud", Bindable.of(CrudProperties.class)));
    }

    @Test
    void defaults_should_bind_global_id_policy() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("ent.loom.crud.defaults.id-policy", "GENERATED");

        CrudProperties properties = new Binder(new MapConfigurationPropertySource(values))
            .bind("ent.loom.crud", Bindable.of(CrudProperties.class))
            .get();

        Assertions.assertEquals(CrudIdPolicy.GENERATED, properties.getDefaults().getIdPolicy());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", " consumer", "consumer "})
    void 固定入口配置拒绝空值及首尾空格(String entry) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("ent.loom.crud.governance.access-entry", entry);
        Assertions.assertThrows(org.springframework.boot.context.properties.bind.BindException.class, () ->
            new Binder(new MapConfigurationPropertySource(values))
                .bind("ent.loom.crud", Bindable.of(CrudProperties.class)));
    }

    @Test
    void controller_should_omit_null_fields_by_default_and_allow_configuration_override() {
        CrudProperties.Controller defaults = new CrudProperties.Controller();
        Assertions.assertEquals(CrudNullFieldMode.OMIT, defaults.getDefaultNullFieldMode());

        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("ent.loom.crud.controller.default-null-field-mode", "include");
        CrudProperties properties = new Binder(new MapConfigurationPropertySource(values))
            .bind("ent.loom.crud", Bindable.of(CrudProperties.class))
            .get();

        Assertions.assertEquals(CrudNullFieldMode.INCLUDE, properties.getController().getDefaultNullFieldMode());
    }

    @Test
    void default_sort_should_keep_release_contract_defaults() {
        CrudProperties.Query.DefaultSort defaultSort = new CrudProperties.Query.DefaultSort();

        Assertions.assertEquals(EnumSet.of(QueryOperation.PAGE, QueryOperation.LIST), defaultSort.getApplyTo());
        Assertions.assertEquals(SortDirection.DESC, defaultSort.getTimeDirection());
        Assertions.assertEquals(SortDirection.DESC, defaultSort.getIdDirection());
        Assertions.assertTrue(defaultSort.isAppendId());
        Assertions.assertTrue(defaultSort.isFallbackToId());
    }

    @Test
    void 默认排序应复制生效操作集合() {
        CrudProperties.Query.DefaultSort defaultSort = new CrudProperties.Query.DefaultSort();
        HashSet<QueryOperation> applyTo = new HashSet<QueryOperation>(Arrays.asList(QueryOperation.PAGE));
        defaultSort.setApplyTo(applyTo);
        applyTo.add(QueryOperation.DETAIL);

        Assertions.assertEquals(EnumSet.of(QueryOperation.PAGE), defaultSort.getApplyTo());
    }

    @Test
    void idempotency_required_ops_should_use_explicit_batch_operations_only() {
        CrudProperties.Idempotency idempotency = new CrudProperties.Idempotency();
        idempotency.setRequiredOps(EnumSet.of(CommandOperation.CREATE_BATCH, CommandOperation.UPDATE_BATCH));

        Assertions.assertEquals(
            EnumSet.of(CommandOperation.CREATE_BATCH, CommandOperation.UPDATE_BATCH),
            idempotency.getRequiredOps()
        );
    }

    @Test
    void import_export_module_switches_should_bind_to_typed_properties() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("ent.loom.crud.import.enabled", "false");
        values.put("ent.loom.crud.export.enabled", "false");

        CrudProperties properties = new Binder(new MapConfigurationPropertySource(values))
            .bind("ent.loom.crud", Bindable.of(CrudProperties.class))
            .get();

        Assertions.assertFalse(properties.getImport().isEnabled());
        Assertions.assertFalse(properties.getExport().isEnabled());
    }

    @Test
    void 实体输入契约由注解解析() {
        var declarations = new CrudEntityDeclarations(java.util.List.of(CustomerContract.class));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("displayName", "email"), declarations.required().get("customer"));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("id"), declarations.forbidden().get("customer"));
    }

    @com.entloom.crud.annotations.EntCrudEntity(name = "customer", requiredCreateFields = {"displayName", "email"}, forbiddenUpdateFields = "id")
    private static class CustomerContract { }

}
