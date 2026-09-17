package com.entloom.crud.core.capability.dao;

import com.entloom.crud.core.exception.ValidationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EntityTypeTest {
    @Test
    void should_keep_entity_and_id_types_together() {
        EntityType<TestEntity, Long> type = EntityType.of(TestEntity.class, Long.class);
        Assertions.assertEquals(TestEntity.class, type.getEntityClass());
        Assertions.assertEquals(Long.class, type.getIdClass());
    }

    @Test
    void should_reject_missing_runtime_types() {
        Assertions.assertThrows(ValidationException.class, () -> EntityType.of(null, Long.class));
        Assertions.assertThrows(ValidationException.class, () -> EntityType.of(TestEntity.class, null));
        Assertions.assertThrows(ValidationException.class, () -> EntityAccessScope.of(null));
    }

    private static final class TestEntity {
    }
}
