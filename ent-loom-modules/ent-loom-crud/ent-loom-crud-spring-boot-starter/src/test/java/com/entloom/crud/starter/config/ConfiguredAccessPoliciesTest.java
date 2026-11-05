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
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.RelationGraph;
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
                    operations:
                      view: [PAGE]
                relations:
                  owner:
                    field: ownerId
        """;
    private final BusinessOperation view = new BusinessOperation("order", "view");

    private static final BusinessIdentityDefinition OWNER = new BusinessIdentityDefinition("owner", "memberId", BusinessIdentityIdType.STRING);
    private static final String CONFIGURED_YAML = YAML.replace("access:\n", "access:\n        identities:\n          owner:\n            id-field: memberId\n            id-type: STRING\n");

    private ApplicationContextRunner configuredRunner(String yaml) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CrudPolicyAutoConfiguration.class))
            .withBean(EntityMetaRegistry.class, () -> CrudPolicyAutoConfigurationTest.metas(Order.class))
            .withBean(com.entloom.declaration.EntityDeclarations.class, () -> DeclarationTestFiles.load("ConfiguredAccessPoliciesTest", yaml, CrudPolicyAutoConfigurationTest.metas(Order.class)))
            .withBean(BusinessIdentityRegistration.class, () -> new BusinessIdentityRegistration<>(
                "owner", ConfiguredBusinessIdentity.class, (subject, id) -> OWNER.snapshot(id)))
            .withInitializer(context -> {
                try {
                    new YamlPropertySourceLoader().load("配置身份策略", new ByteArrayResource(DeclarationTestFiles.properties(yaml).getBytes(StandardCharsets.UTF_8)))
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
            .withBean(com.entloom.declaration.EntityDeclarations.class, () -> DeclarationTestFiles.load("ConfiguredAccessPoliciesTest", yaml, metas))
            .withBean(BusinessIdentityRegistration.class, () -> new BusinessIdentityRegistration<>(
                "owner", OwnerIdentity.class, (subject, id) -> new OwnerIdentity(id)))
            .withInitializer(context -> {
                try {
                    var resource = new ByteArrayResource(DeclarationTestFiles.properties(yaml).getBytes(StandardCharsets.UTF_8));
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
            .replace("        entities:\n          order:\n            operations:\n              view: [PAGE]\n", "");
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
    void 显式操作按动作覆盖且业务动作不猜测映射() {
        runner(YAML).run(context -> {
            assertThat(context).hasNotFailed();
            var mappings = context.getBean(CrudOperationMappingRegistry.class);
            assertThat(mappings.snapshot()).hasSize(1);
            assertThatThrownBy(() -> mappings.require("order", CrudOperationKey.of(QueryOperation.LIST), null))
                .hasMessageContaining("未映射");
        });
        runner(YAML.replace("actions: [view]", "actions: [ship]")
            .replace("        entities:\n          order:\n            operations:\n              view: [PAGE]\n", "")).run(context -> {
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
    void 仅覆盖更新动作保留查询缺省并显式开放批量() {
        runner(YAML.replace("actions: [view]", "actions: [view, update]")
            .replace("view: [PAGE]", "update: [UPDATE, UPDATE_BATCH]")).run(context -> {
                assertThat(context).hasNotFailed();
                var mappings = context.getBean(CrudOperationMappingRegistry.class);
                assertThat(mappings.snapshot()).hasSize(6);
                assertThat(mappings.require("order", CrudOperationKey.of(QueryOperation.LIST), null)).isEqualTo(view);
                assertThat(mappings.require("order", CrudOperationKey.of(CommandOperation.UPDATE_BATCH), null))
                    .isEqualTo(new BusinessOperation("order", "update"));
            });
    }

    @Test
    void 空动作映射只关闭该动作的Crud入口并保留业务授权() {
        runner(YAML.replace("actions: [view]", "actions: [view, update]")
            .replace("view: [PAGE]", "update: []")).run(context -> {
                assertThat(context).hasNotFailed();
                var mappings = context.getBean(CrudOperationMappingRegistry.class);
                assertThat(mappings.snapshot()).hasSize(4);
                assertThatThrownBy(() -> mappings.require("order", CrudOperationKey.of(CommandOperation.UPDATE), null))
                    .hasMessageContaining("未映射");
                assertThat(context.getBean(BusinessAuthorizationService.class).authorize(new AuthorizationRequest(
                    request().getVisitor(), new BusinessOperation("order", "update"), null)).getDecision())
                    .isEqualTo(AccessDecision.ALLOW);
            });
    }

    private ApplicationContextRunner entityFileRunner(String content) {
        String global = YAML.replace("actions: [view]", "actions: [view, update]")
            .replace("        entities:\n          order:\n            operations:\n              view: [PAGE]\n", "");
        if (!content.contains("policy:")) global = global.replace(
            "        policies:\n          order:\n            owner:\n              actions: [view, update]\n", "");
        return runner(global, CrudPolicyAutoConfigurationTest.metas(FileOrder.class)).withAllowBeanDefinitionOverriding(true)
            .withBean(com.entloom.declaration.EntityDeclarations.class, () -> new com.entloom.declaration.EntityDeclarations(
                new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8), "实体试点/order.yml") {
                    @Override public String getFilename() { return "order.yml"; }
                }));
    }

    private static final class FileOrder { }

    @Test
    void 实体文件绑定策略与字段权限并保留逐动作缺省() {
        entityFileRunner("""
            crud:
              policy: order
              operations:
                update: [UPDATE, UPDATE_BATCH]
              fields:
                owner:
                  RETURN:
                    mode: EXCLUDE
                    fields: [ownerId]
                  WRITE:
                    mode: ALL
            """).run(context -> {
                assertThat(context).hasNotFailed();
                var mappings = context.getBean(CrudOperationMappingRegistry.class);
                assertThat(mappings.snapshot()).hasSize(6);
                var service = context.getBean(BusinessAuthorizationService.class);
                var result = service.authorize(new AuthorizationRequest(request().getVisitor(), view, null));
                assertThat(result.getDecision()).isEqualTo(AccessDecision.ALLOW);
                assertThat(result.getFields().allows(AuthorizationFields.Use.RETURN, "ownerId")).isFalse();
                assertThat(result.getFields().allows(AuthorizationFields.Use.RETURN, "id")).isTrue();
            });
    }

    @Test
    void 实体文件错误包含文件实体与配置路径() {
        for (String invalid : List.of("operations: null", "operations: {update: null}", "operations: {update: [UNKNOWN]}", "unknown: true",
            "fields: {owner: {WRITE: {mode: UNKNOWN}}}")) {
            entityFileRunner("crud:\n  policy: order\n  " + invalid + "\n").run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("order.yml")
                    .hasStackTraceContaining("实体=order").hasStackTraceContaining("路径=crud");
            });
        }
    }

    @Test
    void Http实体漏打包声明或遗漏访问选择拒绝启动() {
        entityFileRunner("crud: {policy: order}")
            .withPropertyValues("ent.loom.crud.controller.enabled=true").run(context -> assertThat(context).hasNotFailed());
        entityFileRunner("crud: {access: JAVA}")
            .withPropertyValues("ent.loom.crud.controller.enabled=true").run(context -> assertThat(context).hasNotFailed());
        entityFileRunner("crud: {contracts: {}}")
            .withPropertyValues("ent.loom.crud.controller.enabled=true").run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasMessageContaining("必须显式选择");
            });
        runner("").withAllowBeanDefinitionOverriding(true).withBean(com.entloom.declaration.EntityDeclarations.class, com.entloom.declaration.EntityDeclarations::new).withPropertyValues("ent.loom.crud.controller.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("HTTP 实体缺少声明文件");
        });
    }

    @Test
    void 同一策略模板复用时各实体字段注解独立() {
        String yaml = """
            ent.loom.crud.access:
              policies:
                order:
                  owner:
                    actions: [view, update]
              entities:
                order:
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
            """;
        EntityMeta order = editableMeta(EditableOrder.class, "order", "amount");
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
    void 分类接口无需Yaml绑定即可编译动作范围及字段授权() {
        runner(classifiedYaml(), classifiedMetas(ClassifiedOrder.class)).run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(CrudAccessProperties.class);
            var mappings = context.getBean(CrudOperationMappingRegistry.class);
            assertThat(mappings.snapshot()).hasSize(5);
            assertThat(mappings.require("order", CrudOperationKey.of(QueryOperation.PAGE), null)).isEqualTo(view);
            var update = new BusinessOperation("order", "update");
            assertThat(mappings.require("order", CrudOperationKey.of(CommandOperation.UPDATE), null)).isEqualTo(update);
            var rule = context.getBean(EntityPolicyRegistry.class).requireDeclaration(view, PolicyIdentityBranch.identity("owner"));
            assertThat(rule.getBindingSource()).isEqualTo("Byte array resource [ConfiguredAccessPoliciesTest/order.yml] [实体=order, 路径=crud.policy]");
            var service = context.getBean(BusinessAuthorizationService.class);
            var result = service.authorize(new AuthorizationRequest(request().getVisitor(), update, null));
            assertThat(result.getDecision()).isEqualTo(AccessDecision.ALLOW);
            assertThat(result.getScope().getField()).isEqualTo("ownerId");
            assertThat(result.getScope().getValues()).containsExactly("42");
            assertThat(result.getFields().snapshot().get(AuthorizationFields.Use.WRITE)).containsExactly("amount");
            assertThatThrownBy(() -> service.authorize(new AuthorizationRequest(request().getVisitor(),
                new BusinessOperation("order", "delete"), null))).hasMessageContaining("未注册");
            properties.getPolicies().clear();
            assertThat(service.authorize(new AuthorizationRequest(request().getVisitor(), update, null))
                .getFields().snapshot().get(AuthorizationFields.Use.WRITE)).containsExactly("amount");
        });
    }

    @Test
    void 分类接口可复用父接口及实体操作差异但不缺省授予写入字段() {
        String yaml = classifiedYaml()
            + "  entities:\n    order:\n      operations:\n        view: [PAGE]\n        update: []\n";
        runner(yaml, classifiedMetas(InheritedOrder.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CrudOperationMappingRegistry.class).snapshot()).hasSize(1);
            var result = context.getBean(BusinessAuthorizationService.class).authorize(request());
            assertThat(result.getScope().getValues()).containsExactly("42");
            assertThat(result.getFields().allows(AuthorizationFields.Use.WRITE, "amount")).isFalse();
        });
    }

    @Test
    void 实体直接注解与分类接口共用配置策略编译并接受显式关系() {
        runner(classifiedYaml().replace("field: ownerId", "field: missingId"), classifiedMetas(AnnotatedOrder.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                var result = context.getBean(BusinessAuthorizationService.class).authorize(request());
                assertThat(result.getScope().getField()).isEqualTo("ownerId");
                assertThat(result.getScope().getValues()).containsExactly("42");
            });
    }

    @Test
    void 分类接口与Java重复绑定启动失败() {
        runner(classifiedYaml(), classifiedMetas(ClassifiedOrder.class))
            .withBean(EntityPolicyBinding.class, () -> new EntityPolicyBinding(
                ClassifiedOrder.class, "owned-data", "项目Java", Map.of("owner", "ownerId")))
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("实体策略重复绑定")
                    .hasStackTraceContaining("项目Java").hasStackTraceContaining("ConfiguredAccessPoliciesTest/order.yml");
            });
    }

    @Test
    void 未知分类策略和多个分类及关系冲突均启动失败() {
        runner("", classifiedMetas(UnknownPolicyOrder.class)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("模板未注册: unknown");
        });
        runner(classifiedYaml(), classifiedMetas(ConflictingOrder.class)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("duplicate key");
        });
        runner(classifiedYaml() + "  entities:\n    order:\n      relations:\n        owner: title\n",
            classifiedMetas(AnnotatedOrder.class)).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("实体文件重复声明");
            });
    }

    private static String classifiedYaml() {
        return """
            ent.loom.crud.access:
              policies:
                owned-data:
                  owner:
                    actions: [view, update]
              relations:
                owner:
                  field: ownerId
            """;
    }

    private static EntityMetaRegistry classifiedMetas(Class<?> type) {
        EntityMetaRegistry metas = mock(EntityMetaRegistry.class);
        when(metas.getEntityMetas()).thenReturn(List.of(protectedMeta(type)));
        return metas;
    }

    private interface OwnedData {
        String getOwnerId();
    }

    private interface SpecializedData extends OwnedData { }

    private static class ClassifiedOrder implements OwnedData {
        public String getOwnerId() { return null; }
    }

    private static final class InheritedOrder extends ClassifiedOrder implements SpecializedData { }

    private static final class AnnotatedOrder { }

    private static final class UnknownPolicyOrder { }

    private interface OtherData { }

    private static final class ConflictingOrder implements OwnedData, OtherData {
        public String getOwnerId() { return null; }
    }

    @Test
    void Yaml动作与实体字段注解共同编译可信身份权限() {
        runner(YAML).run(context -> {
            assertThat(context).hasNotFailed();
            var mapping = context.getBean(CrudOperationMappingRegistry.class);
            assertThat(mapping.require("order", CrudOperationKey.of(QueryOperation.PAGE), null)).isEqualTo(view);
            var rule = context.getBean(EntityPolicyRegistry.class).requireDeclaration(view, PolicyIdentityBranch.identity("owner"));
            assertThat(rule.getBindingSource()).contains("ConfiguredAccessPoliciesTest/order.yml");
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
            context.getBean(CrudAccessProperties.class).getPolicies().clear();
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
    void 动态权限拒绝或收窄不能被声明式授权重新放开() {
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
            YAML.replace("order:\n            operations", "unknown:\n            operations"),
            YAML.replace("operations:\n", "policy: unknown\n            operations:\n"),
            YAML.replace("actions: [view]", "actions: [view, view]"),
            YAML.replace("actions: [view]", "actions: [ship]"),
            YAML.replace("owner:\n              actions", "unknown:\n              actions"),
            YAML.replace("field: ownerId", "field: unknown"),
            YAML.replace("field: ownerId", "identity-field: unknown"),
            YAML.replace("actions: [view]", "actions: [view]\n              scope: unknown"),
            YAML.replace("actions: [view]", "actions: [view]\n              fields: unknown"),
            YAML.replace("view: [PAGE]", "view: [SOME_SQL]"))) {
            runner(invalid).run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void 注解与Java重复绑定不按装配顺序覆盖() {
        runner(YAML).withBean(EntityPolicyBinding.class, () -> new EntityPolicyBinding(
            Order.class, "order", "项目Java", Map.of("owner", "ownerId"))).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("项目Java")
                    .hasStackTraceContaining("ConfiguredAccessPoliciesTest/order.yml");
            });
    }

    @Test
    void 已移除的配置入口直接拒绝() {
        for (String property : List.of("ent.loom.crud.contracts.create.input-required-fields.order[0]=title",
                "ent.loom.crud.access.entity-options.order.ownership[0]=owner",
                "ent.loom.crud.access.field-plans.order.owner.WRITE.mode=ALL",
                "ent.loom.crud.access.policy-bindings.order=order")) {
            runner(YAML).withPropertyValues(property).run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void 同名策略不会自动绑定未注解实体且操作差异不能生成绑定() {
        EntityMetaRegistry metas = mock(EntityMetaRegistry.class);
        when(metas.getEntityMetas()).thenReturn(List.of(editableMeta(UnboundOrder.class, "order", "amount")));
        runner(YAML, metas).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("未选择策略");
        });
        when(metas.getEntityMetas()).thenReturn(List.of(
            editableMeta(UnboundOrder.class, "order", "amount"), editableMeta(Note.class, "note", "title")));
        String yaml = YAML.substring(0, YAML.indexOf("        entities:"))
            + "        relations:\n          owner:\n            field: ownerId\n";
        runner(yaml, metas).run(context -> {
            assertThat(context).hasNotFailed();
            var service = context.getBean(BusinessAuthorizationService.class);
            assertThat(service.governs("order")).isFalse();
            assertThat(service.governs("note")).isTrue();
            assertThatThrownBy(() -> service.authorize(request())).hasMessageContaining("未注册");
        });
    }

    @Test
    void 重复Yaml键在绑定前拒绝() {
        assertThatThrownBy(() -> new YamlPropertySourceLoader().load("重复键",
            new ByteArrayResource("policies: {}\npolicies: {}\n".getBytes(StandardCharsets.UTF_8))))
            .hasMessageContaining("duplicate");
    }

    @Test
    void 注解四种模式及空排除均受写入静态上限约束() {
        Map<Class<?>, List<String>> cases = Map.of(
            IncludeWrite.class, List.of("amount"), ExcludeWrite.class, List.of("title"),
            AllWrite.class, List.of("amount", "title"), NoneWrite.class, List.of(),
            EmptyExclude.class, List.of("amount", "title"));
        cases.forEach((type, expected) -> fieldRunner(type).run(context -> {
            assertThat(context).hasNotFailed();
            var fields = context.getBean(BusinessAuthorizationService.class).authorize(request()).getFields();
            assertThat(fields.snapshot().get(AuthorizationFields.Use.WRITE)).containsExactlyInAnyOrderElementsOf(expected);
            assertThat(fields.snapshot().get(AuthorizationFields.Use.RETURN)).doesNotContain("items");
            assertThatThrownBy(() -> fields.snapshot().get(AuthorizationFields.Use.WRITE).add("id"))
                .isInstanceOf(UnsupportedOperationException.class);
        }));
    }

    @Test
    void 各用途按注解编译且筛选排序缺省跟随返回字段() {
        fieldRunner(MixedFields.class).run(context -> {
            assertThat(context).hasNotFailed();
            var fields = context.getBean(BusinessAuthorizationService.class).authorize(request()).getFields();
            assertThat(fields.allows(AuthorizationFields.Use.RETURN, "ownerId")).isFalse();
            assertThat(fields.allows(AuthorizationFields.Use.FILTER, "ownerId")).isTrue();
            assertThat(fields.snapshot().get(AuthorizationFields.Use.SORT)).containsExactly("amount");
            assertThat(fields.snapshot().get(AuthorizationFields.Use.STATS)).isEmpty();
            assertThat(fields.snapshot().get(AuthorizationFields.Use.EXPAND)).containsExactly("items");
            assertThat(fields.allows(AuthorizationFields.Use.WRITE, "amount")).isFalse();
        });
        fieldRunner(ReturnExclude.class).run(context -> {
            assertThat(context).hasNotFailed();
            var fields = context.getBean(BusinessAuthorizationService.class).authorize(request()).getFields();
            for (var use : List.of(AuthorizationFields.Use.RETURN, AuthorizationFields.Use.FILTER, AuthorizationFields.Use.SORT)) {
                assertThat(fields.allows(use, "ownerId")).isFalse();
                assertThat(fields.allows(use, "amount")).isTrue();
            }
        });
        fieldRunner(NoFields.class).run(context -> {
            assertThat(context).hasNotFailed();
            var fields = context.getBean(BusinessAuthorizationService.class).authorize(request()).getFields();
            assertThat(fields.allows(AuthorizationFields.Use.RETURN, "ownerId")).isTrue();
            assertThat(fields.allows(AuthorizationFields.Use.WRITE, "amount")).isFalse();
        });
    }

    @Test
    void 不同身份字段隔离且显式NONE不重新推导筛选排序() {
        String yaml = YAML.replace("actions: [view]", "actions: [view]\n            reviewer:\n              actions: [view]\n              scope: owner");
        EntityMetaRegistry metas = mock(EntityMetaRegistry.class);
        when(metas.getEntityMetas()).thenReturn(List.of(protectedMeta(IdentityFields.class)));
        runner(yaml, metas).withBean("reviewerRegistration", BusinessIdentityRegistration.class,
            () -> new BusinessIdentityRegistration<>("reviewer", ReviewerIdentity.class,
                (subject, id) -> new ReviewerIdentity(id))).run(context -> {
                assertThat(context).hasNotFailed();
                var service = context.getBean(BusinessAuthorizationService.class);
                var owner = service.authorize(request()).getFields();
                var visitor = VisitorContext.authenticated(new AccessSubject("reviewer", AccessSubjectType.HUMAN),
                    new ReviewerIdentity("42"), "merchant", null, null);
                var reviewer = service.authorize(new AuthorizationRequest(visitor, view, null)).getFields();
                assertThat(owner.allows(AuthorizationFields.Use.RETURN, "ownerId")).isFalse();
                assertThat(reviewer.allows(AuthorizationFields.Use.RETURN, "ownerId")).isTrue();
                assertThat(owner.snapshot().get(AuthorizationFields.Use.FILTER)).isEmpty();
                assertThat(owner.snapshot().get(AuthorizationFields.Use.SORT)).isEmpty();
                assertThat(owner.snapshot().get(AuthorizationFields.Use.WRITE)).containsExactly("title");
                assertThat(reviewer.snapshot().get(AuthorizationFields.Use.WRITE)).containsExactly("amount");
            });
    }

    private static final class IdentityFields { }

    @Test
    void 重复用途未知身份字段及非法字段组合启动失败() {
        for (Class<?> type : List.of(DuplicateUse.class, UnknownIdentity.class, UnknownField.class,
            DuplicateField.class, BlankField.class, EmptyInclude.class, InvalidAll.class, InvalidNone.class)) {
            fieldRunner(type).run(context -> assertThat(context).hasFailed());
        }
        fieldRunner(UnboundFields.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("字段授权实体未选择策略");
        });
    }

    @Test
    void 关联排除子字段不会被整段关系授权绕过且整段排除生效() {
        for (Class<?> type : List.of(ExpandExclude.class, ExpandRelationExclude.class, ExpandUnknown.class)) {
            EntityMetaRegistry metas = mock(EntityMetaRegistry.class);
            EntityMeta root = protectedMeta(type);
            EntityMeta child = new EntityMeta(Item.class, new ResourceDescriptor(Item.class, "item", null, List.of()),
                "item", "id", null, Map.of(
                    "id", new EntityFieldMeta("id", String.class, "id", false, false, true, true),
                    "title", new EntityFieldMeta("title", String.class, "title", false, false, true, true)));
            RelationEdge edge = new RelationEdge();
            edge.setFromEntity(type);
            edge.setToEntity(Item.class);
            edge.setRelationField("items");
            when(metas.getEntityMetas()).thenReturn(List.of(root, child));
            when(metas.getEntityMeta(Item.class)).thenReturn(child);
            when(metas.getRelationGraph(type)).thenReturn(RelationGraph.of(List.of(edge)));
            runner(YAML, metas).run(context -> {
                if (type == ExpandUnknown.class) {
                    assertThat(context).hasFailed();
                    return;
                }
                assertThat(context).hasNotFailed();
                var fields = context.getBean(BusinessAuthorizationService.class).authorize(request()).getFields();
                assertThat(fields.snapshot().get(AuthorizationFields.Use.EXPAND))
                    .containsExactlyElementsOf(type == ExpandExclude.class ? List.of("items.id") : List.of());
                assertThat(fields.allows(AuthorizationFields.Use.RETURN, "items.id")).isFalse();
            });
        }
    }

    private ApplicationContextRunner fieldRunner(Class<?> type) {
        EntityMetaRegistry metas = mock(EntityMetaRegistry.class);
        when(metas.getEntityMetas()).thenReturn(List.of(protectedMeta(type)));
        return runner(type == UnboundFields.class ? YAML.replace("        entities:\n          order:\n            operations:\n              view: [PAGE]\n", "") : YAML, metas);
    }

    private static final class Item { }

    private static final class IncludeWrite { }

    private static final class ExcludeWrite { }

    private static final class AllWrite { }

    private static final class NoneWrite { }

    private static final class EmptyExclude { }

    private static final class MixedFields { }

    private static final class ReturnExclude { }

    private static final class NoFields { }

    private static final class DuplicateUse { }

    private static final class UnknownIdentity { }

    private static final class UnknownField { }

    private static final class DuplicateField { }

    private static final class BlankField { }

    private static final class EmptyInclude { }

    private static final class InvalidAll { }

    private static final class InvalidNone { }

    private static final class ExpandExclude { }

    private static final class ExpandRelationExclude { }

    private static final class ExpandUnknown { }

    private static final class UnboundFields { }

    private static EntityMeta protectedMeta(Class<?> type) {
        Map<String, EntityFieldMeta> fields = new java.util.LinkedHashMap<>();
        for (String name : List.of("id", "ownerId", "version", "deleted", "readonly", "immutable", "items", "amount", "title")) {
            fields.put(name, new EntityFieldMeta(name, String.class, name, false, name.equals("items"),
                true, true, !name.equals("readonly"), name.equals("ownerId"), name.equals("immutable")));
        }
        return new EntityMeta(type, new ResourceDescriptor(type, "order", null, List.of()),
            "order", "id", "deleted", fields);
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

    private static final class EditableOrder { }

    private static final class Note { }
    private static final class UnboundOrder { }

    private static EntityMeta editableMeta(Class<?> type, String resource, String writable) {
        Map<String, EntityFieldMeta> fields = Map.of(
            "id", new EntityFieldMeta("id", String.class, "id", false, false, true, true),
            "ownerId", new EntityFieldMeta("ownerId", String.class, "owner_id", false, false, true, true),
            writable, new EntityFieldMeta(writable, String.class, writable, false, false, true, true));
        return new EntityMeta(type, new ResourceDescriptor(type, resource, null, List.of()), resource, "id", null, fields);
    }
}
