package com.entloom.crud.core.capability.command.patch;

import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class UpdatePatchWriteCommandFactoryTest {
    @Test
    void declared_writable_field_should_create_command() {
        WriteCommand<Map<String, Object>> command = UpdatePatchWriteCommandFactory.create(
            patch(set("name"), set("name"), values("name", "A")),
            meta()
        );

        Assertions.assertEquals(7L, command.getId());
        Assertions.assertEquals("A", command.getValues().get("name"));
    }

    @Test
    void undeclared_delegate_value_should_be_rejected() {
        Map<String, Object> values = values("name", "A");
        values.put("unknown", "forged");

        Assertions.assertThrows(ValidationException.class, () -> UpdatePatchWriteCommandFactory.create(
            patch(set("name"), set("name"), values),
            meta()
        ));
    }

    @Test
    void id_unknown_and_read_only_fields_should_be_rejected() {
        for (String field : Arrays.asList("id", "unknown", "createdAt")) {
            Assertions.assertThrows(ValidationException.class, () -> UpdatePatchWriteCommandFactory.create(
                patch(set(field), set(field), values(field, "forged")),
                meta()
            ));
        }
    }

    @Test
    void 构造器保留显式清空与版本且生成不可变快照() {
        var builder = UpdatePatch.builder(TestEntity.class, 7L).set("name", null).expectedVersion(3L);
        var patch = builder.build();
        builder.set("name", "后续值").set("other", 1);
        var command = UpdatePatchWriteCommandFactory.create(patch, meta());
        Assertions.assertEquals(7L, command.getId());
        Assertions.assertTrue(command.getValues().containsKey("name"));
        Assertions.assertNull(command.getValues().get("name"));
        Assertions.assertEquals(3L, patch.getExpectedVersion());
        Assertions.assertEquals(set("name"), patch.getPresentFields());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> patch.getValuesForDelegate().put("name", "篡改"));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> patch.getPresentFields().add("other"));
    }

    @Test
    void 构造器仍由内核拒绝主键未知只读与版本字段() {
        for (String field : Arrays.asList("id", "unknown", "createdAt", "version")) {
            var patch = UpdatePatch.builder(TestEntity.class, 7L).set(field, "非法写入").build();
            Assertions.assertThrows(ValidationException.class, () -> UpdatePatchWriteCommandFactory.create(patch, meta()));
        }
        Assertions.assertThrows(ValidationException.class, () -> UpdatePatch.builder(TestEntity.class, 7L).build());
        Assertions.assertThrows(ValidationException.class, () -> UpdatePatch.builder(TestEntity.class, 7L).set(" ", 1));
    }

    private UpdatePatch<TestEntity> patch(
        Set<String> presentFields,
        Set<String> persistableFields,
        Map<String, Object> values
    ) {
        return new UpdatePatch<TestEntity>() {
            public Class<TestEntity> getEntityType() { return TestEntity.class; }
            public TestEntity getEntity() { return null; }
            public Object getId() { return 7L; }
            public Long getLongId() { return 7L; }
            public Set<String> getPresentFields() { return presentFields; }
            public Set<String> getPersistableFields() { return persistableFields; }
            public Map<String, Object> getValuesForDelegate() { return values; }
            @SuppressWarnings("unchecked") public <V> V get(String field) { return (V) values.get(field); }
            public <V> V get(String field, Class<V> targetType) { return targetType.cast(values.get(field)); }
        };
    }

    private EntityMeta meta() {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", field("id", true, false));
        fields.put("name", field("name", true, false));
        fields.put("createdAt", field("createdAt", false, true));
        return new EntityMeta(
            TestEntity.class,
            new ResourceDescriptor(TestEntity.class, "testEntity", null, Collections.<String>emptyList()),
            "test_entity",
            "id",
            null,
            fields
        );
    }

    private EntityFieldMeta field(String name, boolean writable, boolean immutable) {
        return new EntityFieldMeta(name, String.class, name, true, false, true, true, writable, false, immutable);
    }

    private Set<String> set(String value) {
        return new LinkedHashSet<String>(Collections.singleton(value));
    }

    private Map<String, Object> values(String field, Object value) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put(field, value);
        return values;
    }

    private static final class TestEntity {
    }
}
