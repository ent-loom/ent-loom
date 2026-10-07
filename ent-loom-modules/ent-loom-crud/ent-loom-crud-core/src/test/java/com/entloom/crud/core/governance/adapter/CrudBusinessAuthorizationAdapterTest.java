package com.entloom.crud.core.governance.adapter;

import com.entloom.crud.annotations.EntCrudAction;
import com.entloom.crud.annotations.EntCrudActions;
import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationResult;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.authorization.BusinessAuthorizationService;
import com.entloom.crud.core.governance.authorization.BusinessPermissionDecision;
import com.entloom.crud.core.governance.authorization.BusinessPermissionEvaluator;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.core.governance.policy.BusinessOperationCatalog;
import com.entloom.crud.core.governance.policy.BusinessOperationDeclaration;
import com.entloom.crud.core.governance.policy.CrudOperationMapping;
import com.entloom.crud.core.governance.policy.CrudOperationMappingRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyBinding;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyTemplate;
import com.entloom.crud.core.governance.policy.PolicyIdentityBranch;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrudBusinessAuthorizationAdapterTest {
    private static final BusinessOperation VIEW = new BusinessOperation("order", "view");
    private static final BusinessOperation REFUND = new BusinessOperation("order", "refund");

    @Test
    void crud_and_custom_service_use_the_same_operation_and_dynamic_decision() {
        AtomicInteger calls = new AtomicInteger();
        Fixture fixture = fixture((visitor, operation, rule, target) -> {
            calls.incrementAndGet();
            assertTrue(visitor.isAnonymous());
            assertEquals("order-1", target);
            return BusinessPermissionDecision.allow(AuthorizationScope.equal("id", target),
                AuthorizationFields.of(Collections.singletonMap(AuthorizationFields.Use.RETURN,
                    Collections.singleton("status"))));
        });
        VisitorContext visitor = VisitorContext.anonymous("trusted-entry", "request-1", null);
        SubjectContext forged = new SubjectContext();
        forged.setSubjectId("forged-actor");
        forged.setOrgId("forged-org");
        QuerySpec<Order> query = QuerySpec.<Order>builder().rootType(Order.class)
            .op(QueryOperation.LIST).scene("search").subject(forged).resultType(Order.class).build();

        AuthorizationResult crud = fixture.adapter.authorize(visitor, query, "order-1");
        AuthorizationResult custom = fixture.authorization.authorize(new AuthorizationRequest(visitor, VIEW, "order-1"));
        assertEquals(AccessDecision.ALLOW, crud.getDecision());
        assertEquals(custom.getDecision(), crud.getDecision());
        assertEquals(custom.getRequest().getOperation(), crud.getRequest().getOperation());
        assertEquals(visitor, crud.getRequest().getVisitor());
        assertEquals(custom.getScope().getField(), crud.getScope().getField());
        assertEquals(custom.getScope().getValues(), crud.getScope().getValues());
        assertEquals(custom.getFields().snapshot(), crud.getFields().snapshot());
        assertEquals(2, calls.get());
    }

    @Test
    void action_scene_is_mapped_independently_and_missing_mapping_denies() {
        Fixture fixture = fixture((visitor, operation, rule, target) ->
            BusinessPermissionDecision.allow(AuthorizationScope.all(), AuthorizationFields.none()));
        VisitorContext visitor = VisitorContext.anonymous("trusted-entry", null, null);
        CommandSpec<Object> refund = CommandSpec.builder().rootType(Order.class)
            .op(CommandOperation.ACTION).scene("refund").build();
        AuthorizationResult result = fixture.adapter.authorize(visitor, refund, null);
        assertEquals(REFUND, result.getRequest().getOperation());
        assertThrows(PermissionDeniedException.class, () -> fixture.adapter.authorize(visitor,
            CommandSpec.builder().rootType(Order.class).op(CommandOperation.ACTION).scene("approve").build(), null));
        assertThrows(PermissionDeniedException.class, () -> fixture.adapter.authorize(visitor,
            QuerySpec.builder().rootType(Order.class).op(QueryOperation.DETAIL).build(), null));
        assertThrows(PermissionDeniedException.class, () -> fixture.adapter.authorize(visitor,
            QuerySpec.builder().rootType(Other.class).op(QueryOperation.LIST).build(), null));
        assertThrows(ValidationException.class, () -> fixture.adapter.authorize(visitor,
            QuerySpec.builder().op(QueryOperation.LIST).build(), null));
        assertThrows(IllegalArgumentException.class, () -> fixture.adapter.authorize(visitor,
            QuerySpec.builder().rootType(Order.class).build(), null));
    }

    private static Fixture fixture(BusinessPermissionEvaluator evaluator) {
        ResourceDescriptor resource = new ResourceDescriptor(Order.class, "order", null, Collections.emptyList());
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", new EntityFieldMeta("id", String.class, "id", false, false, true, true));
        fields.put("status", new EntityFieldMeta("status", String.class, "status", false, false, true, true));
        EntityMeta meta = new EntityMeta(Order.class, resource, "orders", "id", null, fields);
        EntityMetaRegistry entities = new EntityMetaRegistry() {
            @Override public EntityMeta getEntityMeta(Class<?> type) { return type == Order.class ? meta : null; }
            @Override public ResourceDescriptor getResourceDescriptor(Class<?> type) {
                return type == Order.class ? resource : null;
            }
            @Override public Collection<EntityMeta> getEntityMetas() { return Collections.singletonList(meta); }
            @Override public RelationGraph getRelationGraph(Class<?> rootType) { return RelationGraph.empty(); }
            @Override public void validateOrThrow() { }
        };
        BusinessOperationCatalog catalog = new BusinessOperationCatalog(Collections.singletonList(resource), Arrays.asList(
            new BusinessOperationDeclaration(VIEW, "Project#view"),
            new BusinessOperationDeclaration(REFUND, "Project#refund")));
        EntityPolicyTemplate template = new EntityPolicyTemplate("public", "Project#public", Arrays.asList(
            new EntityPolicyTemplate.Rule("view", PolicyIdentityBranch.publicBranch(), Collections.emptyList()),
            new EntityPolicyTemplate.Rule("refund", PolicyIdentityBranch.publicBranch(), Collections.emptyList())));
        EntityPolicyRegistry policies = new EntityPolicyRegistry(Collections.singletonList(meta),
            Collections.singletonList(template), Collections.singletonList(new EntityPolicyBinding(
                Order.class, "public", "Order#binding", Collections.emptyMap())),
            catalog, new BusinessIdentityRegistry(Collections.emptyList()));
        CrudOperationMappingRegistry mappings = new CrudOperationMappingRegistry(Collections.singletonList(meta), catalog,
            Arrays.asList(new CrudOperationMapping(CrudOperationKey.of(QueryOperation.LIST), null, VIEW, "Project#list"),
                new CrudOperationMapping(CrudOperationKey.of(CommandOperation.ACTION), "refund", REFUND,
                    "Project#refundMapping")));
        BusinessAuthorizationService authorization = new BusinessAuthorizationService(catalog, policies, evaluator);
        return new Fixture(authorization, new CrudBusinessAuthorizationAdapter(entities, mappings, authorization));
    }

    private static final class Fixture {
        private final BusinessAuthorizationService authorization;
        private final CrudBusinessAuthorizationAdapter adapter;

        private Fixture(BusinessAuthorizationService authorization, CrudBusinessAuthorizationAdapter adapter) {
            this.authorization = authorization;
            this.adapter = adapter;
        }
    }

    @EntCrudActions({@EntCrudAction(value = "refund", name = "退款", capability = "refund")})
    private static final class Order { }

    private static final class Other { }
}
