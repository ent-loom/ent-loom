package com.entloom.crud.engine.jdbc.dao;

import com.entloom.crud.core.capability.dao.RowConstraint;
import com.entloom.crud.core.capability.dao.RowConstraintNormalizer;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JdbcEntityPredicateCompilerTest {
    @Test
    void should_compile_primary_key_scope_and_logic_delete_in_stable_order() {
        EntityMeta meta = meta();
        RowConstraint scope = RowConstraintNormalizer.normalize(
            RowConstraint.and(
                RowConstraint.eq("schoolId", "198"),
                RowConstraint.in("tenantId", Arrays.asList("tenant-a", "tenant-b"))
            ),
            meta
        );

        JdbcEntityPredicateCompiler.CompiledWhere where = new JdbcEntityPredicateCompiler()
            .byId(meta, "id", 7L, scope);

        Assertions.assertEquals(
            "id = ? and school_id = ? and tenant_id in (?,?) and is_deleted = ?",
            where.getSql()
        );
        Assertions.assertEquals(
            Arrays.<Object>asList(7L, 198L, "tenant-a", "tenant-b", 0),
            where.getArgs()
        );
    }

    @Test
    void empty_in_is_deterministically_false_without_an_empty_in_clause() {
        EntityMeta meta = meta();
        RowConstraint scope = RowConstraintNormalizer.normalize(
            RowConstraint.in("schoolId", Collections.emptyList()),
            meta
        );

        JdbcEntityPredicateCompiler.CompiledWhere where = new JdbcEntityPredicateCompiler()
            .byId(meta, "id", 7L, scope);

        Assertions.assertEquals("id = ? and 1 = 0 and is_deleted = ?", where.getSql());
        Assertions.assertEquals(Arrays.<Object>asList(7L, 0), where.getArgs());
    }

    @Test
    void unsupported_expression_unknown_field_and_parameter_overflow_fail_closed() {
        EntityMeta meta = meta();
        JdbcEntityPredicateCompiler compiler = new JdbcEntityPredicateCompiler(3);

        Assertions.assertThrows(
            ValidationException.class,
            () -> compiler.byId(meta, "id", 7L, RowConstraint.or(RowConstraint.eq("schoolId", 198L)))
        );
        Assertions.assertThrows(
            ValidationException.class,
            () -> compiler.byId(meta, "id", 7L, RowConstraint.eq("unknown", "x"))
        );
        Assertions.assertThrows(
            ValidationException.class,
            () -> compiler.byId(meta, "id", 7L, RowConstraint.in("tenantId", Arrays.asList("a", "b")))
        );
    }

    private EntityMeta meta() {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", new EntityFieldMeta("id", Long.class, "id", false, false, true, true));
        fields.put(
            "schoolId",
            new EntityFieldMeta("schoolId", Long.class, "school_id", false, false, true, true, false, true, false)
        );
        fields.put(
            "tenantId",
            new EntityFieldMeta("tenantId", String.class, "tenant_id", false, false, true, true, false, true, false)
        );
        fields.put("isDeleted", new EntityFieldMeta("isDeleted", Integer.class, "is_deleted", false, false, true, true));
        return new EntityMeta(
            SampleEntity.class,
            new ResourceDescriptor(SampleEntity.class, "sample", "test-service", Collections.<String>emptyList()),
            "t_sample",
            "id",
            EntityIdPolicy.EXPLICIT,
            "isDeleted",
            Integer.valueOf(0),
            Integer.valueOf(1),
            fields
        );
    }

    private static final class SampleEntity {
    }
}
