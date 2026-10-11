package com.entloom.crud.core.runtime.model;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CrudScopeFieldRulesTest {
    @Test
    void 候选仅匹配已有字段并保留展示与字段能力() {
        CrudRuntimeModel original = model(Address.class);
        CrudScopeFieldRules rules = new CrudScopeFieldRules(Arrays.asList("customerId", "merchantId"), Collections.emptyMap());
        CrudRuntimeEntityModel entity = rules.apply(original).getEntity(Address.class);
        assertTrue(entity.getField("customerId").isScopeField());
        assertFalse(entity.getField("customerId").isWritable());
        assertEquals("所属顾客", entity.getField("customerId").getLabel());
        assertTrue(entity.getField("customerId").isFilterable());
        assertTrue(entity.getField("address").isWritable());
        assertFalse(entity.getField("id").isScopeField());
        assertNull(entity.getField("merchantId"));
        assertFalse(original.getEntity(Address.class).getField("customerId").isScopeField());
    }

    @Test
    void 配置或注解排除后保留普通关联字段能力() {
        CrudScopeFieldRules configured = new CrudScopeFieldRules(Collections.singletonList("customerId"),
            Collections.singletonMap("address", Collections.singletonList("customerId")));
        assertFalse(configured.apply(model(Address.class)).getEntity(Address.class).getField("customerId").isScopeField());
        assertTrue(configured.apply(model(Address.class)).getEntity(Address.class).getField("customerId").isWritable());
        CrudScopeFieldRules defaults = new CrudScopeFieldRules(Collections.singletonList("customerId"), Collections.emptyMap());
        assertFalse(defaults.apply(model(Reference.class)).getEntity(Reference.class).getField("customerId").isScopeField());
    }

    @Test
    void 注解显式归属字段仍落实保护且与排除冲突拒绝() {
        CrudScopeFieldRules empty = new CrudScopeFieldRules();
        assertTrue(empty.apply(model(Explicit.class)).getEntity(Explicit.class).getField("customerId").isScopeField());
        assertThrows(ValidationException.class, () -> empty.apply(model(Conflict.class)));
        assertThrows(ValidationException.class, () -> new CrudScopeFieldRules(Collections.emptyList(),
            Collections.singletonMap("address", Collections.singletonList("customerId"))).apply(model(Explicit.class)));
    }

    @Test
    void 错误例外和重复候选不能被静默忽略() {
        assertThrows(ValidationException.class, () -> new CrudScopeFieldRules(Arrays.asList("customerId", "customerId"), Collections.emptyMap()));
        assertThrows(ValidationException.class, () -> new CrudScopeFieldRules(Collections.emptyList(),
            Collections.singletonMap("unknown", Collections.singletonList("customerId"))).apply(model(Address.class)));
        assertThrows(ValidationException.class, () -> new CrudScopeFieldRules(Collections.emptyList(),
            Collections.singletonMap("address", Collections.singletonList("unknown"))).apply(model(Address.class)));
        assertThrows(ValidationException.class, () -> new CrudScopeFieldRules(Collections.singletonList("id"),
            Collections.emptyMap()).apply(model(Address.class)));
    }

    private CrudRuntimeModel model(Class<?> type) {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<>();
        fields.put("id", new EntityFieldMeta("id", Long.class, "id", false, false, true, true));
        fields.put("customerId", new EntityFieldMeta("customerId", Long.class, "customer_id", false, false,
            true, true, true, false, false, "所属顾客", false, null));
        fields.put("address", new EntityFieldMeta("address", String.class, "address", false, false, true, true));
        EntityMeta meta = new EntityMeta(type, new ResourceDescriptor(type, "address", null, Collections.emptySet()),
            "address", "id", null, fields);
        return CrudRuntimeModel.from(Collections.singletonList(meta), Collections.emptyList());
    }

    private static class Address { }
    @EntCrudEntity(excludeScopeFields = "customerId")
    private static class Reference { }
    @EntCrudEntity(scopeFields = "customerId")
    private static class Explicit { }
    @EntCrudEntity(scopeFields = "customerId", excludeScopeFields = "customerId")
    private static class Conflict { }
}
