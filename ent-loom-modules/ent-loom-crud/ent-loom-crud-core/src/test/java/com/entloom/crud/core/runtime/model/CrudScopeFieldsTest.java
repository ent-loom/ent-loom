package com.entloom.crud.core.runtime.model;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CrudScopeFieldsTest {
    private static final CrudScopeFields DEFAULTS = new CrudScopeFields(List.of("customerId", "merchantId", "merchantId", "id"));
    private static final Set<String> PERSISTENT = Set.of("customerId", "merchantId", "orderId", "name");

    @Test
    void 缺省仅保护实体存在的非主键持久化字段() {
        assertEquals(Set.of("customerId", "merchantId"), DEFAULTS.resolve(Default.class, PERSISTENT));
        assertEquals(Set.of("merchantId"), DEFAULTS.resolve(Default.class, Set.of("merchantId")));
        assertEquals(Set.of(), DEFAULTS.resolve(Default.class, Set.of("name")));
        assertEquals(Set.of(), DEFAULTS.resolve(Unmanaged.class, PERSISTENT));
    }

    @Test
    void 显式字段完整替换默认字段空数组关闭默认保护() {
        assertEquals(Set.of("orderId"), DEFAULTS.resolve(Explicit.class, PERSISTENT));
        assertEquals(Set.of("merchantId", "orderId"), DEFAULTS.resolve(ExplicitMultiple.class, PERSISTENT));
        assertEquals(Set.of(), DEFAULTS.resolve(Disabled.class, PERSISTENT));
        assertEquals(Set.of("orderId"), CrudScopeFields.none().resolve(Explicit.class, PERSISTENT));
    }

    @Test
    void 重复未知主键及空字段均启动失败() {
        for (Class<?> type : List.of(Duplicate.class, Missing.class, Primary.class, Blank.class)) {
            assertThrows(ValidationException.class, () -> DEFAULTS.resolve(type, PERSISTENT), type.getSimpleName());
        }
    }

    @Test
    void 原生元数据使用同一解析结果关闭普通写入() {
        var model = new CrudNativeRuntimeModelParser(List.of(), new CrudIdPolicyDefaults(), DEFAULTS)
            .parse(List.of(Default.class, Disabled.class));
        var registry = new CrudRuntimeModelBackedEntityMetaRegistry(model);
        var protectedField = registry.getEntityMeta(Default.class).resolveFieldMeta("merchantId");
        assertTrue(protectedField.isScopeField());
        assertFalse(protectedField.isWritable());
        var ordinaryField = registry.getEntityMeta(Disabled.class).resolveFieldMeta("merchantId");
        assertFalse(ordinaryField.isScopeField());
        assertTrue(ordinaryField.isWritable());
    }

    @EntCrudEntity static class Default { Long id; Long merchantId; Long customerId; }
    @EntCrudEntity(scopeFields = {}) static class Disabled { Long id; Long merchantId; }
    static class Unmanaged {}
    @EntCrudEntity(scopeFields = "orderId") static class Explicit {}
    @EntCrudEntity(scopeFields = {"merchantId", "orderId"}) static class ExplicitMultiple {}
    @EntCrudEntity(scopeFields = {"merchantId", "merchantId"}) static class Duplicate {}
    @EntCrudEntity(scopeFields = "missing") static class Missing {}
    @EntCrudEntity(scopeFields = "id") static class Primary {}
    @EntCrudEntity(scopeFields = " ") static class Blank {}
}
