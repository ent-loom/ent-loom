package com.entloom.meta.adapter.ddl;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.ddl.annotations.EntDdlEntity;
import com.entloom.ddl.annotations.EntDdlField;
import com.entloom.ddl.annotations.EntDdlIndex;
import com.entloom.ddl.api.DdlEntityMetadata;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 实体级索引的属性映射与输入校验。 */
class EntityIndexContractTest {
    @Test
    void shouldMapPropertiesInOrderAndPreserveNativeExpression() {
        DdlEntityMetadata entity = load(IndexedEntity.class);
        assertEquals(3, entity.indexes().size());
        assertEquals(Arrays.asList("account_code", "tenant_id"), entity.indexes().get(0).fields());
        assertTrue(entity.indexes().get(0).unique());
        assertEquals(Collections.singletonList("tenant_id"), entity.indexes().get(1).fields());
        assertTrue(entity.indexes().get(2).fields().isEmpty());
        assertEquals("lower(account_code)", entity.indexes().get(2).expression());
    }

    @Test
    void shouldRejectInvalidIndexDeclarations() {
        for (Class<?> type : Arrays.<Class<?>>asList(
                EmptyIndex.class, ConflictingIndex.class, UnknownProperty.class,
                PhysicalColumn.class, NonPersistentProperty.class, BlankProperty.class,
                DuplicateProperty.class)) {
            assertThrows(RuntimeException.class, () -> load(type), type.getSimpleName());
        }
    }

    private DdlEntityMetadata load(Class<?> type) {
        return new MetaDdlAdapter(Collections.<Class<?>>singletonList(type)).models().get(0);
    }

    private static class BaseEntity {
        Long id;
        Long tenantId;
        @EntDdlField(column = "account_code")
        String accountCode;
        @EntDdlField(persisted = OptionalBoolean.FALSE)
        String displayLabel;
    }

    @EntDdlEntity
    @EntDdlIndex(fields = {"accountCode", "tenantId"}, unique = OptionalBoolean.TRUE)
    @EntDdlIndex(fields = "tenantId")
    @EntDdlIndex(expression = "lower(account_code)")
    private static class IndexedEntity extends BaseEntity {
    }

    @EntDdlEntity
    @EntDdlIndex
    private static class EmptyIndex extends BaseEntity {
    }

    @EntDdlEntity
    @EntDdlIndex(fields = "accountCode", expression = "lower(account_code)")
    private static class ConflictingIndex extends BaseEntity {
    }

    @EntDdlEntity
    @EntDdlIndex(fields = "missing")
    private static class UnknownProperty extends BaseEntity {
    }

    @EntDdlEntity
    @EntDdlIndex(fields = "account_code")
    private static class PhysicalColumn extends BaseEntity {
    }

    @EntDdlEntity
    @EntDdlIndex(fields = "displayLabel")
    private static class NonPersistentProperty extends BaseEntity {
    }

    @EntDdlEntity
    @EntDdlIndex(fields = {"accountCode", " "})
    private static class BlankProperty extends BaseEntity {
    }

    @EntDdlEntity
    @EntDdlIndex(fields = {"accountCode", "accountCode"})
    private static class DuplicateProperty extends BaseEntity {
    }
}
