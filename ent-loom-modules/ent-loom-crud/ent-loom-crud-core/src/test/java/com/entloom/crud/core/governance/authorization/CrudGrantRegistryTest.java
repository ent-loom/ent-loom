package com.entloom.crud.core.governance.authorization;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.annotations.EntCrudFieldAccess;
import com.entloom.crud.annotations.EntCrudGrant;
import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistration;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.core.governance.policy.BusinessOperationCatalog;
import com.entloom.crud.core.governance.policy.BusinessOperationDeclaration;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static com.entloom.crud.annotations.EntCrudFieldUse.FILTER;
import static com.entloom.crud.annotations.EntCrudFieldUse.RETURN;
import static com.entloom.crud.annotations.EntCrudGrantScope.ALL;
import static org.junit.jupiter.api.Assertions.*;

class CrudGrantRegistryTest {
    private static final BusinessIdentityRegistry IDENTITIES = new BusinessIdentityRegistry(List.of(
        new BusinessIdentityRegistration<>("customer", Identity.class, (subject, id) -> new Identity(id))));
    private static final CrudGrantDefaults DEFAULTS = new CrudGrantDefaults(Map.of("customer", "customerId"), Set.of());

    @Test
    void 默认归属与实体主键覆盖均使用字段类型且不自动授予操作() {
        var result = authorize(service(Owned.class), visitor(), "view");
        assertEquals(AccessDecision.ALLOW, result.getDecision());
        assertEquals("customerId", result.getScope().getField());
        assertEquals(List.of(7L), result.getScope().getValues());
        assertEquals("id", authorize(service(Self.class), visitor(), "view").getScope().getField());
        assertThrows(PermissionDeniedException.class, () -> authorize(service(Owned.class), visitor(), "update"));
    }

    @Test
    void 匿名SQL条件与字段用途分别生效且未指定用途沿用上限() {
        var result = authorize(service(PublicProduct.class), VisitorContext.anonymous("test", null, null), "view");
        assertEquals(AuthorizationScope.Kind.SQL_WHERE, result.getScope().getKind());
        assertEquals("active = true", result.getScope().getSqlWhere());
        assertTrue(result.getFields().allows(AuthorizationFields.Use.RETURN, "id"));
        assertFalse(result.getFields().allows(AuthorizationFields.Use.RETURN, "customerId"));
        assertTrue(result.getFields().allows(AuthorizationFields.Use.FILTER, "customerId"));
    }

    @Test
    void 全量基础范围支持公开查看且SQL默认保留身份归属() {
        var declared = authorize(service(CustomerVisible.class), visitor(), "view");
        assertEquals(AuthorizationScope.Kind.SQL_WHERE, declared.getScope().getKind());
        var owned = authorize(service(OwnedVisible.class), visitor(), "view").getScope();
        assertEquals(AuthorizationScope.Kind.AND, owned.getKind());
        assertEquals("customerId", owned.getChildren().get(0).getField());
        assertEquals(List.of(7L), owned.getChildren().get(0).getValues());
        assertEquals("active = true", owned.getChildren().get(1).getSqlWhere());
    }

    @Test
    void 字段排除只收窄指定用途() {
        var result = authorize(service(Excluded.class), visitor(), "view");
        assertFalse(result.getFields().allows(AuthorizationFields.Use.FILTER, "customerId"));
        assertTrue(result.getFields().allows(AuthorizationFields.Use.RETURN, "customerId"));
        assertTrue(result.getFields().allows(AuthorizationFields.Use.SORT, "customerId"));
    }

    @Test
    void 字段注解约束默认继承的身份授权() {
        var fields = authorize(service(FieldRestricted.class), visitor(), "view").getFields();
        for (var use : AuthorizationFields.Use.values()) assertFalse(fields.allows(use, "active"));
        assertTrue(fields.allows(AuthorizationFields.Use.RETURN, "customerId"));
    }

