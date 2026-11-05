package com.entloom.meta.adapter.crud;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.policy.BusinessOperationCatalog;
import com.entloom.crud.core.governance.policy.BusinessOperationDeclaration;
import com.entloom.crud.core.governance.policy.EntityPolicyBinding;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyTemplate;
import com.entloom.crud.core.governance.policy.PolicyIdentityBranch;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntRelation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 使用真实 EntRelation 解析链，验证结构、授权选择与写入约束的边界。 */
class RelationAuthorizationMetadataTest {
    @Test
    void 全局归属字段在Meta适配链中统一保护且允许显式关闭() {
        var adapter = new MetaCrudAdapter(List.of(DefaultOwned.class, Unprotected.class),
            new com.entloom.meta.core.parser.ReflectiveEntMetaParser(),
            new com.entloom.crud.core.runtime.model.input.CrudNativeAnnotationParser(),
            new com.entloom.meta.adapter.crud.merge.CrudRuntimeModelMerger(),
            com.entloom.meta.contract.diagnostic.DefaultMetaDiagnosticPolicy.failFast(),
            new com.entloom.crud.core.runtime.model.CrudIdPolicyDefaults(),
            new com.entloom.crud.core.runtime.model.CrudScopeFields(List.of("merchantId", "customerId")));
        var registry = new CrudRuntimeModelBackedEntityMetaRegistry(adapter.runtimeModel());
        var owned = registry.getEntityMeta(DefaultOwned.class).resolveFieldMeta("merchantId");
        assertTrue(owned.isScopeField());
        assertFalse(owned.isWritable());
        var plain = registry.getEntityMeta(Unprotected.class).resolveFieldMeta("merchantId");
        assertFalse(plain.isScopeField());
        assertTrue(plain.isWritable());
    }

    @EntEntity(entity = "defaultOwned") @EntCrudEntity
    static class DefaultOwned {
        @com.entloom.meta.annotations.EntField("主键") Long id;
        @com.entloom.meta.annotations.EntField("商户") Long merchantId;
    }
    @EntEntity(entity = "unprotected") @EntCrudEntity(scopeFields = {})
    static class Unprotected {
        @com.entloom.meta.annotations.EntField("主键") Long id;
        @com.entloom.meta.annotations.EntField("商户") Long merchantId;
    }

    @Test
    void 业务策略选择关系后复用真实外键而不是身份命名约定() {
        var entities = entities(Invoice.class, Seller.class);
        var reference = entities.requireReference(Invoice.class, Seller.class);
        assertEquals("sellerRef", reference.getFromField());
        var scope = AuthorizationScope.equal(reference.getFromField(), 1001L);
        assertEquals("sellerRef", scope.getField());
        assertEquals(List.of(1001L), scope.getValues());
        assertThrows(UnsupportedOperationException.class, () -> reference.setFromField("another"));
    }

    @Test
    void 普通关系保持可写而显式归属字段受保护() {
        var entities = entities(Invoice.class, OwnedInvoice.class, Seller.class);
        assertFalse(entities.getEntityMeta(Invoice.class).resolveFieldMeta("sellerRef").isScopeField());
        assertTrue(entities.getEntityMeta(Invoice.class).resolveFieldMeta("sellerRef").isWritable());
        assertTrue(entities.getEntityMeta(OwnedInvoice.class).resolveFieldMeta("sellerRef").isScopeField());
        assertFalse(entities.getEntityMeta(OwnedInvoice.class).resolveFieldMeta("sellerRef").isWritable());
    }

    @Test
    void 同一目标存在多条关系时必须显式选择() {
        var entities = entities(Approval.class, Seller.class);
        assertThrows(RuntimeException.class, () -> entities.requireReference(Approval.class, Seller.class));
        assertEquals("ownerRef", entities.requireReference(Approval.class, Seller.class, "ownerRef").getFromField());
        assertEquals("reviewerRef", entities.requireReference(Approval.class, "reviewerRef").getFromField());
        assertThrows(RuntimeException.class, () -> entities.requireReference(Approval.class, Seller.class, "missing"));
    }

    @Test
    void 不支持的自然键引用不能被解释为主体主键() {
        var entities = entities(NaturalKeyInvoice.class, Seller.class);
        assertThrows(RuntimeException.class, () -> entities.requireReference(NaturalKeyInvoice.class, Seller.class));
    }

    @Test
    void 类型不一致的关系不能生成授权范围() {
        assertThrows(RuntimeException.class, () -> entities(BrokenInvoice.class, Seller.class)
            .requireReference(BrokenInvoice.class, Seller.class));
    }

    @Test
    void Java模板可直接选择关系属性无需重复绑定外键() {
        var entities = entities(Invoice.class, Seller.class);
        var view = new BusinessOperation("invoice", "view");
        var operations = new BusinessOperationCatalog(List.of(entities.getResourceDescriptor(Invoice.class)),
            List.of(new BusinessOperationDeclaration(view, "test")));
        var policy = new EntityPolicyTemplate("read", "test", List.of(new EntityPolicyTemplate.Rule(
            "view", PolicyIdentityBranch.publicBranch(), List.of("sellerRef"))));
        var registry = new EntityPolicyRegistry(entities, List.of(policy), List.of(new EntityPolicyBinding(
            Invoice.class, "read", "test", Map.of())), operations, new BusinessIdentityRegistry(List.of()));
        assertEquals(Map.of("sellerRef", "sellerRef"), registry.requireDeclaration(view,
            PolicyIdentityBranch.publicBranch()).getRelationFields());
    }

    private EntityMetaRegistry entities(Class<?>... types) {
        return new CrudRuntimeModelBackedEntityMetaRegistry(new MetaCrudAdapter(Arrays.asList(types)).runtimeModel());
    }

    @EntEntity(entity = "seller")
    static class Seller { Long id; String code; }

    @EntEntity(entity = "invoice")
    static class Invoice {
        Long id;
        @EntRelation(Seller.class) Long sellerRef;
    }

    @EntEntity(entity = "ownedInvoice") @EntCrudEntity(scopeFields = "sellerRef")
    static class OwnedInvoice {
        Long id;
        @EntRelation(Seller.class) Long sellerRef;
    }

    @EntEntity(entity = "approval")
    static class Approval {
        Long id;
        @EntRelation(Seller.class) Long ownerRef;
        @EntRelation(Seller.class) Long reviewerRef;
    }

    @EntEntity(entity = "naturalKeyInvoice")
    static class NaturalKeyInvoice {
        Long id;
        @EntRelation(value = Seller.class, targetField = "code") String sellerCode;
    }

    @EntEntity(entity = "brokenInvoice")
    static class BrokenInvoice {
        Long id;
        @EntRelation(Seller.class) String sellerRef;
    }
}
