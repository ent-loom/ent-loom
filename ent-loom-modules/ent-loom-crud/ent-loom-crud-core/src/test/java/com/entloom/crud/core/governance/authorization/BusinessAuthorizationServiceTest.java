package com.entloom.crud.core.governance.authorization;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityDaoBatch;
import com.entloom.crud.core.capability.dao.EntityDaoBatchAuthorizationContext;
import com.entloom.crud.core.capability.dao.EntityDaoAuthorizationContext;
import com.entloom.crud.core.capability.dao.EntityDaoAuthorizationProvider;
import com.entloom.crud.core.capability.dao.EntityDaoFactory;
import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.governance.fact.RelationFactService;
import com.entloom.crud.core.governance.fact.RelationFactSet;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistration;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.core.governance.policy.BusinessOperationCatalog;
import com.entloom.crud.core.governance.policy.BusinessOperationDeclaration;
import com.entloom.crud.core.governance.policy.EntityPolicyBinding;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyTemplate;
import com.entloom.crud.core.governance.policy.PolicyIdentityBranch;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessAuthorizationServiceTest {
    private static final BusinessOperation VIEW = new BusinessOperation("order", "view");
    private static final BusinessOperation REFUND = new BusinessOperation("order", "refund");
    private static final BusinessOperation RELATED = new BusinessOperation("order", "related");

    @Test
    void reevaluates_dynamic_permission_without_caching_and_binds_the_result_to_one_call() {
        AtomicBoolean qualified = new AtomicBoolean(true);
        AtomicInteger calls = new AtomicInteger();
        BusinessAuthorizationService service = service((visitor, operation, rule, target) -> {
            calls.incrementAndGet();
            assertEquals("teacher", rule.getBranch().getIdentityCode());
            assertEquals("order-1", target);
            return qualified.get() ? BusinessPermissionDecision.allow(
                AuthorizationScope.equal("ownerId", "teacher-1"), returnFields())
                : BusinessPermissionDecision.deny();
        });
        AuthorizationRequest first = request(teacher(), VIEW, "order-1");
        AuthorizationResult allowed = service.authorize(first);
        assertEquals(AccessDecision.ALLOW, allowed.getDecision());
        assertSame(first, allowed.getRequest());
        assertEquals(AuthorizationScope.Kind.EQUAL, allowed.getScope().getKind());
        assertTrue(allowed.getFields().allows(AuthorizationFields.Use.RETURN, "status"));
        allowed.requireApplicable(first);
        qualified.set(false);
        AuthorizationRequest second = request(teacher(), VIEW, "order-1");
        AuthorizationResult denied = service.authorize(second);
        assertEquals(AccessDecision.DENY, denied.getDecision());
        assertSame(AuthorizationScope.none(), denied.getScope());
        assertThrows(PermissionDeniedException.class, () -> denied.requireApplicable(second));
        assertThrows(PermissionDeniedException.class, () -> allowed.requireApplicable(second));
        assertEquals(2, calls.get());
    }

    @Test
    void selects_exact_identity_branch_and_sanitizes_public_branch() {
        AtomicInteger publicCalls = new AtomicInteger();
        BusinessAuthorizationService service = service((visitor, operation, rule, target) -> {
            if (rule.getBranch().equals(PolicyIdentityBranch.publicBranch())) {
                publicCalls.incrementAndGet();
                assertTrue(visitor.isAnonymous());
                assertFalse(visitor.getIdentity().isPresent());
                assertEquals("trusted-entry", visitor.getAccessEntry());
            } else {
                assertEquals("teacher", visitor.getIdentity().get().getIdentityCode());
            }
            return BusinessPermissionDecision.allow(AuthorizationScope.none(), AuthorizationFields.none());
        });
        service.authorize(request(teacher(), VIEW, null));
        service.authorize(request(VisitorContext.anonymous("trusted-entry", null, null), VIEW, null));
        VisitorContext noIdentity = VisitorContext.authenticated(
            new AccessSubject("actor-1", AccessSubjectType.HUMAN), null, "trusted-entry", null, null);
        service.authorize(request(noIdentity, VIEW, null));
        assertEquals(2, publicCalls.get());
        assertThrows(PermissionDeniedException.class, () -> service.authorize(request(teacher(), REFUND, null)));
    }

    @Test
    void rejects_unknown_unbound_and_relation_rules_without_fact_service() {
        AtomicInteger calls = new AtomicInteger();
        BusinessAuthorizationService service = service((visitor, operation, rule, target) -> {
            calls.incrementAndGet();
            return BusinessPermissionDecision.allow(AuthorizationScope.all(), returnFields());
        });
        assertThrows(PermissionDeniedException.class, () -> service.authorize(
            request(teacher(), new BusinessOperation("order", "missing"), null)));
        assertThrows(PermissionDeniedException.class, () -> service.authorize(
            request(teacher(), new BusinessOperation("other", "view"), null)));
        assertThrows(PermissionDeniedException.class, () -> service.authorize(request(teacher(), RELATED, null)));
        assertEquals(1, calls.get());
    }

    @Test
    void intersects_paired_relation_facts_with_dynamic_scope_and_rechecks_revocation() {
        AtomicReference<RelationFactSet> current = new AtomicReference<RelationFactSet>(new RelationFactSet(Arrays.asList(
            pair("teacher-1", "group-a"), pair("teacher-2", "group-b"))));
        AtomicInteger calls = new AtomicInteger();
        RelationFactService facts = new RelationFactService((visitor, rule, target, limit) -> {
            calls.incrementAndGet();
            assertEquals("order-1", target);
            assertEquals("teacher", visitor.getIdentity().get().getIdentityCode());
            return current.get();
        }, 2);
        BusinessAuthorizationService service = service((visitor, operation, rule, target) ->
            BusinessPermissionDecision.allow(AuthorizationScope.equal("status", "OPEN"), returnFields()), facts);
        AuthorizationResult result = service.authorize(request(teacher(), RELATED, "order-1"));
        assertEquals(AccessDecision.ALLOW, result.getDecision());
        assertEquals(AuthorizationScope.Kind.AND, result.getScope().getKind());
        assertEquals("status", result.getScope().getChildren().get(0).getField());
        AuthorizationScope relation = result.getScope().getChildren().get(1);
        assertEquals(AuthorizationScope.Kind.OR, relation.getKind());
        assertEquals(AuthorizationScope.Kind.AND, relation.getChildren().get(0).getKind());
        assertEquals(AuthorizationScope.Kind.AND, relation.getChildren().get(1).getKind());
        assertEquals("ownerId", relation.getChildren().get(0).getChildren().get(0).getField());
        assertEquals("teacher-1", relation.getChildren().get(0).getChildren().get(0).getValues().get(0));
        assertEquals("groupId", relation.getChildren().get(0).getChildren().get(1).getField());
        assertEquals("group-a", relation.getChildren().get(0).getChildren().get(1).getValues().get(0));
        assertEquals("teacher-2", relation.getChildren().get(1).getChildren().get(0).getValues().get(0));
        assertEquals("group-b", relation.getChildren().get(1).getChildren().get(1).getValues().get(0));
        current.set(new RelationFactSet(Collections.emptyList()));
        assertThrows(PermissionDeniedException.class, () -> service.authorize(request(teacher(), RELATED, "order-1")));
        assertEquals(2, calls.get());
    }

    @Test
    void dynamic_denial_skips_fact_lookup_and_none_scope_cannot_expand() {
        AtomicInteger calls = new AtomicInteger();
        RelationFactService facts = new RelationFactService((visitor, rule, target, limit) -> {
            calls.incrementAndGet();
            return new RelationFactSet(Collections.singletonList(pair("teacher-1", "group-a")));
        }, 2);
        AuthorizationResult denied = service((visitor, operation, rule, target) -> BusinessPermissionDecision.deny(), facts)
            .authorize(request(teacher(), RELATED, null));
        assertEquals(AccessDecision.DENY, denied.getDecision());
        assertEquals(0, calls.get());
        AuthorizationResult empty = service((visitor, operation, rule, target) ->
            BusinessPermissionDecision.allow(AuthorizationScope.none(), returnFields()), facts)
            .authorize(request(teacher(), RELATED, null));
        assertEquals(AuthorizationScope.Kind.NONE, empty.getScope().getKind());
        assertEquals(1, calls.get());
    }

    @Test
    void narrows_each_dynamic_field_use_to_the_frozen_entity_limit() {
        Map<AuthorizationFields.Use, java.util.Collection<String>> requested =
            new EnumMap<AuthorizationFields.Use, java.util.Collection<String>>(AuthorizationFields.Use.class);
        requested.put(AuthorizationFields.Use.RETURN, Arrays.asList("status", "readonly", "unknown"));
        requested.put(AuthorizationFields.Use.WRITE,
            Arrays.asList("status", "readonly", "scoped", "immutable", "relation", "unknown"));
        requested.put(AuthorizationFields.Use.FILTER, Arrays.asList("status", "readonly", "relation", "unknown"));
        requested.put(AuthorizationFields.Use.SORT, Arrays.asList("status", "readonly", "relation", "unknown"));
        requested.put(AuthorizationFields.Use.STATS, Arrays.asList("status", "relation", "unknown"));
        requested.put(AuthorizationFields.Use.EXPAND, Arrays.asList("relation", "status", "unknown"));
        BusinessAuthorizationService service = service((visitor, operation, rule, target) ->
            BusinessPermissionDecision.allow(AuthorizationScope.all(), AuthorizationFields.of(requested)));
        AuthorizationFields fields = service.authorize(request(teacher(), VIEW, null)).getFields();
        assertEquals(Collections.singleton("status"), fields.snapshot().get(AuthorizationFields.Use.WRITE));
        assertEquals(Collections.singleton("status"), fields.snapshot().get(AuthorizationFields.Use.FILTER));
        assertEquals(Collections.singleton("status"), fields.snapshot().get(AuthorizationFields.Use.SORT));
        assertEquals(new LinkedHashSet<String>(Arrays.asList("status", "readonly")),
            fields.snapshot().get(AuthorizationFields.Use.RETURN));
        assertEquals(Collections.singleton("status"), fields.snapshot().get(AuthorizationFields.Use.STATS));
        assertEquals(Collections.singleton("relation"), fields.snapshot().get(AuthorizationFields.Use.EXPAND));
        assertFalse(fields.allows(AuthorizationFields.Use.WRITE, "readonly"));
        assertFalse(fields.allows(AuthorizationFields.Use.RETURN, "unknown"));
        assertThrows(UnsupportedOperationException.class,
            () -> fields.snapshot().get(AuthorizationFields.Use.RETURN).add("unknown"));
        requested.clear();
        assertTrue(fields.allows(AuthorizationFields.Use.RETURN, "status"));
    }

    @Test
    void null_or_denied_permission_never_becomes_allow_and_errors_propagate() {
        AuthorizationRequest request = request(teacher(), VIEW, null);
        AuthorizationResult denied = service((visitor, operation, rule, target) -> null).authorize(request);
        assertEquals(AccessDecision.DENY, denied.getDecision());
        assertFalse(denied.getFields().allows(AuthorizationFields.Use.RETURN, "status"));
        assertThrows(IllegalStateException.class, () -> service((visitor, operation, rule, target) -> {
            throw new IllegalStateException("权限来源不可用");
        }).authorize(request));
        assertThrows(NullPointerException.class, () -> BusinessPermissionDecision.allow(null, returnFields()));
        assertThrows(NullPointerException.class, () -> BusinessPermissionDecision.allow(AuthorizationScope.all(), null));
    }

    @Test
    void provider_authorizes_each_batch_target_and_preserves_input_order() {
        AtomicInteger calls = new AtomicInteger();
        List<String> targets = new ArrayList<String>();
        EntityDaoFactory factory = new EntityDaoFactory() {
            @Override
            public <T, ID> EntityDao<T, ID> scoped(EntityType<T, ID> entityType, EntityAccessScope scope) {
                return null;
            }
        };
        EntityDaoAuthorizationProvider provider = new EntityDaoAuthorizationProvider(
            service((visitor, operation, rule, target) -> {
                calls.incrementAndGet();
                targets.add(target);
                return BusinessPermissionDecision.allow(
                    AuthorizationScope.equal("ownerId", target), returnFields()
                );
            }),
            factory
        );
        VisitorContext visitor = teacher();

        EntityDaoBatchAuthorizationContext batch = provider.authorizeBatch(
            visitor, VIEW, Arrays.asList("order-2", "order-1")
        );

        assertEquals(2, calls.get());
        assertEquals(Arrays.asList("order-2", "order-1"), targets);
        assertEquals(Arrays.asList("order-2", "order-1"), batch.getTargetIds());
        assertSame(visitor, batch.getVisitor());
        assertEquals(VIEW, batch.getOperation());
        batch.requireResource("order");
    }

    @Test
    void provider_rejects_duplicate_or_denied_batch_target_before_returning_context() {
        EntityDaoFactory factory = new EntityDaoFactory() {
            @Override
            public <T, ID> EntityDao<T, ID> scoped(EntityType<T, ID> entityType, EntityAccessScope scope) {
                return null;
            }
        };
        EntityDaoAuthorizationProvider provider = new EntityDaoAuthorizationProvider(
            service((visitor, operation, rule, target) ->
                "order-2".equals(target)
                    ? BusinessPermissionDecision.deny()
                    : BusinessPermissionDecision.allow(AuthorizationScope.all(), returnFields())),
            factory
        );

        assertThrows(ValidationException.class, () -> provider.authorizeBatch(
            teacher(), VIEW, Arrays.asList("order-1", "order-1")
        ));
        assertThrows(PermissionDeniedException.class, () -> provider.authorizeBatch(
            teacher(), VIEW, Arrays.asList("order-1", "order-2")
        ));
    }

    @Test
    void provider_validates_batch_shape_before_authorizing_any_target() {
        AtomicInteger calls = new AtomicInteger();
        EntityDaoFactory factory = new EntityDaoFactory() {
            @Override
            public <T, ID> EntityDao<T, ID> scoped(EntityType<T, ID> entityType, EntityAccessScope scope) {
                return null;
            }
        };
        EntityDaoAuthorizationProvider provider = new EntityDaoAuthorizationProvider(
            service((visitor, operation, rule, target) -> {
                calls.incrementAndGet();
                return BusinessPermissionDecision.allow(AuthorizationScope.all(), returnFields());
            }),
            factory
        );

        assertThrows(ValidationException.class, () -> provider.authorizeBatch(
            teacher(), VIEW, Arrays.asList("order-1", "order-1")
        ));
        assertEquals(0, calls.get());

        List<String> oversized = new ArrayList<String>();
        for (int i = 0; i < EntityDaoBatchAuthorizationContext.DEFAULT_MAX_TARGETS + 1; i++) {
            oversized.add("order-" + i);
        }
        assertThrows(ValidationException.class, () -> provider.authorizeBatch(
            teacher(), VIEW, oversized
        ));
        assertEquals(0, calls.get());
    }

    @Test
    void provider_binds_one_authorized_request_before_creating_dao() {
        AtomicReference<EntityDaoAuthorizationContext> captured = new AtomicReference<EntityDaoAuthorizationContext>();
        EntityDaoFactory factory = new EntityDaoFactory() {
            @Override
            public <T, ID> EntityDao<T, ID> scoped(EntityType<T, ID> entityType, EntityAccessScope scope) {
                throw new UnsupportedOperationException("测试不应进入旧范围 DAO");
            }

            @Override
            public <T, ID> EntityDao<T, ID> scopedAuthorized(
                EntityType<T, ID> entityType,
                EntityDaoAuthorizationContext authorization
            ) {
                captured.set(authorization);
                return null;
            }
        };
        EntityDaoAuthorizationProvider provider = new EntityDaoAuthorizationProvider(
            service((visitor, operation, rule, target) ->
                BusinessPermissionDecision.allow(AuthorizationScope.equal("ownerId", "teacher-1"), returnFields())),
            factory
        );
        VisitorContext visitor = teacher();

        provider.open(EntityType.of(Order.class, String.class), visitor, VIEW, "order-1");

        assertEquals("order-1", captured.get().getTargetId());
        assertSame(visitor, captured.get().getVisitor());
        assertEquals("order", captured.get().getOperation().getResourceCode());
        assertEquals(AccessDecision.ALLOW, captured.get().getResult().getDecision());
    }

    @Test
    void provider_binds_all_authorized_requests_before_creating_batch_dao() {
        AtomicReference<EntityDaoBatchAuthorizationContext> captured =
            new AtomicReference<EntityDaoBatchAuthorizationContext>();
        EntityDaoFactory factory = new EntityDaoFactory() {
            @Override
            public <T, ID> EntityDao<T, ID> scoped(EntityType<T, ID> entityType, EntityAccessScope scope) {
                throw new UnsupportedOperationException("测试不应进入旧范围 DAO");
            }

            @Override
            public <T, ID> EntityDaoBatch<T, ID> scopedAuthorizedBatch(
                EntityType<T, ID> entityType,
                EntityDaoBatchAuthorizationContext authorization
            ) {
                captured.set(authorization);
                return null;
            }
        };
        EntityDaoAuthorizationProvider provider = new EntityDaoAuthorizationProvider(
            service((visitor, operation, rule, target) ->
                BusinessPermissionDecision.allow(AuthorizationScope.equal("ownerId", target), returnFields())),
            factory
        );

        provider.openBatch(
            EntityType.of(Order.class, String.class), teacher(), VIEW,
            Arrays.asList("order-2", "order-1")
        );

        assertEquals(Arrays.asList("order-2", "order-1"), captured.get().getTargetIds());
        assertEquals(2, captured.get().size());
    }

    @Test
    void provider_rejects_before_factory_when_business_authorization_denies() {
        AtomicBoolean factoryCalled = new AtomicBoolean(false);
        EntityDaoFactory factory = new EntityDaoFactory() {
            @Override
            public <T, ID> EntityDao<T, ID> scoped(EntityType<T, ID> entityType, EntityAccessScope scope) {
                throw new UnsupportedOperationException("测试不应进入旧范围 DAO");
            }

            @Override
            public <T, ID> EntityDao<T, ID> scopedAuthorized(
                EntityType<T, ID> entityType,
                EntityDaoAuthorizationContext authorization
            ) {
                factoryCalled.set(true);
                return null;
            }
        };
        EntityDaoAuthorizationProvider provider = new EntityDaoAuthorizationProvider(
            service((visitor, operation, rule, target) -> BusinessPermissionDecision.deny()),
            factory
        );

        assertThrows(PermissionDeniedException.class,
            () -> provider.open(EntityType.of(Order.class, String.class), teacher(), VIEW, "order-1"));
        assertFalse(factoryCalled.get());
    }

    private static BusinessAuthorizationService service(BusinessPermissionEvaluator evaluator) {
        return service(evaluator, null);
    }

    private static BusinessAuthorizationService service(BusinessPermissionEvaluator evaluator, RelationFactService facts) {
        ResourceDescriptor orderResource = new ResourceDescriptor(Order.class, "order", null, Collections.emptyList());
        ResourceDescriptor otherResource = new ResourceDescriptor(Other.class, "other", null, Collections.emptyList());
        List<BusinessOperationDeclaration> declarations = Arrays.asList(
            new BusinessOperationDeclaration(VIEW, "Project#view"),
            new BusinessOperationDeclaration(REFUND, "Project#refund"),
            new BusinessOperationDeclaration(RELATED, "Project#related"),
            new BusinessOperationDeclaration(new BusinessOperation("other", "view"), "Project#otherView"));
        BusinessOperationCatalog catalog = new BusinessOperationCatalog(Arrays.asList(orderResource, otherResource), declarations);
        List<EntityPolicyTemplate.Rule> rules = new ArrayList<EntityPolicyTemplate.Rule>();
        rules.add(new EntityPolicyTemplate.Rule("view", PolicyIdentityBranch.identity("teacher"), Collections.emptyList()));
        rules.add(new EntityPolicyTemplate.Rule("view", PolicyIdentityBranch.publicBranch(), Collections.emptyList()));
        rules.add(new EntityPolicyTemplate.Rule("refund", PolicyIdentityBranch.publicBranch(), Collections.emptyList()));
        rules.add(new EntityPolicyTemplate.Rule("related", PolicyIdentityBranch.identity("teacher"),
            Arrays.asList("owner", "group")));
        EntityPolicyTemplate template = new EntityPolicyTemplate("record", "Project#record", rules);
        Map<String, String> relations = new LinkedHashMap<String, String>();
        relations.put("owner", "ownerId");
        relations.put("group", "groupId");
        EntityPolicyBinding binding = new EntityPolicyBinding(Order.class, "record", "Order#binding", relations);
        BusinessIdentityRegistry identities = new BusinessIdentityRegistry(Collections.singletonList(
            new BusinessIdentityRegistration<TeacherIdentity>("teacher", TeacherIdentity.class,
                (subject, identityId) -> new TeacherIdentity(identityId))));
        EntityPolicyRegistry policies = new EntityPolicyRegistry(Arrays.asList(meta(orderResource), meta(otherResource)),
            Collections.singletonList(template), Collections.singletonList(binding), catalog, identities);
        return new BusinessAuthorizationService(catalog, policies, evaluator, facts);
    }

    private static EntityMeta meta(ResourceDescriptor resource) {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        for (String field : Arrays.asList("id", "ownerId", "groupId", "status")) {
            fields.put(field, new EntityFieldMeta(field, String.class, field, false, false, true, true));
        }
        fields.put("readonly", new EntityFieldMeta("readonly", String.class, "readonly",
            false, false, false, false, false, false, false));
        fields.put("scoped", new EntityFieldMeta("scoped", String.class, "scoped",
            false, false, true, true, true, true, false));
        fields.put("immutable", new EntityFieldMeta("immutable", String.class, "immutable",
            false, false, true, true, true, false, true));
        fields.put("relation", new EntityFieldMeta("relation", String.class, "relation",
            false, true, true, true, true, false, false));
        return new EntityMeta(resource.getEntityType(), resource, resource.getResourceCode(), "id", null, fields);
    }

    private static AuthorizationFields returnFields() {
        return AuthorizationFields.of(Collections.singletonMap(AuthorizationFields.Use.RETURN,
            Collections.singleton("status")));
    }

    private static Map<String, String> pair(String owner, String group) {
        Map<String, String> tuple = new LinkedHashMap<String, String>();
        tuple.put("owner", owner);
        tuple.put("group", group);
        return tuple;
    }

    private static AuthorizationRequest request(VisitorContext visitor, BusinessOperation operation, String target) {
        return new AuthorizationRequest(visitor, operation, target);
    }

    private static VisitorContext teacher() {
        return VisitorContext.authenticated(new AccessSubject("actor-1", AccessSubjectType.HUMAN),
            new TeacherIdentity("teacher-1"), "trusted-entry", null, null);
    }

    private static final class Order {
    }

    private static final class Other {
    }

    private static final class TeacherIdentity implements BusinessIdentity {
        private final String id;

        private TeacherIdentity(String id) { this.id = id; }

        @Override
        public String getIdentityCode() { return "teacher"; }

        @Override
        public String getIdentityId() { return id; }
    }
}
