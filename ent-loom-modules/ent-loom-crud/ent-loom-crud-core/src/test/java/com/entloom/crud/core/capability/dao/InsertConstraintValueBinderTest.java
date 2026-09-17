package com.entloom.crud.core.capability.dao;

import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class InsertConstraintValueBinderTest {
    @Test
    void equality_and_single_in_should_fill_normalized_scope_value() {
        Map<String, Object> values = new LinkedHashMap<String, Object>();

        Map<String, Object> equality = InsertConstraintValueBinder.bind(
            RowConstraint.eq("schoolId", "12"), meta(), values
        );
        Assertions.assertEquals(Long.valueOf(12L), equality.get("schoolId"));
        Assertions.assertTrue(values.isEmpty());

        Map<String, Object> singleIn = InsertConstraintValueBinder.bind(
            RowConstraint.in("schoolId", Arrays.asList("12", "12")), meta(), values
        );
        Assertions.assertEquals(Long.valueOf(12L), singleIn.get("schoolId"));
    }

    @Test
    void multi_in_should_require_and_validate_entity_scope_value() {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("schoolId", "2");

        Map<String, Object> result = InsertConstraintValueBinder.bind(
            RowConstraint.in("schoolId", Arrays.asList("1", "2")), meta(), values
        );
        Assertions.assertEquals(Long.valueOf(2L), result.get("schoolId"));

        values.remove("schoolId");
        Assertions.assertThrows(
            ValidationException.class,
            () -> InsertConstraintValueBinder.bind(RowConstraint.in("schoolId", Arrays.asList(1L, 2L)), meta(), values)
        );
    }

    @Test
    void unsupported_expression_null_and_conflict_should_fail_closed() {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("schoolId", 9L);

        Assertions.assertThrows(
            ValidationException.class,
            () -> InsertConstraintValueBinder.bind(RowConstraint.or(RowConstraint.eq("schoolId", 9L)), meta(), values)
        );
        Assertions.assertThrows(
            ValidationException.class,
            () -> InsertConstraintValueBinder.bind(RowConstraint.predicate("schoolId", RowConstraintOperator.GT, 1L), meta(), values)
        );
        Assertions.assertThrows(
            ValidationException.class,
            () -> InsertConstraintValueBinder.bind(RowConstraint.eq("schoolId", null), meta(), values)
        );
        Assertions.assertThrows(
            ValidationException.class,
            () -> InsertConstraintValueBinder.bind(RowConstraint.eq("schoolId", 10L), meta(), values)
        );
        Assertions.assertThrows(
            ValidationException.class,
            () -> InsertConstraintValueBinder.bind(RowConstraint.in("schoolId", Collections.emptyList()), meta(), values)
        );
    }

    @Test
    void scope_should_be_a_deep_immutable_snapshot() {
        java.util.List<Object> ids = new java.util.ArrayList<Object>(Collections.<Object>singletonList(1L));
        RowConstraint constraint = RowConstraint.in("schoolId", ids);
        ids.add(2L);
        Assertions.assertEquals(Collections.<Object>singletonList(1L), constraint.getValues());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> constraint.getValues().add(2L));

        RowConstraint combined = RowConstraint.and(constraint, RowConstraint.eq("tenantId", "tenant-a"));
        Assertions.assertEquals(2, combined.getChildren().size());
        Assertions.assertEquals(1, constraint.getValues().size());
    }

    private EntityMeta meta() {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", new EntityFieldMeta("id", Long.class, "id", false, false, true, true, true, false, true));
        fields.put("schoolId", new EntityFieldMeta("schoolId", Long.class, "school_id", false, false, true, true, false, true, false));
        fields.put("tenantId", new EntityFieldMeta("tenantId", String.class, "tenant_id", false, false, true, true, false, true, false));
        fields.put("name", new EntityFieldMeta("name", String.class, "name", true, false, true, true));
        fields.put("isDeleted", new EntityFieldMeta("isDeleted", Integer.class, "is_deleted", false, false, true, true, false, false, true));
        return new EntityMeta(
            TestEntity.class,
            new ResourceDescriptor(TestEntity.class, "test", "test", Collections.<String>emptyList()),
            "t_test",
            "id",
            EntityIdPolicy.EXPLICIT,
            "isDeleted",
            Integer.valueOf(0),
            Integer.valueOf(1),
            fields
        );
    }

    private static final class TestEntity {
    }
}