    @Test
    void 动态扩展按次执行且不能扩大范围或字段() {
        AtomicBoolean allowed = new AtomicBoolean(true);
        EntityGrantCustomizer extension = new EntityGrantCustomizer() {
            public Class<?> getEntityType() { return PublicProduct.class; }
            public BusinessPermissionDecision customize(VisitorContext visitor, BusinessOperation operation, String target,
                                                         BusinessPermissionDecision declared) {
                assertEquals("target", target);
                return allowed.get() ? BusinessPermissionDecision.allow(AuthorizationScope.all(),
                    AuthorizationFields.of(Map.of(AuthorizationFields.Use.RETURN, List.of("id", "customerId"))))
                    : BusinessPermissionDecision.deny();
            }
        };
        var service = service(PublicProduct.class, List.of(extension));
        var request = new AuthorizationRequest(VisitorContext.anonymous("test", null, null), new BusinessOperation("sample", "view"), "target");
        var result = service.authorize(request);
        assertEquals(AuthorizationScope.Kind.SQL_WHERE, result.getScope().getKind());
        assertFalse(result.getFields().allows(AuthorizationFields.Use.RETURN, "customerId"));
        allowed.set(false);
        assertEquals(AccessDecision.DENY, service.authorize(request).getDecision());
    }

    @Test
    void 实体显式规则整体覆盖全局星号授权() {
        var registry = registry(Owned.class, new CrudGrantDefaults(Map.of(), Set.of("customer")), List.of());
        assertTrue(registry.governs("sample"));
        assertFalse(registry.governs("unknown"));
        assertEquals(AuthorizationScope.Kind.ALL, authorize(service(Owned.class, registry), visitor(), "view").getScope().getKind());
        var restricted = registry(Restricted.class, new CrudGrantDefaults(Map.of(), Set.of("customer")), List.of());
        var service = service(Restricted.class, restricted);
        assertEquals(AccessDecision.ALLOW, authorize(service, visitor(), "view").getDecision());
        assertThrows(PermissionDeniedException.class, () -> authorize(service, visitor(), "update"));
    }

    @Test
    void 全局星号自动覆盖实体已声明操作及自定义动作但不越过字段上限() {
        var registry = registry(GlobalOnly.class, new CrudGrantDefaults(Map.of(), Set.of("customer")), List.of());
        var service = service(GlobalOnly.class, registry);
        for (String action : List.of("view", "update", "approve")) {
            var result = authorize(service, visitor(), action);
            assertEquals(AccessDecision.ALLOW, result.getDecision());
            assertEquals(AuthorizationScope.Kind.ALL, result.getScope().getKind());
            assertTrue(result.getFields().allows(AuthorizationFields.Use.RETURN, "id"));
            assertFalse(result.getFields().allows(AuthorizationFields.Use.RETURN, "customerId"));
        }
        assertThrows(PermissionDeniedException.class, () -> authorize(service, visitor(), "unknown"));
    }

    @Test
    void 实体显式禁止关闭全局星号且不能与授权混用() {
        var registry = registry(Denied.class, new CrudGrantDefaults(Map.of(), Set.of("customer")), List.of());
        assertTrue(registry.governs("sample"));
        assertThrows(PermissionDeniedException.class, () -> authorize(service(Denied.class, registry), visitor(), "view"));
        assertThrows(ValidationException.class, () -> registry(MixedDenial.class, DEFAULTS, List.of()));
    }

    @Test
    void 默认所有用途白名单不会开放主键写入且排除对所有用途生效() {
        var fields = authorize(service(AllWhitelist.class), visitor(), "view").getFields();
        assertTrue(fields.allows(AuthorizationFields.Use.RETURN, "id"));
        assertTrue(fields.allows(AuthorizationFields.Use.FILTER, "id"));
        assertFalse(fields.allows(AuthorizationFields.Use.WRITE, "id"));
        assertFalse(fields.allows(AuthorizationFields.Use.RETURN, "active"));
        var excluded = authorize(service(AllExclude.class), visitor(), "view").getFields();
        for (var use : AuthorizationFields.Use.values()) assertFalse(excluded.allows(use, "customerId"));
    }

    @Test
    void 统一规则与专属规则交集不依赖声明顺序() {
        var first = authorize(service(AllThenSpecific.class), visitor(), "view").getFields();
        var reversed = authorize(service(SpecificThenAll.class), visitor(), "view").getFields();
        assertEquals(first.snapshot(), reversed.snapshot());
        assertTrue(first.allows(AuthorizationFields.Use.RETURN, "id"));
        assertFalse(first.allows(AuthorizationFields.Use.RETURN, "customerId"));
        assertTrue(first.allows(AuthorizationFields.Use.FILTER, "customerId"));
    }

    @Test
    void 一次声明多个用途并保留未选择用途的上限() {
        var fields = authorize(service(MultipleUses.class), visitor(), "view").getFields();
        assertTrue(fields.allows(AuthorizationFields.Use.RETURN, "id"));
        assertFalse(fields.allows(AuthorizationFields.Use.RETURN, "customerId"));
        assertFalse(fields.allows(AuthorizationFields.Use.FILTER, "customerId"));
        assertTrue(fields.allows(AuthorizationFields.Use.SORT, "customerId"));
    }

