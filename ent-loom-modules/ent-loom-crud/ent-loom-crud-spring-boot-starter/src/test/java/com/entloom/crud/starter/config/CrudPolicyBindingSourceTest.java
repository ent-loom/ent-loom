package com.entloom.crud.starter.config;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.policy.EntityPolicyBinding;
import com.entloom.crud.core.governance.policy.EntityPolicyTemplate;
import com.entloom.crud.core.governance.policy.PolicyIdentityBranch;
import com.entloom.crud.core.governance.policy.ScenePolicyRegistry;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 绑定来源由装配入口区分，策略名不参与来源识别。 */
class CrudPolicyBindingSourceTest {
    @EntCrudEntity(name = "product", policy = "baseline")
    static class Product { }

    @Test
    void Java策略使用内部前缀也不能覆盖实体选择() {
        var binding = binding("grant:custom");
        var error = assertThrows(ValidationException.class,
            () -> compile(List.of(template("grant:custom")), List.of(binding), List.of()));
        assertTrue(error.getMessage().contains("实体策略绑定重复"));
    }

    @Test
    void 声明式绑定无需特定命名前缀() {
        var result = compile(List.of(template("compiled")), List.of(), List.of(binding("compiled")));
        assertEquals("compiled", result.bindings().get(0).getTemplateCode());
    }

    @Test
    void Java与声明式来源重复绑定同一实体时拒绝() {
        assertThrows(ValidationException.class, () -> compile(List.of(template("compiled")),
            List.of(binding("compiled")), List.of(binding("compiled"))));
    }

    @Test
    void 未知实体策略不能被忽略() {
        var error = assertThrows(ValidationException.class,
            () -> compile(List.of(), List.of(), List.of()));
        assertTrue(error.getMessage().contains("未知"));
    }

    private static EntityPolicyBinding binding(String code) {
        return new EntityPolicyBinding(Product.class, code, "test", Map.of());
    }

    private static EntityPolicyTemplate template(String code) {
        return new EntityPolicyTemplate(code, "test", List.of(new EntityPolicyTemplate.Rule(
            "view", PolicyIdentityBranch.publicBranch(), List.of())));
    }

    private static CrudPolicyDeclarations compile(List<EntityPolicyTemplate> templates,
            List<EntityPolicyBinding> javaBindings, List<EntityPolicyBinding> grantBindings) {
        var meta = mock(EntityMeta.class);
        when(meta.getEntityType()).thenReturn((Class) Product.class);
        when(meta.getResourceDescriptor()).thenReturn(new ResourceDescriptor(Product.class, "product", null, List.of()));
        var entities = mock(EntityMetaRegistry.class);
        when(entities.getEntityMetas()).thenReturn(List.of(meta));
        return new CrudPolicyDeclarations(entities, new CrudEntityDeclarations(List.of(Product.class)),
            templates, javaBindings, grantBindings, mock(ScenePolicyRegistry.class));
    }
}
