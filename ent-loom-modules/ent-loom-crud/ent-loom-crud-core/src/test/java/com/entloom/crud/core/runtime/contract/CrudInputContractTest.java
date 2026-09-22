package com.entloom.crud.core.runtime.contract;

import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.runtime.validation.RequiredFieldValidator;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CrudInputContractTest {
    @Test
    void configured_fields_should_match_resource_code_simple_name_and_custom_alias() {
        EntityMeta meta = meta(EntityIdPolicy.EXPLICIT, new ResourceDescriptor(
            TestEntity.class,
            "customer_profile",
            "customer-service",
            Collections.singletonList("customer")
        ));

        CrudInputContract byResource = new CrudInputContract(
            fields("customer_profile", "name"),
            Collections.<String, java.util.List<String>>emptyMap()
        );
        CrudInputContract bySimpleName = new CrudInputContract(
            fields("TestEntity", "name"),
            Collections.<String, java.util.List<String>>emptyMap()
        );
        CrudInputContract byAlias = new CrudInputContract(
            fields("customer", "name"),
            Collections.<String, java.util.List<String>>emptyMap()
        );

        Assertions.assertEquals(Collections.singleton("name"), byResource.resolveCreateRequiredFields(meta));
        Assertions.assertEquals(Collections.singleton("name"), bySimpleName.resolveCreateRequiredFields(meta));
        Assertions.assertEquals(Collections.singleton("name"), byAlias.resolveCreateRequiredFields(meta));
    }

    @Test
    void unconfigured_resource_should_not_infer_create_required_fields() {
        LinkedHashMap<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", new EntityFieldMeta("id", Long.class, "id", false, false, true, true));
        fields.put("name", new EntityFieldMeta(
            "name", String.class, "name", true, false, true, true, true, false, false,
            "名称", false, "默认名称"
        ));
        fields.put("schoolId", new EntityFieldMeta(
            "schoolId", Long.class, "school_id", false, false, true, true, false, true, false
        ));
        fields.put("active", new EntityFieldMeta("active", Boolean.class, "active", false, false, true, true));
        EntityMeta meta = new EntityMeta(
            TestEntity.class,
            new ResourceDescriptor(TestEntity.class, "customer_profile", "customer-service", null),
            "customer_profile",
            "id",
            EntityIdPolicy.GENERATED,
            null,
            fields
        );

        Set<String> required = CrudInputContract.empty().resolveCreateRequiredFields(meta);

        Assertions.assertTrue(required.isEmpty());
    }

    @Test
    void update_should_reject_forbidden_field_even_when_value_is_null() {
        EntityMeta meta = meta(EntityIdPolicy.EXPLICIT, new ResourceDescriptor(
            TestEntity.class, "customer_profile", "customer-service", null
        ));
        CrudInputContract contract = new CrudInputContract(
            Collections.<String, java.util.List<String>>emptyMap(),
            fields("customer_profile", "name")
        );
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("name", null);

        IllegalArgumentException exception = Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> contract.validateUpdate(values, meta)
        );

        Assertions.assertEquals("禁止修改字段: customer_profile.name", exception.getMessage());
    }

    @Test
    void update_should_treat_forbidden_id_as_target_locator_not_changed_field() {
        EntityMeta meta = meta(EntityIdPolicy.EXPLICIT, new ResourceDescriptor(
            TestEntity.class, "customer_profile", "customer-service", null
        ));
        CrudInputContract contract = new CrudInputContract(
            Collections.<String, java.util.List<String>>emptyMap(),
            fields("customer_profile", "id")
        );
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("id", Long.valueOf(11L));
        values.put("name", "Alice");

        Assertions.assertDoesNotThrow(() -> contract.validateUpdate(values, meta));
    }

    @Test
    void configured_unknown_field_should_fail_at_validation_boundary() {
        EntityMeta meta = meta(EntityIdPolicy.EXPLICIT, new ResourceDescriptor(
            TestEntity.class, "customer_profile", "customer-service", null
        ));
        CrudInputContract contract = new CrudInputContract(
            fields("customer_profile", "unknown"),
            Collections.<String, java.util.List<String>>emptyMap()
        );

        ValidationException exception = Assertions.assertThrows(
            ValidationException.class,
            () -> new RequiredFieldValidator(contract).validateCreateValues(
                Collections.<String, Object>emptyMap(), meta
            )
        );

        Assertions.assertEquals("创建契约字段不存在: customer_profile.unknown", exception.getMessage());
    }

    private static EntityMeta meta(EntityIdPolicy idPolicy, ResourceDescriptor descriptor) {
        LinkedHashMap<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", new EntityFieldMeta("id", Long.class, "id", false, false, true, true));
        fields.put("name", new EntityFieldMeta("name", String.class, "name", true, false, true, true));
        return new EntityMeta(TestEntity.class, descriptor, "customer_profile", "id", idPolicy, null, fields);
    }

    private static Map<String, java.util.List<String>> fields(String key, String... fieldNames) {
        Map<String, java.util.List<String>> result = new LinkedHashMap<String, java.util.List<String>>();
        result.put(key, Arrays.asList(fieldNames));
        return result;
    }

    private static final class TestEntity {
    }
}