    @Test
    void 启动拒绝重复未知身份超限操作字段以及不合法范围() {
        for (Class<?> type : List.of(Duplicate.class, UnknownIdentity.class, BadAction.class, BadField.class,
            MissingOwner.class, BlankWhere.class, WriteWhere.class, MixedWhere.class, DeniedWhere.class, PublicDefault.class, ConflictingPolicy.class, DuplicateUse.class,
            BothFieldModes.class, InvalidAll.class, OutsideCeiling.class,
            MixedAllUses.class, EmptyUses.class, DuplicateAll.class, UnknownAllField.class, OverlappingUses.class)) {
            assertThrows(ValidationException.class, () -> registry(type, DEFAULTS, List.of()), type.getSimpleName());
        }
    }

    @Test
    void 启动拒绝未知默认身份和重复动态扩展() {
        assertThrows(ValidationException.class, () -> registry(Owned.class,
            new CrudGrantDefaults(Map.of("unknown", "customerId"), Set.of()), List.of()));
        EntityGrantCustomizer extension = new EntityGrantCustomizer() {
            public Class<?> getEntityType() { return Owned.class; }
            public BusinessPermissionDecision customize(VisitorContext visitor, BusinessOperation operation, String target,
                                                         BusinessPermissionDecision declared) { return declared; }
        };
        assertThrows(ValidationException.class, () -> registry(Owned.class, DEFAULTS, List.of(extension, extension)));
    }

