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

class CrudOwnershipRulesTest {
    @Test
    void 默认匹配已有关系字段并保护多重归属且保留其他字段能力() {
        CrudRuntimeModel original = model(Address.class);
        CrudRuntimeEntityModel entity = rules(Collections.emptyMap()).apply(original).getEntity(Address.class);
        assertTrue(entity.getField("customerId").isScopeField());
        assertTrue(entity.getField("merchantId").isScopeField());
        assertFalse(entity.getField("customerId").isWritable());
        assertFalse(entity.getField("merchantId").isWritable());
        assertEquals("所属顾客", entity.getField("customerId").getLabel());
        assertTrue(entity.getField("customerId").isFilterable());
        assertTrue(entity.getField("address").isWritable());
        assertFalse(entity.getField("id").isScopeField());
        assertFalse(original.getEntity(Address.class).getField("customerId").isScopeField());
    }

    @Test
    void 显式列表整体替换而空列表关闭推导并保留只读约束() {
        CrudRuntimeEntityModel selected = rules(Collections.singletonMap("address",
            ownership(Collections.singletonList("merchant")))).apply(model(Address.class)).getEntity(Address.class);
        assertFalse(selected.getField("customerId").isScopeField());
        assertTrue(selected.getField("customerId").isWritable());
        assertTrue(selected.getField("merchantId").isScopeField());
        CrudRuntimeEntityModel disabled = rules(Collections.singletonMap("address",
            ownership(Collections.emptyList()))).apply(model(Address.class)).getEntity(Address.class);
        assertFalse(disabled.getField("customerId").isScopeField());
        assertFalse(disabled.getField("merchantId").isScopeField());
        assertFalse(disabled.getField("readOnly").isWritable());
    }

    @Test
    void 实体映射参与缺省和显式推导且配置冻结后不受来源变更影响() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("customer", "merchantId");
        Map<String, CrudOwnershipRules.Ownership> declarations = new LinkedHashMap<>();
        declarations.put("address", new CrudOwnershipRules.Ownership(null, mappings));
        CrudOwnershipRules rules = rules(declarations);
        mappings.clear();
        declarations.clear();
        CrudRuntimeEntityModel entity = rules.apply(model(Address.class)).getEntity(Address.class);
        assertFalse(entity.getField("customerId").isScopeField());
        assertTrue(entity.getField("merchantId").isScopeField());
        assertTrue(rules(Collections.singletonMap("address", new CrudOwnershipRules.Ownership(
            Collections.singletonList("customer"), Collections.singletonMap("customer", "merchantId"))))
            .apply(model(Address.class)).getEntity(Address.class).getField("merchantId").isScopeField());
    }

    @Test
    void 无匹配字段不补造属性且显式引用缺失字段拒绝() {
        Map<String, CrudOwnershipRules.Relation> relations = Collections.singletonMap("unknown",
            new CrudOwnershipRules.Relation("unknownId", Long.class));
        assertFalse(new CrudOwnershipRules(relations, Collections.emptyMap()).apply(model(Address.class))
            .getEntity(Address.class).getField("customerId").isScopeField());
        assertThrows(ValidationException.class, () -> new CrudOwnershipRules(relations, Collections.singletonMap(
            "address", ownership(Collections.singletonList("unknown")))).apply(model(Address.class)));
    }

    @Test
    void 注解显式范围保护不能通过空归属列表解除() {
        assertTrue(new CrudOwnershipRules().apply(model(Explicit.class)).getEntity(Explicit.class)
            .getField("customerId").isScopeField());
        assertTrue(rules(Collections.singletonMap("address", ownership(Collections.emptyList())))
            .apply(model(Explicit.class)).getEntity(Explicit.class).getField("customerId").isScopeField());
    }

    @Test
    void 未知资源关系重复归属空白名称主键及类型错误均拒绝() {
        assertThrows(ValidationException.class, () -> rules(Collections.singletonMap("unknown",
            ownership(Collections.emptyList()))).apply(model(Address.class)));
        assertThrows(ValidationException.class, () -> rules(Collections.singletonMap("address",
            ownership(Collections.singletonList("unknown")))));
        assertThrows(ValidationException.class, () -> ownership(Arrays.asList("customer", "customer")));
        assertThrows(ValidationException.class, () -> ownership(Collections.singletonList(" ")));
        assertThrows(ValidationException.class, () -> rules(Collections.singletonMap("address",
            new CrudOwnershipRules.Ownership(null, Collections.singletonMap("unknown", "customerId")))));
        for (String invalidField : Arrays.asList("unknown", "id", "address")) {
            assertThrows(ValidationException.class, () -> rules(Collections.singletonMap("address",
                new CrudOwnershipRules.Ownership(null, Collections.singletonMap("customer", invalidField))))
                .apply(model(Address.class)));
        }
        assertThrows(ValidationException.class, () -> new CrudOwnershipRules(Collections.singletonMap("customer",
            new CrudOwnershipRules.Relation("customerId", String.class)), Collections.emptyMap()).apply(model(Address.class)));
    }

    private CrudOwnershipRules rules(Map<String, CrudOwnershipRules.Ownership> declarations) {
        Map<String, CrudOwnershipRules.Relation> relations = new LinkedHashMap<>();
        relations.put("customer", new CrudOwnershipRules.Relation("customerId", Long.class));
        relations.put("merchant", new CrudOwnershipRules.Relation("merchantId", Long.class));
        return new CrudOwnershipRules(relations, declarations);
    }

    private CrudOwnershipRules.Ownership ownership(java.util.Collection<String> relations) {
        return new CrudOwnershipRules.Ownership(relations, Collections.emptyMap());
    }

    private CrudRuntimeModel model(Class<?> type) {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<>();
        fields.put("id", new EntityFieldMeta("id", Long.class, "id", false, false, true, true));
        fields.put("customerId", new EntityFieldMeta("customerId", Long.class, "customer_id", false, false,
            true, true, true, false, false, "所属顾客", false, null));
        fields.put("merchantId", new EntityFieldMeta("merchantId", Long.class, "merchant_id", false, false, true, true));
        fields.put("address", new EntityFieldMeta("address", String.class, "address", false, false, true, true));
        fields.put("readOnly", new EntityFieldMeta("readOnly", String.class, "read_only", false, false,
            true, true, false, false, false, null, false, null));
        EntityMeta meta = new EntityMeta(type, new ResourceDescriptor(type, "address", null, Collections.emptySet()),
            "address", "id", null, fields);
        return CrudRuntimeModel.from(Collections.singletonList(meta), Collections.emptyList());
    }

    private static class Address { }
    @EntCrudEntity(scopeFields = "customerId")
    private static class Explicit { }
}
