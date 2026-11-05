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
    void 实体输入契约由独立文件解析() {
        var resource = new org.springframework.core.io.ByteArrayResource(
            "crud: {contracts: {create: {input-required-fields: [displayName, email]}, update: {forbidden-fields: [id]}}}".getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return "customer.yml"; }
        };
        var declarations = new CrudEntityDeclarations(new com.entloom.declaration.EntityDeclarations(resource));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("displayName", "email"), declarations.required().get("customer"));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("id"), declarations.forbidden().get("customer"));
    }

}
