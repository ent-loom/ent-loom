package com.entloom.crud.core.runtime.validation;

import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** required 类型语义回归。 */
class RequiredFieldValidatorTest {
    private final RequiredFieldValidator validator = new RequiredFieldValidator();

    @Test
    void should_reject_blank_text_and_empty_container_with_label_message() {
        ValidationException text = Assertions.assertThrows(
            ValidationException.class,
            () -> validator.validateCreateValues(values("name", "  "), meta(required("name", String.class, "商品名称")))
        );
        Assertions.assertEquals("商品名称不能为空", text.getMessage());

        Assertions.assertThrows(
            ValidationException.class,
            () -> validator.validateCreateValues(
                values("items", Collections.emptyList()),
                meta(required("items", java.util.List.class, "商品明细"))
            )
        );
        Assertions.assertThrows(
            ValidationException.class,
            () -> validator.validateCreateValues(values("items", new String[0]), meta(required("items", String[].class, "商品明细")))
        );
    }

    @Test
    void should_accept_zero_false_and_non_empty_container() {
        validator.validateCreateValues(values("number", Integer.valueOf(0)), meta(required("number", Integer.class, "数量")));
        validator.validateCreateValues(values("enabled", Boolean.FALSE), meta(required("enabled", Boolean.class, "启用状态")));
        validator.validateCreateValues(values("items", Arrays.asList("A")), meta(required("items", java.util.List.class, "商品明细")));
    }

    @Test
    void should_reject_missing_primitive_but_accept_explicit_false() {
        EntityFieldMeta field = new EntityFieldMeta(
            "enabled", Boolean.TYPE, "enabled", false, false, true, true,
            true, false, false, "启用状态", true
        );
        Set<String> absent = new HashSet<String>();
        Assertions.assertThrows(
            ValidationException.class,
            () -> validator.validateCreateEntity(new PrimitiveEntity(), meta(field), absent)
        );

        PrimitiveEntity entity = new PrimitiveEntity();
        Set<String> present = new HashSet<String>();
        present.add("enabled");
        validator.validateCreateEntity(entity, meta(field), present);
    }

    @Test
    void should_validate_resolved_explicit_id_after_command_normalization() {
        EntityFieldMeta id = required("id", Long.class, "商品 ID");
        validator.validateCreateValues(Collections.<String, Object>emptyMap(), meta(id), Long.valueOf(1001L));
    }

    @Test
    void should_skip_database_generated_id() {
        EntityFieldMeta id = required("id", Long.class, "商品 ID");
        validator.validateCreateValues(Collections.<String, Object>emptyMap(), meta(id, EntityIdPolicy.GENERATED));
    }

    private EntityFieldMeta required(String name, Class<?> type, String label) {
        return new EntityFieldMeta(name, type, name, true, false, true, true, true, false, false, label, true);
    }

    private Map<String, Object> values(String name, Object value) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put(name, value);
        return values;
    }

    private EntityMeta meta(EntityFieldMeta field) {
        return meta(field, EntityIdPolicy.EXPLICIT);
    }

    private EntityMeta meta(EntityFieldMeta field, EntityIdPolicy idPolicy) {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put(field.getFieldName(), field);
        return new EntityMeta(
            Object.class,
            new ResourceDescriptor(Object.class, "test", "test", Collections.<String>emptyList()),
            "test",
            "id",
            idPolicy,
            null,
            fields
        );
    }

    private static final class PrimitiveEntity {
        private boolean enabled;
    }
}
