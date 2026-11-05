package com.entloom.crud.starter.config;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrudGrantPoliciesTest {
    @EntCrudEntity(policy = "catalog")
    static class Product { }

    @Test
    void 同一基线多个身份独立覆盖并支持显式撤权() {
        var properties = properties();
        var patch = new CrudGrantPolicies.Grant();
        patch.setFields(List.of("id"));
        properties.setPolicyOverrides(Map.of("product", Map.of("customer", patch)));
        var declarations = CrudGrantPolicies.resolve(entities(), properties).get(Product.class);
        assertArrayEquals(new String[] {"id", "name"}, declarations[0].fields()[0].value());
        assertArrayEquals(new String[] {"id"}, declarations[1].fields()[0].value());
        assertArrayEquals(new String[] {"view"}, declarations[1].actions());
        assertEquals("active = true", declarations[1].where());
        patch.setWhere("active = true AND status IN ('ON_SALE', 'PRE_SALE')");
        assertEquals(patch.getWhere(), CrudGrantPolicies.resolve(entities(), properties).get(Product.class)[1].where());
        patch.setWhere("");
        assertEquals("", CrudGrantPolicies.resolve(entities(), properties).get(Product.class)[1].where());
        patch.setActions(List.of());
        assertEquals(0, CrudGrantPolicies.resolve(entities(), properties).get(Product.class)[1].actions().length);
    }

    @Test
    void 错误身份和实体定制以及重复身份拒绝() {
        var properties = properties();
        properties.setPolicyOverrides(Map.of("product", Map.of("typo", new CrudGrantPolicies.Grant())));
        assertThrows(ValidationException.class, () -> CrudGrantPolicies.resolve(entities(), properties));
        properties.setPolicyOverrides(Map.of("typo", Map.of()));
        assertThrows(ValidationException.class, () -> CrudGrantPolicies.resolve(entities(), properties));
        properties.setPolicyOverrides(Map.of());
        var policy = properties.getPolicies().get("catalog");
        policy.setGrants(List.of(policy.getGrants().get(0), policy.getGrants().get(0)));
        assertThrows(ValidationException.class, () -> CrudGrantPolicies.resolve(entities(), properties));
    }

    private static CrudProperties properties() {
        var grant = new CrudGrantPolicies.Grant();
        grant.setIdentities(List.of("public", "customer"));
        grant.setActions(List.of("view"));
        grant.setScope(com.entloom.crud.annotations.EntCrudGrantScope.ALL);
        grant.setWhere("active = true");
        grant.setFields(List.of("id", "name"));
        var policy = new CrudGrantPolicies.Policy();
        policy.setGrants(List.of(grant));
        var properties = new CrudProperties();
        properties.setPolicies(Map.of("catalog", policy));
        return properties;
    }

    private static EntityMetaRegistry entities() {
        var meta = mock(EntityMeta.class);
        when(meta.getEntityType()).thenReturn((Class) Product.class);
        when(meta.getResourceDescriptor()).thenReturn(new ResourceDescriptor(Product.class, "product", null, List.of()));
        var entities = mock(EntityMetaRegistry.class);
        when(entities.getEntityMetas()).thenReturn(List.of(meta));
        return entities;
    }
}
