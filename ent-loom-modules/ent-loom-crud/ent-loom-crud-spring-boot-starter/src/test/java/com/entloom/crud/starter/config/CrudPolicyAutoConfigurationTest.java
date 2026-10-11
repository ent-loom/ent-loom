package com.entloom.crud.starter.config;

import com.entloom.crud.annotations.EntPolicy;
import com.entloom.crud.annotations.EntPolicyRelation;
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
        .withBean(BusinessIdentityRegistration.class, this::teacherRegistration);

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
            .withBean(BusinessOperationDeclaration.class,
                () -> new BusinessOperationDeclaration(new BusinessOperation("order", "view"), "Project#view"))
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
            .withBean("orderViewDeclaration", BusinessOperationDeclaration.class,
                () -> new BusinessOperationDeclaration(new BusinessOperation("order", "view"), "Project#orderView"))
            .withBean("invoiceViewDeclaration", BusinessOperationDeclaration.class,
                () -> new BusinessOperationDeclaration(new BusinessOperation("invoice", "view"), "Project#invoiceView"))
            .withBean("classifiedViewDeclaration", BusinessOperationDeclaration.class,
                () -> new BusinessOperationDeclaration(new BusinessOperation("classified", "view"), "Project#classifiedView"))
            .withBean(EntityPolicyBinding.class, () -> new EntityPolicyBinding(
                JavaOrder.class, "record", "Project#invoiceBinding", Collections.singletonMap("owner", "ownerId")))
            .run(context -> {
                assertNotNull(context.getBean(BusinessOperationCatalog.class));
                EntityPolicyRegistry registry = context.getBean(EntityPolicyRegistry.class);
                assertEquals("ownerId", registry.requireDeclaration(AnnotatedOrder.class,
                    new BusinessOperation("order", "view"), PolicyIdentityBranch.identity("teacher"))
                    .getRelationFields().get("owner"));
                assertEquals("@EntPolicy:" + AnnotatedOrder.class.getName(), registry.requireDeclaration(
                    AnnotatedOrder.class, new BusinessOperation("order", "view"),
                    PolicyIdentityBranch.identity("teacher")).getBindingSource());
                assertEquals("Project#invoiceBinding", registry.requireDeclaration(JavaOrder.class,
                    new BusinessOperation("invoice", "view"), PolicyIdentityBranch.identity("teacher"))
                    .getBindingSource());
                assertEquals("@EntPolicy:" + OrderPolicyGroup.class.getName(), registry.requireDeclaration(
                    ClassifiedOrder.class, new BusinessOperation("classified", "view"),
                    PolicyIdentityBranch.identity("teacher")).getBindingSource());
            });
    }

    @Test
    void duplicate_annotation_and_java_binding_fails_with_both_sources() {
        runner.withBean(EntityMetaRegistry.class, () -> metas(AnnotatedOrder.class))
            .withBean(EntityPolicyTemplate.class, this::template)
            .withBean(BusinessOperationDeclaration.class,
                () -> new BusinessOperationDeclaration(new BusinessOperation("order", "view"), "Project#view"))
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
                assertTrue(message.contains("@EntPolicy:" + AnnotatedOrder.class.getName()), message);
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
    void duplicate_relation_role_in_annotation_fails_at_startup() {
        runner.withBean(EntityMetaRegistry.class, () -> metas(DuplicateRelations.class))
            .run(context -> {
                assertNotNull(context.getStartupFailure());
                Throwable cause = context.getStartupFailure();
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                assertTrue(cause.getMessage().contains("策略注解关系角色重复"));
                assertTrue(cause.getMessage().contains(DuplicateRelations.class.getName()));
            });
    }

    @Test
    void entity_and_classification_interface_cannot_both_select_policy() {
        runner.withBean(EntityMetaRegistry.class, () -> metas(ConflictingOrder.class))
            .run(context -> {
                assertNotNull(context.getStartupFailure());
                Throwable cause = context.getStartupFailure();
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                assertTrue(cause.getMessage().contains(ConflictingOrder.class.getName()));
                assertTrue(cause.getMessage().contains(OrderPolicyGroup.class.getName()));
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
                return RelationGraph.empty();
            }

            @Override
            public void validateOrThrow() {
            }
        };
    }

    @EntPolicy(template = "record", relations = @EntPolicyRelation(role = "owner", field = "ownerId"))
    private static final class AnnotatedOrder {
    }

    private static final class JavaOrder {
    }

    @EntPolicy(template = "record", relations = @EntPolicyRelation(role = "owner", field = "ownerId"))
    private interface OrderPolicyGroup {
    }

    private static final class ClassifiedOrder implements OrderPolicyGroup {
    }

    @EntPolicy(template = "record", relations = @EntPolicyRelation(role = "owner", field = "ownerId"))
    private static final class ConflictingOrder implements OrderPolicyGroup {
    }

    @EntPolicy(template = "record", relations = {
        @EntPolicyRelation(role = "owner", field = "ownerId"),
        @EntPolicyRelation(role = "owner", field = "id")
    })
    private static final class DuplicateRelations {
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
