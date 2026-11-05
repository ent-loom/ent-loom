package com.entloom.crud.core.capability.command.engine;

import com.entloom.crud.api.access.*;
import com.entloom.crud.api.model.CommandAction;
import com.entloom.crud.core.capability.dao.RowConstraint;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.admission.*;
import com.entloom.crud.core.governance.authorization.*;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.core.governance.policy.*;
import com.entloom.crud.core.runtime.meta.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthorizedActionContextTest {
    @Test
    void 根实体范围与关联声明隔离且作用域结束后不能复用() {
        var admission = new AccessAdmissionService(new BusinessIdentityRegistry(List.of())).admit(
            AuthenticationMode.OPTIONAL, CredentialAuthentication::absent, "base", null, null);
        var visitor = admission.getVisitorContext();
        var context = new AuthorizedActionContext(Order.class, "trade-order", visitor, authorization(visitor), List.of(
            new RelatedDaoAccess(Product.class, RelatedDaoAccess.Operation.READ, "id"),
            new RelatedDaoAccess(Item.class, RelatedDaoAccess.Operation.CREATE, "orderId")));
        try (var ignored = AccessContextScope.enter(admission)) {
            context.requireDaoScope(Order.class, RowConstraint.unrestricted());
            assertEquals("ownerId", context.rowConstraint().getField());
            context.requireDaoScope(Product.class, RowConstraint.in("id", List.of(1L, 2L)));
            context.requireDaoMethod(Product.class, "findAllByIdAsMap");
            context.requireDaoScope(Item.class, RowConstraint.eq("orderId", 7L));
            context.requireDaoMethod(Item.class, "insertAll");
            assertThrows(PermissionDeniedException.class, () -> context.requireDaoScope(String.class, RowConstraint.eq("id", 1L)));
            assertThrows(PermissionDeniedException.class, () -> context.requireDaoScope(Product.class, RowConstraint.unrestricted()));
            assertThrows(PermissionDeniedException.class, () -> context.requireDaoScope(Product.class, RowConstraint.eq("merchantId", 1L)));
            assertThrows(PermissionDeniedException.class, () -> context.requireDaoScope(Product.class, RowConstraint.eq("id", null)));
            assertThrows(PermissionDeniedException.class, () -> context.requireDaoScope(Product.class, RowConstraint.in("id", List.of())));
            assertThrows(PermissionDeniedException.class, () -> context.requireDaoMethod(Product.class, "deleteById"));
            assertThrows(PermissionDeniedException.class, () -> context.requireDaoMethod(Item.class, "findById"));
        }
        assertThrows(PermissionDeniedException.class, () -> context.requireDaoMethod(Product.class, "findById"));
    }

    @Test
    void 类型化动作核对实体资源操作且不能跨作用域使用() {
        var admission = new AccessAdmissionService(new BusinessIdentityRegistry(List.of())).admit(
            AuthenticationMode.OPTIONAL, CredentialAuthentication::absent, "base", null, null);
        var visitor = admission.getVisitorContext();
        var result = authorization(visitor);
        var context = new AuthorizedActionContext(Order.class, "trade-order", visitor, result, List.of());
        var create = action(Order.class, "create");
        try (var ignored = AccessContextScope.enter(admission)) {
            assertDoesNotThrow(() -> context.requireAction(create));
            assertThrows(PermissionDeniedException.class, () -> context.requireAction(action(Product.class, "create")));
            assertThrows(PermissionDeniedException.class, () -> context.requireAction(action(Order.class, "cancel")));
            assertThrows(PermissionDeniedException.class, () -> context.requireAction(null));
            // 元数据资源码可以不同于类名，但必须与授权请求中的资源一致。
            var wrongResource = new AuthorizedActionContext(Order.class, "other", visitor, result, List.of());
            assertThrows(PermissionDeniedException.class, () -> wrongResource.requireAction(create));
        }
        assertThrows(PermissionDeniedException.class, () -> context.requireAction(create));
    }

    private CommandAction<Void, Void> action(Class<?> entityType, String scene) {
        return new CommandAction<Void, Void>(entityType, scene, Void.class, Void.class) { };
    }

    private AuthorizationResult authorization(VisitorContext visitor) {
        var resource = new ResourceDescriptor(Order.class, "trade-order", null, List.of());
        var operation = new BusinessOperation("trade-order", "create");
        var catalog = new BusinessOperationCatalog(List.of(resource), List.of(new BusinessOperationDeclaration(operation, "测试")));
        var meta = new EntityMeta(Order.class, resource, "orders", "id", null,
            Map.of("id", new EntityFieldMeta("id", Long.class, "id", false, false, true, true),
                "ownerId", new EntityFieldMeta("ownerId", Long.class, "owner_id", false, false, true, true)));
        var template = new EntityPolicyTemplate("test", "测试", List.of(new EntityPolicyTemplate.Rule(
            "create", PolicyIdentityBranch.publicBranch(), List.of())));
        var policies = new EntityPolicyRegistry(List.of(meta), List.of(template), List.of(new EntityPolicyBinding(
            Order.class, "test", "测试", Map.of())), catalog, new BusinessIdentityRegistry(List.of()));
        return new BusinessAuthorizationService(catalog, policies, (who, action, rule, target) ->
            BusinessPermissionDecision.allow(AuthorizationScope.equal("ownerId", 42L), AuthorizationFields.none()))
            .authorize(new AuthorizationRequest(visitor, operation, null));
    }
    private static class Order { }
    private static class Product { }
    private static class Item { }
}
