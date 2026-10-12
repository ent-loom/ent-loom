package com.entloom.crud.starter.config;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.api.access.BusinessIdentityDefinition;
import com.entloom.crud.api.access.BusinessIdentityIdType;
import com.entloom.crud.api.access.ConfiguredBusinessIdentity;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.authorization.BusinessAuthorizationService;
import com.entloom.crud.core.governance.authorization.BusinessPermissionDecision;
import com.entloom.crud.core.governance.authorization.BusinessPermissionEvaluator;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistration;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.core.governance.policy.CrudOperationMappingRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyBinding;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.governance.policy.PolicyIdentityBranch;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfiguredAccessPoliciesTest {
    private static final String YAML = """
        ent:
          loom:
            crud:
              access:
                policies:
                  order:
                    owner:
                      actions: [view]
                entities:
                  order:
                    fields:
                      owner: owner
                    operations:
                      view: [PAGE]
                relations:
                  owner:
                    field: ownerId
                field-plans:
                  owner:
                    RETURN: [id]
                    FILTER: [ownerId]
        """;
    private final BusinessOperation view = new BusinessOperation("order", "view");

    private static final BusinessIdentityDefinition OWNER = new BusinessIdentityDefinition("owner", "memberId", BusinessIdentityIdType.STRING);
    private static final String CONFIGURED_YAML = YAML.replace("access:\n", "access:\n        identities:\n          owner:\n            id-field: memberId\n            id-type: STRING\n");

    private ApplicationContextRunner configuredRunner(String yaml) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CrudPolicyAutoConfiguration.class))
            .withBean(EntityMetaRegistry.class, () -> CrudPolicyAutoConfigurationTest.metas(Order.class))
            .withBean(BusinessIdentityRegistration.class, () -> new BusinessIdentityRegistration<>(
                "owner", ConfiguredBusinessIdentity.class, (subject, id) -> OWNER.snapshot(id)))
            .withInitializer(context -> {
                try {
                    new YamlPropertySourceLoader().load("配置身份策略", new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)))
                        .forEach(source -> context.getEnvironment().getPropertySources().addFirst(source));
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
    }

    @Test
    void 配置身份读取声明的Id字段并进入统一关系授权链() {
        configuredRunner(CONFIGURED_YAML).run(context -> {
            assertThat(context).hasNotFailed();
            AccessSubject subject = new AccessSubject("actor", AccessSubjectType.HUMAN);
            var identity = context.getBean(BusinessIdentityRegistry.class).resolve(subject, "owner", "42");
            VisitorContext visitor = VisitorContext.authenticated(subject, identity, "merchant", null, null);
            var result = context.getBean(BusinessAuthorizationService.class).authorize(new AuthorizationRequest(visitor, view, null));
            assertThat(result.getDecision()).isEqualTo(AccessDecision.ALLOW);
            assertThat(result.getScope().getField()).isEqualTo("ownerId");
            assertThat(result.getScope().getValues()).containsExactly("42");
            context.getBean(CrudAccessProperties.class).getIdentities().clear();
            assertThat(context.getBean(ConfiguredBusinessIdentities.class).definitions()).containsOnlyKeys("owner");
            assertThat(context.getBean(BusinessAuthorizationService.class).authorize(new AuthorizationRequest(visitor, view, null))
                .getDecision()).isEqualTo(AccessDecision.ALLOW);
        });
    }

    @Test
    void 配置身份的未知属性及实体Id类型不一致启动失败() {
        configuredRunner(CONFIGURED_YAML.replace("field: ownerId", "field: ownerId\n            identity-field: missingId"))
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("未知身份属性");
            });
        configuredRunner(CONFIGURED_YAML.replace("id-type: STRING", "id-type: LONG")).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("身份 ID 类型与实体关系字段不匹配");
        });
    }

    private ApplicationContextRunner runner(String yaml) {
        return runner(yaml, CrudPolicyAutoConfigurationTest.metas(Order.class));
    }

    private ApplicationContextRunner runner(String yaml, EntityMetaRegistry metas) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CrudPolicyAutoConfiguration.class))
            .withBean(EntityMetaRegistry.class, () -> metas)
            .withBean(BusinessIdentityRegistration.class, () -> new BusinessIdentityRegistration<>(
                "owner", OwnerIdentity.class, (subject, id) -> new OwnerIdentity(id)))
            .withInitializer(context -> {
                try {
                    var resource = new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
                    new YamlPropertySourceLoader().load("业务策略", resource).forEach(
                        source -> context.getEnvironment().getPropertySources().addFirst(source));
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException(ex);
                }
            });
    }

    @Test
    void 省略操作目录仅按策略标准动作推导Crud映射() {
        String yaml = YAML.replace("actions: [view]", "actions: [view, create, update, delete]")
            .replace("            operations:\n              view: [PAGE]\n", "");
        runner(yaml).run(context -> {
            assertThat(context).hasNotFailed();
            var mappings = context.getBean(CrudOperationMappingRegistry.class);
            for (QueryOperation operation : QueryOperation.values()) {
                assertThat(mappings.require("order", CrudOperationKey.of(operation), null)).isEqualTo(view);
            }
            for (CommandOperation operation : List.of(CommandOperation.CREATE, CommandOperation.UPDATE, CommandOperation.DELETE)) {
                assertThat(mappings.require("order", CrudOperationKey.of(operation), null))
                    .isEqualTo(new BusinessOperation("order", operation.name().toLowerCase(java.util.Locale.ROOT)));
            }
            assertThat(mappings.snapshot()).hasSize(7);
            assertThatThrownBy(() -> mappings.require("order", CrudOperationKey.of(CommandOperation.UPDATE_BATCH), null))
                .hasMessageContaining("未映射");
            assertThatThrownBy(() -> mappings.require("order", CrudOperationKey.of(CommandOperation.SAVE_OR_UPDATE), null))
                .hasMessageContaining("未映射");
        });
    }

    @Test
    void 显式操作目录整体覆盖缺省且业务动作不猜测映射() {
        runner(YAML).run(context -> {
            assertThat(context).hasNotFailed();
            var mappings = context.getBean(CrudOperationMappingRegistry.class);
            assertThat(mappings.snapshot()).hasSize(1);
            assertThatThrownBy(() -> mappings.require("order", CrudOperationKey.of(QueryOperation.LIST), null))
                .hasMessageContaining("未映射");
        });
        runner(YAML.replace("actions: [view]", "actions: [ship]")
            .replace("            operations:\n              view: [PAGE]\n", "")).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("业务动作须显式声明 operations");
            });
        runner(YAML.replace("actions: [view]", "actions: [ship]").replace("view: [PAGE]", "ship: []"))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(CrudOperationMappingRegistry.class).snapshot()).isEmpty();
                assertThat(context.getBean(BusinessAuthorizationService.class).authorize(new AuthorizationRequest(
                    request().getVisitor(), new BusinessOperation("order", "ship"), null)).getDecision())
                    .isEqualTo(AccessDecision.ALLOW);
            });
        runner(YAML.replace("operations:\n              view: [PAGE]", "operations: {}"))
            .run(context -> {
                // Spring 不保留空对象，关闭 CRUD 映射须使用 view: []。
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(CrudOperationMappingRegistry.class).snapshot()).hasSize(4);
            });
    }

    @Test
    void 同一策略模板复用时各实体字段方案独立且优先显式选择() {
        String yaml = """
            ent.loom.crud.access:
              policies:
                owned-data:
                  owner:
                    actions: [view, update]
              policy-bindings:
                owned-data: [order, note]
              entities:
                order:
                  fields:
                    owner: order-edit
                  operations:
                    view: [LIST]
                    update: [UPDATE]
                note:
                  operations:
                    view: [LIST]
                    update: [UPDATE]
              relations:
                owner:
                  field: ownerId
              field-plans:
                order:
                  WRITE: [ownerId]
                order-edit:
                  WRITE: [amount]
                note:
                  WRITE: [title]
            """;
        EntityMeta order = editableMeta(Order.class, "order", "amount");
        EntityMeta note = editableMeta(Note.class, "note", "title");
        EntityMetaRegistry metas = mock(EntityMetaRegistry.class);
        when(metas.getEntityMetas()).thenReturn(List.of(order, note));
        runner(yaml, metas).run(context -> {
            assertThat(context).hasNotFailed();
            var service = context.getBean(BusinessAuthorizationService.class);
            for (String resource : List.of("order", "note")) {
                var result = service.authorize(new AuthorizationRequest(request().getVisitor(),
                    new BusinessOperation(resource, "update"), null));
                assertThat(result.getFields().snapshot().get(AuthorizationFields.Use.WRITE))
                    .containsExactly(resource.equals("order") ? "amount" : "title");
                assertThat(result.getFields().allows(AuthorizationFields.Use.RETURN,
                    resource.equals("order") ? "amount" : "title")).isTrue();
                assertThat(result.getScope().getField()).isEqualTo("ownerId");
                assertThat(result.getScope().getValues()).containsExactly("42");
            }
        });
    }

    @Test
    void 批量绑定无需实体声明且复用默认操作与可信关系() {
        String yaml = YAML.replace("        entities:\n          order:\n            fields:\n              owner: owner\n            operations:\n              view: [PAGE]\n", "")
            + "        policy-bindings:\n          order: [order]\n";
        EntityMetaRegistry metas = mock(EntityMetaRegistry.class);
        when(metas.getEntityMetas()).thenReturn(List.of(
            editableMeta(Order.class, "order", "amount"), editableMeta(Note.class, "note", "title")));
        runner(yaml, metas).run(context -> {
            assertThat(context).hasNotFailed();
            var service = context.getBean(BusinessAuthorizationService.class);
            var result = service.authorize(request());
            assertThat(result.getDecision()).isEqualTo(AccessDecision.ALLOW);
            assertThat(result.getScope().getField()).isEqualTo("ownerId");
            assertThat(result.getScope().getValues()).containsExactly("42");
            var mapping = context.getBean(CrudOperationMappingRegistry.class);
            assertThat(mapping.snapshot()).hasSize(4);
            assertThat(mapping.require("order", CrudOperationKey.of(QueryOperation.PAGE), null)).isEqualTo(view);
            var rule = context.getBean(EntityPolicyRegistry.class)
                .requireDeclaration(view, PolicyIdentityBranch.identity("owner"));
            assertThat(rule.getBindingSource()).isEqualTo("ent.loom.crud.access.policy-bindings.order");
            assertThatThrownBy(() -> service.authorize(new AuthorizationRequest(request().getVisitor(),
                new BusinessOperation("note", "view"), null))).hasMessageContaining("未注册");
            var properties = context.getBean(CrudAccessProperties.class);
            assertThat(properties.getEntities()).isEmpty();
            properties.getPolicyBindings().clear();
            assertThat(service.authorize(request()).getDecision()).isEqualTo(AccessDecision.ALLOW);
        });
    }

    @Test
    void 批量绑定可叠加实体差异并接受一致的显式策略() {
        for (String declaration : List.of("", "            policy: order\n",
            "            exclude-scope-fields: [amount]\n")) {
            String yaml = YAML.replace("            fields:\n", declaration + "            fields:\n")
                + "        policy-bindings:\n          order: [order]\n";
            runner(yaml).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(CrudOperationMappingRegistry.class).snapshot()).hasSize(1);
                var result = context.getBean(BusinessAuthorizationService.class).authorize(request());
                assertThat(result.getFields().snapshot().get(AuthorizationFields.Use.RETURN)).containsExactly("id");
                assertThat(result.getScope().getValues()).containsExactly("42");
            });
        }
    }

    @Test
    void 仅归属字段例外的实体有批量绑定时仍生成策略能力() {
        String yaml = YAML.replace("            fields:\n              owner: owner\n            operations:\n              view: [PAGE]\n",
            "            exclude-scope-fields: [amount]\n")
            .replace("          order:\n            owner:", "          owned-data:\n            owner:")
            + "        policy-bindings:\n          owned-data: [order]\n";
        runner(yaml).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CrudOperationMappingRegistry.class).snapshot()).hasSize(4);
            assertThat(context.getBean(BusinessAuthorizationService.class).authorize(request()).getDecision())
                .isEqualTo(AccessDecision.ALLOW);
        });
    }

    @Test
    void 批量绑定的未知引用空列表重复实体及策略冲突均启动失败() {
        Map<String, String> invalid = Map.of(
            "          unknown: [order]\n", "批量绑定未知策略",
            "          order: [unknown]\n", "策略绑定未知实体",
            "          order: []\n", "不能为空",
            "          order: [order, order]\n", "重复",
            "          order: [order]\n          other: [order]\n", "实体重复批量绑定",
            "          other: [order]\n", "实体策略与批量绑定冲突",
            "          order: [' ']\n", "名称不能为空");
        String yaml = YAML.replace("        entities:\n",
            "          other:\n            owner:\n              actions: [view]\n        entities:\n")
            .replace("            fields:\n", "            policy: order\n            fields:\n");
        invalid.forEach((binding, message) -> runner(yaml + "        policy-bindings:\n" + binding)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining(message);
            }));
    }

    @Test
    void 批量绑定与Java绑定冲突仍由统一注册链拒绝() {
        runner(YAML + "        policy-bindings:\n          order: [order]\n")
            .withBean(EntityPolicyBinding.class, () -> new EntityPolicyBinding(
                Order.class, "order", "项目Java", Map.of("owner", "ownerId"))).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("项目Java")
                    .hasStackTraceContaining("ent.loom.crud.access.policy-bindings.order");
            });
    }

    @Test
    void 同一实体的身份分支可选择不同字段方案且不会互相扩大() {
        String yaml = YAML.replace("actions: [view]", "actions: [view]\n            reviewer:\n              actions: [view]\n              scope: owner")
            .replace("            operations:\n              view: [PAGE]\n", "")
            .replace("owner: owner", "owner: owner\n              reviewer: review")
            + "          review:\n            RETURN: [ownerId]\n";
        runner(yaml).withBean("reviewerRegistration", BusinessIdentityRegistration.class,
            () -> new BusinessIdentityRegistration<>("reviewer", ReviewerIdentity.class,
                (subject, id) -> new ReviewerIdentity(id))).run(context -> {
                assertThat(context).hasNotFailed();
                var service = context.getBean(BusinessAuthorizationService.class);
                var ownerFields = service.authorize(request()).getFields();
                var reviewer = VisitorContext.authenticated(new AccessSubject("reviewer", AccessSubjectType.HUMAN),
                    new ReviewerIdentity("42"), "merchant", null, null);
                var reviewerFields = service.authorize(new AuthorizationRequest(reviewer, view, null)).getFields();
                assertThat(ownerFields.snapshot().get(AuthorizationFields.Use.RETURN)).containsExactly("id");
                assertThat(reviewerFields.snapshot().get(AuthorizationFields.Use.RETURN)).containsExactly("ownerId");
                assertThat(reviewerFields.allows(AuthorizationFields.Use.WRITE, "ownerId")).isFalse();
            });
    }

    @Test
    void 实体字段方案未知引用和未使用身份均拒绝() {
        for (String invalid : List.of(
            YAML.replace("owner: owner", "owner: missing"),
            YAML.replace("owner: owner", "unused: owner"),
            YAML.replace("owner: owner", "owner:"))) {
            runner(invalid).run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void 未声明字段方案缺省返回普通字段且动态约束继续生效() {
        String yaml = YAML.substring(0, YAML.indexOf("        field-plans:"))
            .replace("            fields:\n              owner: owner\n", "");
        runner(yaml).run(context -> {
            assertThat(context).hasNotFailed();
            var result = context.getBean(BusinessAuthorizationService.class).authorize(request());
            assertThat(result.getFields().allows(AuthorizationFields.Use.RETURN, "id")).isTrue();
            assertThat(result.getFields().allows(AuthorizationFields.Use.RETURN, "ownerId")).isTrue();
            assertThat(result.getFields().allows(AuthorizationFields.Use.FILTER, "ownerId")).isTrue();
            assertThat(result.getFields().allows(AuthorizationFields.Use.WRITE, "ownerId")).isFalse();
            assertThat(result.getScope().getField()).isEqualTo("ownerId");
        });
        runner(yaml).withBean(BusinessPermissionEvaluator.class, () -> (visitor, op, rule, target) ->
            BusinessPermissionDecision.allow(AuthorizationScope.all(),
                AuthorizationFields.of(Map.of(AuthorizationFields.Use.RETURN, List.of("id")))))
            .run(context -> {
                assertThat(context).hasNotFailed();
                var result = context.getBean(BusinessAuthorizationService.class).authorize(request());
                assertThat(result.getFields().allows(AuthorizationFields.Use.RETURN, "ownerId")).isFalse();
                assertThat(result.getFields().allows(AuthorizationFields.Use.RETURN, "id")).isTrue();
            });
        runner(YAML.replace("RETURN: [id]", "RETURN: []")).run(context -> {
            assertThat(context).hasNotFailed();
            var result = context.getBean(BusinessAuthorizationService.class).authorize(request());
            assertThat(result.getFields().allows(AuthorizationFields.Use.RETURN, "id")).isFalse();
        });
    }

    @Test
    void Yaml筛选省略显式列表和空列表均保留约定() {
        for (String declaration : List.of("", "FILTER: [ownerId]", "FILTER: []")) {
            String yaml = YAML.replace("FILTER: [ownerId]", declaration);
            runner(yaml).run(context -> {
                assertThat(context).hasNotFailed();
                var result = context.getBean(BusinessAuthorizationService.class).authorize(request());
                assertThat(result.getFields().allows(AuthorizationFields.Use.FILTER, "id"))
                    .isEqualTo(declaration.isEmpty());
                assertThat(result.getFields().allows(AuthorizationFields.Use.FILTER, "ownerId"))
                    .isEqualTo(declaration.equals("FILTER: [ownerId]"));
                assertThat(result.getScope().getField()).isEqualTo("ownerId");
                assertThat(result.getScope().getValues()).containsExactly("42");
            });
        }
    }

    @Test
    void 主Yaml按缺省编译动作实体字段和可信身份关系() {
        runner(YAML).run(context -> {
            assertThat(context).hasNotFailed();
            var mapping = context.getBean(CrudOperationMappingRegistry.class);
            assertThat(mapping.require("order", CrudOperationKey.of(QueryOperation.PAGE), null)).isEqualTo(view);
            var rule = context.getBean(EntityPolicyRegistry.class).requireDeclaration(view, PolicyIdentityBranch.identity("owner"));
            assertThat(rule.getBindingSource()).contains("ent.loom.crud.access.entities.order");
            var result = context.getBean(BusinessAuthorizationService.class).authorize(request());
            assertThat(result.getDecision()).isEqualTo(AccessDecision.ALLOW);
            assertThat(result.getScope().getKind()).isEqualTo(AuthorizationScope.Kind.EQUAL);
            assertThat(result.getScope().getField()).isEqualTo("ownerId");
            assertThat(result.getScope().getValues()).containsExactly("42");
            assertThat(result.getFields().allows(AuthorizationFields.Use.RETURN, "id")).isTrue();
            assertThat(result.getFields().allows(AuthorizationFields.Use.WRITE, "id")).isFalse();
            assertThatThrownBy(() -> context.getBean(BusinessAuthorizationService.class).authorize(new AuthorizationRequest(
                VisitorContext.anonymous("merchant", null, null), view, null))).hasMessageContaining("未声明");
            // 编译后修改可绑定属性不影响已冻结的执行能力。
            context.getBean(CrudAccessProperties.class).getFieldPlans().clear();
            assertThat(context.getBean(BusinessAuthorizationService.class).authorize(request()).getFields()
                .allows(AuthorizationFields.Use.RETURN, "id")).isTrue();
        });
    }

    @Test
    void 空映射只登记业务动作供授权Dao使用() {
        runner(YAML.replace("view: [PAGE]", "view: []")).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(BusinessAuthorizationService.class).authorize(request()).getDecision())
                .isEqualTo(AccessDecision.ALLOW);
            assertThatThrownBy(() -> context.getBean(CrudOperationMappingRegistry.class)
                .require("order", CrudOperationKey.of(QueryOperation.PAGE), null)).hasMessageContaining("映射");
        });
    }

    @Test
    void 显式身份属性时仍可缺省实体关系字段() {
        runner(YAML.replace("field: ownerId", "identity-field: ownerId")).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(BusinessAuthorizationService.class).authorize(request()).getScope().getField())
                .isEqualTo("ownerId");
        });
    }

    @Test
    void 身份关系属性不是标量则启动失败() {
        runner(YAML.replace("field: ownerId", "identity-field: memberships")).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("身份属性必须为标量");
        });
    }

    @Test
    void 动态权限拒绝或收窄不能被Yaml重新放开() {
        runner(YAML).withBean(BusinessPermissionEvaluator.class, () -> (visitor, op, rule, target) ->
            BusinessPermissionDecision.deny()).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(BusinessAuthorizationService.class).authorize(request()).getDecision())
                    .isEqualTo(AccessDecision.DENY);
            });
        runner(YAML).withBean(BusinessPermissionEvaluator.class, () -> (visitor, op, rule, target) ->
            BusinessPermissionDecision.allow(AuthorizationScope.equal("id", "1"),
                AuthorizationFields.of(Map.of(AuthorizationFields.Use.RETURN, List.of("id", "ownerId")))))
            .run(context -> {
                assertThat(context).hasNotFailed();
                var result = context.getBean(BusinessAuthorizationService.class).authorize(request());
                assertThat(result.getScope().getKind()).isEqualTo(AuthorizationScope.Kind.AND);
                assertThat(result.getFields().allows(AuthorizationFields.Use.RETURN, "ownerId")).isFalse();
            });
    }

    @Test
    void 未知键身份动作字段关系和缺省方案均启动失败() {
        for (String invalid : List.of(
            YAML.replace("actions:", "actons:"),
            YAML.replace("order:\n            fields", "unknown:\n            fields"),
            YAML.replace("operations:\n", "policy: unknown\n            operations:\n"),
            YAML.replace("actions: [view]", "actions: [view, view]"),
            YAML.replace("actions: [view]", "actions: [ship]"),
            YAML.replace("owner:\n              actions", "unknown:\n              actions"),
            YAML.replace("RETURN: [id]", "RETURN: [unknown]"),
            YAML.replace("RETURN: [id]", "RETURN: [id, id]"),
            YAML.replace("field: ownerId", "field: unknown"),
            YAML.replace("field: ownerId", "identity-field: unknown"),
            YAML.replace("actions: [view]", "actions: [view]\n              scope: unknown"),
            YAML.replace("actions: [view]", "actions: [view]\n              fields: unknown"),
            YAML.replace("view: [PAGE]", "view: [SOME_SQL]"))) {
            runner(invalid).run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void Java与Yaml重复绑定不按装配顺序覆盖() {
        runner(YAML).withBean(EntityPolicyBinding.class, () -> new EntityPolicyBinding(
            Order.class, "order", "项目Java", Map.of("owner", "ownerId"))).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("项目Java")
                    .hasStackTraceContaining("ent.loom.crud.access.entities.order");
            });
    }

    @Test
    void 重复Yaml键在绑定前拒绝() {
        assertThatThrownBy(() -> new YamlPropertySourceLoader().load("重复键",
            new ByteArrayResource("policies: {}\npolicies: {}\n".getBytes(StandardCharsets.UTF_8))))
            .hasMessageContaining("duplicate");
    }

    private AuthorizationRequest request() {
        return new AuthorizationRequest(VisitorContext.authenticated(new AccessSubject("user", AccessSubjectType.HUMAN),
            new OwnerIdentity("42"), "merchant", null, null), view, null);
    }
    public record OwnerIdentity(String ownerId) implements BusinessIdentity {
        public String getIdentityCode() { return "owner"; }
        public String getIdentityId() { return ownerId; }
        public List<String> memberships() { return List.of(ownerId); }
    }
    public record ReviewerIdentity(String ownerId) implements BusinessIdentity {
        public String getIdentityCode() { return "reviewer"; }
        public String getIdentityId() { return ownerId; }
    }
    private static final class Order { }
    private static final class Note { }

    private static EntityMeta editableMeta(Class<?> type, String resource, String writable) {
        Map<String, EntityFieldMeta> fields = Map.of(
            "id", new EntityFieldMeta("id", String.class, "id", false, false, true, true),
            "ownerId", new EntityFieldMeta("ownerId", String.class, "owner_id", false, false, true, true),
            writable, new EntityFieldMeta(writable, String.class, writable, false, false, true, true));
        return new EntityMeta(type, new ResourceDescriptor(type, resource, null, List.of()), resource, "id", null, fields);
    }
}
