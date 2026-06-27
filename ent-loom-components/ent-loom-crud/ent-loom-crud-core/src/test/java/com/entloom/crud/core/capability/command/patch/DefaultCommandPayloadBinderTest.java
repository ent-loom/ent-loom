package com.entloom.crud.core.capability.command.patch;

import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultCommandPayloadBinderTest {
    private final DefaultCommandPayloadBinder binder = new DefaultCommandPayloadBinder();

    @Test
    void should_bind_update_patch_with_three_state_semantics() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", "12");
        payload.put("name", null);
        payload.put("age", "18");
        payload.put("unknown", "ignored");

        EntityPatch<TestEntity> patch = binder.bindEntityPatch(payload, TestEntity.class, meta());

        Assertions.assertEquals(12L, patch.getLongId().longValue());
        Assertions.assertTrue(patch.hasField("name"));
        Assertions.assertNull(patch.get("name", String.class));
        Assertions.assertTrue(patch.hasField("age"));
        Assertions.assertEquals(18, patch.get("age", Integer.class).intValue());
        Assertions.assertFalse(patch.hasField("remark"));
        Assertions.assertFalse(patch.hasField("unknown"));
        Assertions.assertFalse(patch.isPersistableField("id"));
        Assertions.assertTrue(patch.isPersistableField("name"));
        Assertions.assertFalse(patch.getValuesForDelegate().containsKey("id"));
        Assertions.assertTrue(patch.getValuesForDelegate().containsKey("name"));
        Assertions.assertTrue(patch.getValuesForDelegate().containsKey("age"));
        Assertions.assertEquals(Integer.valueOf(18), patch.getEntity().age);
    }

    @Test
    void should_bind_blank_string_to_null_for_nullable_non_string_fields() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", "");
        payload.put("age", " ");
        payload.put("name", "");

        EntityPatch<TestEntity> patch = binder.bindEntityPatch(payload, TestEntity.class, meta());

        Assertions.assertNull(patch.getLongId());
        Assertions.assertNull(patch.getEntity().id);
        Assertions.assertNull(patch.getEntity().age);
        Assertions.assertEquals("", patch.getEntity().name);
    }

    @Test
    void should_bind_entity_blank_string_to_null_for_nullable_non_string_fields() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", "");
        payload.put("age", " ");
        payload.put("name", "");

        TestEntity entity = binder.bindEntity(payload, TestEntity.class, meta());

        Assertions.assertNull(entity.id);
        Assertions.assertNull(entity.age);
        Assertions.assertEquals("", entity.name);
    }

    @Test
    void values_for_delegate_should_be_immutable() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1L);
        payload.put("name", "A");

        EntityPatch<TestEntity> patch = binder.bindEntityPatch(payload, TestEntity.class, meta());

        Assertions.assertThrows(UnsupportedOperationException.class, () -> patch.getValuesForDelegate().put("name", "B"));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> patch.getPresentFields().add("remark"));
    }

    @Test
    void should_keep_additional_present_fields_out_of_delegate_values() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1L);
        payload.put("name", "A");
        payload.put("mediaUrls", Collections.singletonList("a.png"));

        EntityPatch<TestEntity> patch = binder.bindEntityPatch(
            payload,
            TestEntity.class,
            meta(),
            Collections.singleton("mediaUrls")
        );

        Assertions.assertTrue(patch.hasField("mediaUrls"));
        Assertions.assertEquals(Collections.singletonList("a.png"), patch.get("mediaUrls", Object.class));
        Assertions.assertFalse(patch.isPersistableField("mediaUrls"));
        Assertions.assertFalse(patch.getValuesForDelegate().containsKey("mediaUrls"));
    }

    @Test
    void should_bind_additional_entity_list_field_to_typed_children() {
        Map<String, Object> child = new LinkedHashMap<String, Object>();
        child.put("url", "a.png");
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1L);
        payload.put("items", Collections.singletonList(child));

        EntityPatch<TestEntity> patch = binder.bindEntityPatch(
            payload,
            TestEntity.class,
            meta(),
            Collections.singleton("items")
        );

        Assertions.assertTrue(patch.hasField("items"));
        Assertions.assertFalse(patch.isPersistableField("items"));
        Assertions.assertEquals(1, patch.getEntity().items.size());
        Assertions.assertEquals("a.png", patch.getEntity().items.get(0).url);
        Assertions.assertFalse(patch.getValuesForDelegate().containsKey("items"));
    }

    @Test
    void should_bind_entity_fields_even_when_not_in_meta() {
        Map<String, Object> child = new LinkedHashMap<String, Object>();
        child.put("url", "a.png");
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1L);
        payload.put("items", Collections.singletonList(child));
        payload.put("unknown", "ignored");

        TestEntity entity = binder.bindEntity(payload, TestEntity.class, meta());

        Assertions.assertEquals(Long.valueOf(1L), entity.id);
        Assertions.assertEquals(1, entity.items.size());
        Assertions.assertEquals("a.png", entity.items.get(0).url);
    }

    private EntityMeta meta() {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", field("id", Long.class, "id"));
        fields.put("name", field("name", String.class, "name"));
        fields.put("age", field("age", Integer.class, "age"));
        fields.put("remark", field("remark", String.class, "remark"));
        return new EntityMeta(
            TestEntity.class,
            new ResourceDescriptor(TestEntity.class, "testEntity", "test", Collections.<String>emptyList()),
            "test_entity",
            "id",
            null,
            fields
        );
    }

    private EntityFieldMeta field(String name, Class<?> type, String column) {
        return new EntityFieldMeta(name, type, column, true, false, true, true);
    }

    public static class TestEntity {
        public Long id;
        public String name;
        public Integer age;
        public String remark;
        public List<TestChild> items;
    }

    public static class TestChild {
        public String url;
    }
}
