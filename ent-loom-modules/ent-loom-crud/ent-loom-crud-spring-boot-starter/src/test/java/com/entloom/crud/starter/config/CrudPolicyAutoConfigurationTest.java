package com.entloom.crud.starter.config;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.StatsOperation;
import com.entloom.crud.core.governance.adapter.CrudBusinessAuthorizationAdapter;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityDaoAuthorizationContext;
import com.entloom.crud.core.capability.dao.EntityDaoAuthorizationProvider;
import com.entloom.crud.core.capability.dao.EntityDaoFactory;
import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.authorization.BusinessAuthorizationService;
import com.entloom.crud.core.governance.authorization.BusinessPermissionDecision;
import com.entloom.crud.core.governance.authorization.BusinessPermissionEvaluator;
import com.entloom.crud.core.governance.fact.RelationFactResolver;
import com.entloom.crud.core.governance.fact.RelationFactService;
import com.entloom.crud.core.governance.fact.RelationFactSet;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistration;
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
import com.entloom.crud.annotations.EntCrudEntity;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrudPolicyAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CrudPolicyAutoConfiguration.class))
        .withBean(BusinessIdentityRegistration.class, this::teacherRegistration)
        .withInitializer(context -> ((org.springframework.context.support.GenericApplicationContext) context).registerBean("testEntityDeclarations", CrudEntityDeclarations.class,
            () -> {
                EntityMetaRegistry registry = context.getBeanProvider(EntityMetaRegistry.class).getIfAvailable();
                return new CrudEntityDeclarations(registry == null ? Collections.emptyList()
                    : registry.getEntityMetas().stream().map(EntityMeta::getEntityType).toList());
            }));

    @Test
    void binds_project_grant_defaults_and_defaults_to_empty() {
        runner.run(context -> {
            var defaults = context.getBean(com.entloom.crud.core.governance.authorization.CrudGrantDefaults.class);
            assertTrue(defaults.getOwnerFields().isEmpty());
            assertTrue(defaults.getWildcardIdentities().isEmpty());
        });
        runner.withPropertyValues("ent.loom.crud.grant-defaults.owner-fields.teacher=ownerId",
            "ent.loom.crud.grant-defaults.wildcard-identities[0]=admin").run(context -> {
                assertEquals(null, context.getStartupFailure());
                var defaults = context.getBean(com.entloom.crud.core.governance.authorization.CrudGrantDefaults.class);
                assertEquals(Map.of("teacher", "ownerId"), defaults.getOwnerFields());
                assertEquals(java.util.Set.of("admin"), defaults.getWildcardIdentities());
            });
        runner.withPropertyValues("ent.loom.crud.grant-defaults.owner-fields.teacher=ownerId",
            "ent.loom.crud.grant-defaults.wildcard-identities[0]=teacher")
            .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void registers_one_business_authorization_service_and_denies_without_permission_evaluator() {
        var configured = runner.withBean(EntityMetaRegistry.class, () -> metas(JavaOrder.class))
            .withBean(BusinessOperationDeclaration.class,
                () -> new BusinessOperationDeclaration(new BusinessOperation("invoice", "view"), "Project#view"))
            .withBean(EntityPolicyTemplate.class, () -> new EntityPolicyTemplate("public", "Project#public",
                Collections.singletonList(new EntityPolicyTemplate.Rule("view", PolicyIdentityBranch.publicBranch(),
                    Collections.emptyList()))))
            .withBean(EntityPolicyBinding.class, () -> new EntityPolicyBinding(
                JavaOrder.class, "public", "Project#binding", Collections.emptyMap()));
        configured.run(context -> {
            assertEquals(1, context.getBeansOfType(BusinessAuthorizationService.class).size());
            assertEquals(1, context.getBeansOfType(CrudBusinessAuthorizationAdapter.class).size());
            assertEquals(AccessDecision.DENY, context.getBean(BusinessAuthorizationService.class)
                .authorize(new AuthorizationRequest(VisitorContext.anonymous("base", null, null),
                    new BusinessOperation("invoice", "view"), null)).getDecision());
        });
        configured.withBean(BusinessPermissionEvaluator.class, () -> (visitor, operation, rule, target) ->
            BusinessPermissionDecision.allow(AuthorizationScope.equal("id", "invoice-1"), AuthorizationFields.none()))
            .run(context -> assertEquals(AccessDecision.ALLOW, context.getBean(BusinessAuthorizationService.class)
                .authorize(new AuthorizationRequest(VisitorContext.anonymous("base", null, null),
                    new BusinessOperation("invoice", "view"), "invoice-1")).getDecision()));
    }

    @Test
    void registers_explicit_dao_authorization_provider_when_dao_factory_is_available() {
        runner.withBean(EntityMetaRegistry.class, () -> metas(JavaOrder.class))
            .withBean(BusinessOperationDeclaration.class,
                () -> new BusinessOperationDeclaration(new BusinessOperation("invoice", "view"), "Project#view"))
            .withBean(EntityPolicyTemplate.class, () -> new EntityPolicyTemplate("public", "Project#public",
                Collections.singletonList(new EntityPolicyTemplate.Rule("view", PolicyIdentityBranch.publicBranch(),
                    Collections.emptyList()))))
            .withBean(EntityPolicyBinding.class, () -> new EntityPolicyBinding(
                JavaOrder.class, "public", "Project#binding", Collections.emptyMap()))
            .withBean(EntityDaoFactory.class, () -> new EntityDaoFactory() {
                @Override
                public <T, ID> EntityDao<T, ID> scoped(EntityType<T, ID> entityType, EntityAccessScope scope) {
                    throw new UnsupportedOperationException("测试不应进入旧范围 DAO");
                }

                @Override
                public <T, ID> EntityDao<T, ID> scopedAuthorized(
                    EntityType<T, ID> entityType, EntityDaoAuthorizationContext authorization
                ) {
                    throw new UnsupportedOperationException("测试不执行 DAO");
                }
            })
            .run(context -> assertEquals(1, context.getBeansOfType(EntityDaoAuthorizationProvider.class).size()));
    }

    @Test
    void relation_resolver_is_wired_into_business_authorization() {
        runner.withBean(EntityMetaRegistry.class, () -> metas(AnnotatedOrder.class))
            .withBean(EntityPolicyTemplate.class, this::template)
            .withBean(BusinessPermissionEvaluator.class, () -> (visitor, operation, rule, target) ->
                BusinessPermissionDecision.allow(AuthorizationScope.all(), AuthorizationFields.none()))
            .withBean(RelationFactResolver.class, () -> (visitor, rule, target, limit) ->
                new RelationFactSet(Collections.singletonList(Collections.singletonMap("owner", "teacher-1"))))
            .run(context -> {
                assertEquals(1, context.getBeansOfType(RelationFactService.class).size());
                AuthorizationScope scope = context.getBean(BusinessAuthorizationService.class)
                    .authorize(new AuthorizationRequest(VisitorContext.authenticated(
                        new AccessSubject("actor-1", AccessSubjectType.HUMAN),
                        new TeacherIdentity("teacher-1"), "base", null, null),
                        new BusinessOperation("order", "view"), null)).getScope();
                assertEquals(AuthorizationScope.Kind.EQUAL, scope.getKind());
                assertEquals("ownerId", scope.getField());
                assertEquals("teacher-1", scope.getValues().get(0));
            });
    }

    @Test
    void compiles_project_crud_mapping_beans() {
        runner.withBean(EntityMetaRegistry.class, () -> metas(JavaOrder.class))
            .withBean(BusinessOperationDeclaration.class,
                () -> new BusinessOperationDeclaration(new BusinessOperation("invoice", "stats"), "Project#stats"))
            .withBean(CrudOperationMapping.class,
                () -> new CrudOperationMapping(CrudOperationKey.of(StatsOperation.QUERY), null,
                    new BusinessOperation("invoice", "stats"), "Project#statsMapping"))
            .run(context -> assertEquals(new BusinessOperation("invoice", "stats"),
                context.getBean(CrudOperationMappingRegistry.class)
                    .require("invoice", CrudOperationKey.of(StatsOperation.QUERY), null)));
    }

    @Test
    void compiles_annotation_and_java_bindings_through_one_registry() {
        runner.withBean(EntityMetaRegistry.class, () -> metas(AnnotatedOrder.class, JavaOrder.class, ClassifiedOrder.class))
            .withBean(EntityPolicyTemplate.class, this::template)
            .withBean("invoiceViewDeclaration", BusinessOperationDeclaration.class,
                () -> new BusinessOperationDeclaration(new BusinessOperation("invoice", "view"), "Project#invoiceView"))
            .withBean(EntityPolicyBinding.class, () -> new EntityPolicyBinding(
                JavaOrder.class, "record", "Project#invoiceBinding", Collections.singletonMap("owner", "ownerId")))
            .run(context -> {
                assertNotNull(context.getBean(BusinessOperationCatalog.class));
                EntityPolicyRegistry registry = context.getBean(EntityPolicyRegistry.class);
                assertEquals("ownerId", registry.requireDeclaration(AnnotatedOrder.class,
                    new BusinessOperation("order", "view"), PolicyIdentityBranch.identity("teacher"))
                    .getRelationFields().get("owner"));
                assertEquals("@EntCrudEntity(order).policy", registry.requireDeclaration(
                    AnnotatedOrder.class, new BusinessOperation("order", "view"),
                    PolicyIdentityBranch.identity("teacher")).getBindingSource());
                assertEquals("Project#invoiceBinding", registry.requireDeclaration(JavaOrder.class,
                    new BusinessOperation("invoice", "view"), PolicyIdentityBranch.identity("teacher"))
                    .getBindingSource());
                assertEquals("@EntCrudEntity(classified).policy", registry.requireDeclaration(
                    ClassifiedOrder.class, new BusinessOperation("classified", "view"),
                    PolicyIdentityBranch.identity("teacher")).getBindingSource());
            });
    }

    @Test
    void duplicate_annotation_and_java_binding_fails_with_both_sources() {
        runner.withBean(EntityMetaRegistry.class, () -> metas(AnnotatedOrder.class))
            .withBean(EntityPolicyTemplate.class, this::template)
            .withBean(EntityPolicyBinding.class, () -> new EntityPolicyBinding(
                AnnotatedOrder.class, "record", "Project#override", Collections.singletonMap("owner", "ownerId")))
            .run(context -> {
                assertNotNull(context.getStartupFailure());
                String message = context.getStartupFailure().toString();
                Throwable cause = context.getStartupFailure();
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                    message += " " + cause.getMessage();
                }
                assertTrue(message.contains("@EntCrudEntity(order).policy"), message);
                assertTrue(message.contains("Project#override"), message);
            });
    }

    @Test
    void missing_meta_registry_does_not_compile_policy_but_keeps_identity_registration() {
        runner.run(context -> {
            assertFalse(context.containsBean("businessOperationCatalog"));
            assertFalse(context.containsBean("entityPolicyRegistry"));
            assertFalse(context.containsBean("crudOperationMappingRegistry"));
            assertFalse(context.containsBean("businessAuthorizationService"));
            assertFalse(context.containsBean("crudBusinessAuthorizationAdapter"));
            assertTrue(context.getBean(BusinessIdentityRegistry.class).snapshot().containsKey("teacher"));
        });
    }

    @Test
    void policy_and_mvc_auto_configuration_share_one_identity_registry() {
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                CrudPolicyAutoConfiguration.class, CrudAccessWebAutoConfiguration.class))
            .withBean(BusinessIdentityRegistration.class, this::teacherRegistration)
            .run(context -> {
                assertEquals(1, context.getBeansOfType(BusinessIdentityRegistry.class).size());
                assertTrue(context.getBean(BusinessIdentityRegistry.class).snapshot().containsKey("teacher"));
            });
    }

    @Test
    void entity_grants_work_without_global_evaluator_and_cannot_be_bypassed_by_one() {
        var configured = runner.withBean(EntityMetaRegistry.class, () -> metas(GrantedOrder.class))
            .withPropertyValues("ent.loom.crud.grant-defaults.owner-fields.teacher=ownerId");
        configured.run(context -> {
            assertEquals(null, context.getStartupFailure());
            assertTrue(context.getBeansOfType(BusinessPermissionEvaluator.class).isEmpty());
            var request = new AuthorizationRequest(VisitorContext.authenticated(
                new AccessSubject("subject", AccessSubjectType.HUMAN), new TeacherIdentity("teacher-7"), "base", null, null),
                new BusinessOperation("order", "view"), null);
            var result = context.getBean(BusinessAuthorizationService.class).authorize(request);
            assertEquals(AccessDecision.ALLOW, result.getDecision());
            assertEquals("ownerId", result.getScope().getField());
            assertEquals(java.util.List.of("teacher-7"), result.getScope().getValues());
        });
        configured.withBean(BusinessPermissionEvaluator.class, () -> (visitor, operation, rule, target) ->
            BusinessPermissionDecision.allow(AuthorizationScope.all(), rule.getFieldLimit()))
            .run(context -> {
                assertEquals(null, context.getStartupFailure());
                var request = new AuthorizationRequest(VisitorContext.authenticated(
                    new AccessSubject("subject", AccessSubjectType.HUMAN), new TeacherIdentity("teacher-7"), "base", null, null),
                    new BusinessOperation("order", "view"), null);
                assertEquals("ownerId", context.getBean(BusinessAuthorizationService.class).authorize(request).getScope().getField());
            });
    }

    @EntCrudEntity(name = "order")
    @com.entloom.crud.annotations.EntCrudGrant(identities = "teacher", actions = "view")
    private static final class GrantedOrder {}

    private BusinessIdentityRegistration<TeacherIdentity> teacherRegistration() {
        return new BusinessIdentityRegistration<>("teacher", TeacherIdentity.class,
            (subject, identityId) -> subject.getType() == AccessSubjectType.HUMAN
                ? new TeacherIdentity(identityId) : null);
    }

    private EntityPolicyTemplate template() {
        return new EntityPolicyTemplate("record", "Project#record", Collections.singletonList(
            new EntityPolicyTemplate.Rule("view", PolicyIdentityBranch.identity("teacher"),
                Collections.singleton("owner"))));
    }

    static EntityMetaRegistry metas(Class<?>... types) {
        java.util.List<EntityMeta> metas = new java.util.ArrayList<EntityMeta>();
        for (Class<?> type : types) {
            String resource = type == JavaOrder.class ? "invoice"
                : type == ClassifiedOrder.class ? "classified" : "order";
            Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
            fields.put("id", new EntityFieldMeta("id", String.class, "id", false, false, true, true));
            fields.put("ownerId", new EntityFieldMeta("ownerId", String.class, "owner_id", false, false, true, true));
            metas.add(new EntityMeta(type,
                new ResourceDescriptor(type, resource, null, Collections.emptyList()),
                resource, "id", null, fields));
        }
        return new EntityMetaRegistry() {
            @Override
            public EntityMeta getEntityMeta(Class<?> entityType) {
                return metas.stream().filter(meta -> meta.getEntityType() == entityType).findFirst().orElse(null);
            }

            @Override
            public ResourceDescriptor getResourceDescriptor(Class<?> entityType) {
                EntityMeta meta = getEntityMeta(entityType);
                return meta == null ? null : meta.getResourceDescriptor();
            }

            @Override
            public Collection<EntityMeta> getEntityMetas() {
                return metas;
            }

            @Override
            public RelationGraph getRelationGraph(Class<?> rootType) {
                var edge = new com.entloom.crud.core.runtime.meta.RelationEdge();
                edge.setFromEntity(rootType);
                edge.setToEntity(rootType);
                edge.setRelationField("owner");
                edge.setFromField("ownerId");
                edge.setToField("id");
                edge.setCardinality(com.entloom.meta.enums.RelationCardinality.MANY_TO_ONE);
                edge.setScope(com.entloom.crud.enums.RelationScope.LOCAL_DB);
                return RelationGraph.of(Collections.singletonList(edge));
            }

            @Override
            public void validateOrThrow() {
            }
        };
    }

    @EntCrudEntity(name = "order", policy = "record")
    private static final class AnnotatedOrder {
    }

    private static final class JavaOrder {
    }

    private interface OrderPolicyGroup {
    }

    @EntCrudEntity(name = "classified", policy = "record")
    private static final class ClassifiedOrder implements OrderPolicyGroup {
    }

    private static final class TeacherIdentity implements BusinessIdentity {
        private final String id;

        private TeacherIdentity(String id) {
            this.id = id;
        }

        @Override
        public String getIdentityCode() {
            return "teacher";
        }

        @Override
        public String getIdentityId() {
            return id;
        }
    }
}