    private static BusinessAuthorizationService service(Class<?> type) { return service(type, List.of()); }
    private static BusinessAuthorizationService service(Class<?> type, List<EntityGrantCustomizer> extensions) {
        return service(type, registry(type, DEFAULTS, extensions));
    }
    private static BusinessAuthorizationService service(Class<?> type, CrudGrantRegistry grants) {
        var entities = entities(type);
        var declarations = new ArrayList<BusinessOperationDeclaration>();
        for (String action : List.of("view", "update", "approve")) declarations.add(new BusinessOperationDeclaration(new BusinessOperation("sample", action), "test"));
        var catalog = new BusinessOperationCatalog(List.of(entities.getResourceDescriptor(type)), declarations);
        var policies = new EntityPolicyRegistry(entities, grants.getTemplates(), grants.getBindings(), catalog, IDENTITIES);
        return new BusinessAuthorizationService(catalog, policies, grants::evaluate);
    }
    private static CrudGrantRegistry registry(Class<?> type, CrudGrantDefaults defaults, List<EntityGrantCustomizer> extensions) {
        return new CrudGrantRegistry(entities(type), IDENTITIES, defaults, extensions, com.entloom.crud.core.governance.policy.CrudEntityActions.readOnly());
    }
    private static AuthorizationResult authorize(BusinessAuthorizationService service, VisitorContext visitor, String action) {
        return service.authorize(new AuthorizationRequest(visitor, new BusinessOperation("sample", action), null));
    }
    private static VisitorContext visitor() {
        return VisitorContext.authenticated(new AccessSubject("subject", AccessSubjectType.HUMAN), new Identity("7"), "test", null, null);
    }
    private static EntityMetaRegistry entities(Class<?> type) {
        var descriptor = new ResourceDescriptor(type, "sample", null, Collections.emptyList());
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<>();
        for (String name : List.of("id", "customerId")) fields.put(name, new EntityFieldMeta(name, Long.class, name, false, false, true, true));
        fields.put("active", new EntityFieldMeta("active", Boolean.class, "active", false, false, true, true));
        var meta = new EntityMeta(type, descriptor, "sample", "id", null, fields);
        return new EntityMetaRegistry() {
            public EntityMeta getEntityMeta(Class<?> entityType) { return entityType == type ? meta : null; }
            public ResourceDescriptor getResourceDescriptor(Class<?> entityType) { return getEntityMeta(entityType).getResourceDescriptor(); }
            public Collection<EntityMeta> getEntityMetas() { return List.of(meta); }
            public RelationGraph getRelationGraph(Class<?> root) { return RelationGraph.empty(); }
            public void validateOrThrow() {}
        };
    }
    public static final class Identity implements BusinessIdentity {
        private final String id;
        Identity(String id) { this.id = id; }
        public String getIdentityCode() { return "customer"; }
        public String getIdentityId() { return id; }
    }

    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view", fields = @EntCrudGrant.Fields(value = {"id", "customerId"})) static class AllWhitelist {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view")
    static class FieldRestricted { @EntCrudFieldAccess(exclude = com.entloom.crud.annotations.EntCrudFieldUse.ALL) Boolean active; }
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view",
        fields = @EntCrudGrant.Fields(exclude = "customerId")) static class AllExclude {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view", fields = {
        @EntCrudGrant.Fields(value = {"id", "customerId"}),
        @EntCrudGrant.Fields(use = RETURN, exclude = "customerId")}) static class AllThenSpecific {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view", fields = {
        @EntCrudGrant.Fields(use = RETURN, exclude = "customerId"),
        @EntCrudGrant.Fields(value = {"id", "customerId"})}) static class SpecificThenAll {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view",
        fields = @EntCrudGrant.Fields(use = {RETURN, FILTER}, value = "id")) static class MultipleUses {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view",
        fields = @EntCrudGrant.Fields(use = {com.entloom.crud.annotations.EntCrudFieldUse.ALL, RETURN})) static class MixedAllUses {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view",
        fields = @EntCrudGrant.Fields(use = {})) static class EmptyUses {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view",
        fields = {@EntCrudGrant.Fields(), @EntCrudGrant.Fields()}) static class DuplicateAll {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view",
        fields = @EntCrudGrant.Fields(value = "unknown")) static class UnknownAllField {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view", fields = {
        @EntCrudGrant.Fields(use = {RETURN, FILTER}), @EntCrudGrant.Fields(use = FILTER)}) static class OverlappingUses {}
    @EntCrudEntity(actions = {"view", "update", "approve"}, fieldLimits = {"RETURN:id", "SORT:id"})
    static class GlobalOnly {}
    @EntCrudEntity(actions = {"view", "update"})
    @EntCrudGrant(identities = "customer", actions = "view") static class Restricted {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = {}) static class Denied {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = {})
    @EntCrudGrant(identities = "customer", actions = "view") static class MixedDenial {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view") static class Owned {}
    @EntCrudEntity @EntCrudGrant(identities = {"public", "customer"}, actions = "view", scope = ALL, where = "active = true") static class CustomerVisible {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view", where = "active = true") static class OwnedVisible {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view", scopeField = "id") static class Self {}
    @EntCrudEntity @EntCrudGrant(identities = "public", actions = "view", scope = ALL, where = "active = true",
        fields = @EntCrudGrant.Fields(use = RETURN, value = "id")) static class PublicProduct {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view",
        fields = @EntCrudGrant.Fields(use = FILTER, exclude = "customerId")) static class Excluded {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view")
    @EntCrudGrant(identities = "customer", actions = "view") static class Duplicate {}
    @EntCrudEntity @EntCrudGrant(identities = "unknown", actions = "view", scope = ALL) static class UnknownIdentity {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "update") static class BadAction {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view",
        fields = @EntCrudGrant.Fields(use = RETURN, value = "unknown")) static class BadField {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view", scopeField = "missing") static class MissingOwner {}
    @EntCrudEntity @EntCrudGrant(identities = "public", actions = "view", where = "  ") static class BlankWhere {}
    @EntCrudEntity(actions = "update") @EntCrudGrant(identities = "customer", actions = "update", where = "active = true") static class WriteWhere {}
    @EntCrudEntity(actions = {"view", "update"}) @EntCrudGrant(identities = "customer", actions = {"view", "update"}, where = "active = true") static class MixedWhere {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = {}, where = "active = true") static class DeniedWhere {}
    @EntCrudEntity @EntCrudGrant(identities = "public", actions = "view") static class PublicDefault {}
    @EntCrudEntity(policy = "old") @EntCrudGrant(identities = "customer", actions = "view") static class ConflictingPolicy {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view", fields = {
        @EntCrudGrant.Fields(use = RETURN), @EntCrudGrant.Fields(use = RETURN)}) static class DuplicateUse {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view",
        fields = @EntCrudGrant.Fields(use = FILTER, value = "id", exclude = "customerId")) static class BothFieldModes {}
    @EntCrudEntity @EntCrudGrant(identities = "customer", actions = "view", scope = ALL, scopeField = "id") static class InvalidAll {}
    @EntCrudEntity(fieldLimits = "RETURN:id") @EntCrudGrant(identities = "customer", actions = "view",
        fields = @EntCrudGrant.Fields(use = RETURN, value = "customerId")) static class OutsideCeiling {}
}
