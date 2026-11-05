package com.entloom.crud.starter.config;

import com.entloom.crud.api.access.*;
import com.entloom.crud.api.enums.*;
import com.entloom.crud.core.governance.authorization.*;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistration;
import com.entloom.crud.core.governance.policy.*;
import com.entloom.crud.core.runtime.meta.*;
import com.entloom.declaration.EntityDeclarations;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ByteArrayResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 以最终授权行为验证配置继承；实体声明与全局配置均使用真实 YAML 加载。 */
class ConfiguredAccessPoliciesTest {
    private static final String GLOBAL = """
        ent.loom.crud.access:
          identities:
            merchant: {id-type: LONG}
            customer: {id-type: LONG}
            super-admin: {id-field: adminId, id-type: LONG}
          relations:
            merchant: {field: merchantId}
            customer: {field: customerId}
          default-policy: record
          defaults:
            access:
              super-admin:
                actions: ["*"]
                scope: all
              merchant:
                scope: merchant
              customer:
                scope: customer
          policies:
            maintainable:
              actions: [view, create, update, delete]
              access:
                merchant:
                  actions: [view, create, update, delete]
            workflow:
              actions: [view]
              access:
                merchant:
                  actions: [view]
                customer:
                  actions: [view]
            record:
              actions: [view]
              access:
                merchant:
                  actions: [view]
        """;

    private EntityMetaRegistry metas() {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<>();
        fields.put("id", new EntityFieldMeta("id", Long.class, "id", false, false, true, true));
        fields.put("merchantId", new EntityFieldMeta("merchantId", Long.class, "merchant_id", false, false, true, true));
        fields.put("customerId", new EntityFieldMeta("customerId", Long.class, "customer_id", false, false, true, true));
        fields.put("title", new EntityFieldMeta("title", String.class, "title", false, false, true, true));
        fields.put("active", new EntityFieldMeta("active", Boolean.class, "active", false, false, true, true));
        EntityMeta meta = new EntityMeta(Order.class, new ResourceDescriptor(Order.class, "order", null, List.of()),
            "orders", "id", null, fields);
        EntityMetaRegistry registry = mock(EntityMetaRegistry.class);
        when(registry.getEntityMetas()).thenReturn(List.of(meta));
        when(registry.getEntityMeta(Order.class)).thenReturn(meta);
        return registry;
    }

    private ApplicationContextRunner runner(String entity) { return runner(GLOBAL, entity); }
    private ApplicationContextRunner runner(String global, String entity) {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CrudPolicyAutoConfiguration.class))
            .withBean("merchantIdentity", BusinessIdentityRegistration.class, () -> registration("merchant", "merchantId"))
            .withBean("customerIdentity", BusinessIdentityRegistration.class, () -> registration("customer", "customerId"))
            .withBean("adminIdentity", BusinessIdentityRegistration.class, () -> registration("super-admin", "adminId"))
            .withBean(EntityMetaRegistry.class, this::metas)
            .withBean(EntityDeclarations.class, () -> entity == null ? new EntityDeclarations() : new EntityDeclarations(
                new ByteArrayResource(entity.getBytes(StandardCharsets.UTF_8), "order.yml") {
                    @Override public String getFilename() { return "order.yml"; }
                }))
            .withInitializer(context -> {
                try {
                    new YamlPropertySourceLoader().load("全局访问配置", new ByteArrayResource(global.getBytes(StandardCharsets.UTF_8)))
                        .forEach(source -> context.getEnvironment().getPropertySources().addFirst(source));
                } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
            });
    }

    private AuthorizationResult authorize(org.springframework.context.ApplicationContext context, String identity, String action) {
        VisitorContext visitor;
        if (identity == null) visitor = VisitorContext.anonymous("commerce", null, null);
        else {
            var subject = new AccessSubject("actor", AccessSubjectType.HUMAN);
            visitor = VisitorContext.authenticated(subject,
                context.getBean(BusinessIdentityRegistry.class).resolve(subject, identity, "42"), "commerce", null, null);
        }
        return context.getBean(BusinessAuthorizationService.class).authorize(new AuthorizationRequest(visitor,
            new BusinessOperation("order", action), null));
    }

    @Test
    void 默认记录策略及显式全量管理员复用现有授权链() {
        runner("crud: {}").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(authorize(context, "merchant", "view").getScope().getField()).isEqualTo("merchantId");
            assertThat(authorize(context, "merchant", "view").getScope().getValues()).containsExactly("42");
            assertThat(authorize(context, "super-admin", "view").getScope().getKind()).isEqualTo(AuthorizationScope.Kind.ALL);
            assertThatThrownBy(() -> authorize(context, "customer", "view")).hasMessageContaining("未声明");
            var effective = context.getBean(ConfiguredAccessPolicies.class).snapshot().get("order");
            assertThat(effective.actions()).containsExactly("view");
            assertThat(effective.sources()).containsEntry("policy", "ent.loom.crud.access.default-policy [实体=order]");
            assertThatThrownBy(() -> effective.grants().clear()).isInstanceOf(UnsupportedOperationException.class);
        });
    }

    @Test
    void 默认策略不自动发布无声明或明确关闭的内部实体() {
        for (String file : Arrays.asList(null, "crud: {http-exposed: false}")) runner(file).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CrudEntityDeclarations.class).httpExposed("order")).isFalse();
            assertThat(context.getBean(ConfiguredAccessPolicies.class).snapshot()).isEmpty();
        });
    }

    @Test
    void 实体动作整体替换继承授权裁剪且通配符按最终动作展开() {
        runner("""
            crud:
              access:
                policy: maintainable
                actions: [view, update]
                merchant:
                  actions: {add: [view], remove: [update]}
                super-admin:
                  actions: {remove: [update]}
            """).run(context -> {
            assertThat(context).hasNotFailed();
            var result = context.getBean(ConfiguredAccessPolicies.class).snapshot().get("order");
            assertThat(result.grants().get("merchant")).containsExactly("view");
            assertThat(result.grants().get("super-admin")).containsExactly("view");
        });
    }

    @Test
    void 实体动作增量继承且移除优先并在授权前展开最终动作() {
        runner("""
            crud:
              access:
                policy: maintainable
                actions:
                  add: [view, batch-update, delete]
                  remove: [delete]
                merchant:
                  actions: {add: [batch-update]}
            """).run(context -> {
                assertThat(context).hasNotFailed();
                var effective = context.getBean(ConfiguredAccessPolicies.class).snapshot().get("order");
                assertThat(effective.actions()).containsExactly("view", "create", "update", "batch-update");
                assertThat(effective.grants().get("merchant")).containsExactlyInAnyOrderElementsOf(effective.actions());
                assertThat(effective.grants().get("super-admin")).containsExactlyInAnyOrderElementsOf(effective.actions());
                assertThat(effective.sources().get("actions")).contains("order.yml", "crud.access.actions");
                assertThat(effective.sources()).containsEntry("actions.inherited", "ent.loom.crud.access.policies.maintainable.actions");
                assertThat(authorize(context, "merchant", "batch-update").getDecision()).isEqualTo(AccessDecision.ALLOW);
                assertThatThrownBy(() -> authorize(context, "merchant", "delete")).isInstanceOf(RuntimeException.class);
            });
    }

    @Test
    void 增加动作不自动授予身份且已知未开放动作可幂等移除() {
        runner("crud: {access: {actions: {add: [batch-update], remove: [delete]}}}").run(context -> {
            assertThat(context).hasNotFailed();
            var effective = context.getBean(ConfiguredAccessPolicies.class).snapshot().get("order");
            assertThat(effective.actions()).containsExactly("view", "batch-update");
            assertThat(effective.grants().get("merchant")).containsExactly("view");
            assertThat(effective.grants().get("super-admin")).containsExactly("view", "batch-update");
            assertThatThrownBy(() -> authorize(context, "merchant", "batch-update")).hasMessageContaining("未声明");
        });
    }

    @Test
    void 增量跟随策略演进而显式列表保持固定() {
        String evolved = GLOBAL.replace("actions: [view]", "actions: [view, update]");
        runner(evolved, "crud: {access: {actions: {add: [batch-update]}}}").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ConfiguredAccessPolicies.class).snapshot().get("order").actions())
                .containsExactly("view", "update", "batch-update");
        });
        runner(evolved, "crud: {access: {actions: [view, batch-update]}}").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ConfiguredAccessPolicies.class).snapshot().get("order").actions())
                .containsExactly("view", "batch-update");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{add: [], remove: []}"})
    void 空增量对象保持继承(String actions) {
        runner("crud: {access: {actions: " + actions + "}}").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ConfiguredAccessPolicies.class).snapshot().get("order").actions()).containsExactly("view");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"{add: [shpi]}", "{remove: [shpi]}", "{add: ['*']}", "{remove: ['*']}", "['*']",
        "{add: [view, view]}", "{remove: [view, view]}", "{add: null}", "{remove: null}", "null",
        "{add: view}", "{replace: [view]}", "{add: {remove: [view]}}", "{add: [export]}"})
    void 动作增量拒绝未知通配符非法结构和未接入能力(String actions) {
        runner("crud: {access: {actions: " + actions + "}}").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("order.yml").hasStackTraceContaining("actions");
        });
    }

    @Test
    void 移除动作后显式身份重授权仍启动失败() {
        runner("crud: {access: {policy: maintainable, actions: {remove: [delete]}, merchant: {actions: {add: [delete]}}}}")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("超出实体最终 actions");
            });
    }

    @Test
    void 全局策略继续只接受完整动作列表() {
        runner(GLOBAL.replace("actions: [view]", "actions: {add: [view]}"), "crud: {}")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 列表替换空列表撤权与后层重新授权() {
        runner(GLOBAL.replace("          actions: [view, create, update, delete]", "          actions: {add: [view], remove: [update]}") , """
            crud:
              access:
                policy: maintainable
                merchant:
                  actions: {add: [update], remove: [delete]}
                super-admin:
                  actions: []
                customer:
                  actions: [view]
            """).run(context -> {
            assertThat(context).hasNotFailed();
            var grants = context.getBean(ConfiguredAccessPolicies.class).snapshot().get("order").grants();
            assertThat(grants.get("merchant")).containsExactly("view", "update");
            assertThat(grants.get("super-admin")).isEmpty();
            assertThat(authorize(context, "customer", "view").getScope().getField()).isEqualTo("customerId");
            assertThatThrownBy(() -> authorize(context, "super-admin", "view")).hasMessageContaining("未声明");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "access: {policy: unknown}",
        "access: {actions: [shpi]}",
        "access: {stranger: {actions: [view]}}",
        "access: {merchant: {actions: [update]}}",
        "access: {merchant: {actions: {add: [update]}}}",
        "access: {merchant: {actions: {remove: [shpi]}}}",
        "access: {merchant: {scope: missing}}",
        "access: {merchant: {scope: broken}}",
        "access: {policy: maintainable, actions: [view], merchant: {actions: [view, delete]}}",
        "access: {actions: [stats]}",
        "access: {actions: [export]}",
        "access: {policy: maintainable}\n  operations: {update: [UPDATE_BATCH]}"
    })
    void 错误声明启动失败且保留实体来源(String declaration) {
        runner("crud:\n  " + declaration).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("order");
        });
    }

    @Test
    void 范围缺失或关系字段缺失拒绝全量退化() {
        runner(GLOBAL.replace("scope: merchant", "scope: missing"), "crud: {}").run(context -> assertThat(context).hasFailed());
        runner(GLOBAL.replace("field: merchantId", "field: absent"), "crud: {}").run(context -> assertThat(context).hasFailed());
        runner(GLOBAL.replace("        scope: merchant\n", ""), "crud: {}").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 公共查询必须有实体显式范围及返回白名单() {
        for (String extra : List.of("", "\n      scope: all")) runner("crud:\n  access:\n    public:\n      actions: [view]" + extra)
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void YAML规则与字段限制保持一致且动态规则只能收窄() {
        runner("""
            crud:
              access:
                public:
                  actions: [view]
                  rules: {active: true}
                  fields:
                    RETURN: {mode: INCLUDE, fields: [id, title]}
                    EXPAND: {mode: NONE}
            """)
            .run(context -> {
                assertThat(context).hasNotFailed();
                var result = authorize(context, null, "view");
                assertThat(result.getScope().getField()).isEqualTo("active");
                assertThat(result.getFields().allows(AuthorizationFields.Use.RETURN, "title")).isTrue();
                assertThat(result.getFields().allows(AuthorizationFields.Use.RETURN, "customerId")).isFalse();
                assertThat(result.getFields().allows(AuthorizationFields.Use.FILTER, "customerId")).isFalse();
                assertThatThrownBy(() -> authorize(context, "customer", "view")).hasMessageContaining("未声明");
            });
    }

    @Test
    void 公共Return白名单不会隐式授予关联展开而登录身份缺省保留() {
        runner("""
            crud:
              access:
                public:
                  actions: [view]
                  scope: all
                  fields:
                    RETURN: {mode: INCLUDE, fields: [id, title]}
            """).withAllowBeanDefinitionOverriding(true).withBean(EntityMetaRegistry.class, () -> {
                var registry = metas();
                var original = registry.getEntityMeta(Order.class);
                var fields = new LinkedHashMap<>(original.getFieldMetas());
                fields.put("customer", new EntityFieldMeta("customer", Object.class, "customer", false, true, false, false));
                var meta = new EntityMeta(Order.class, original.getResourceDescriptor(), "orders", "id", null, fields);
                when(registry.getEntityMeta(Order.class)).thenReturn(meta);
                when(registry.getEntityMetas()).thenReturn(List.of(meta));
                return registry;
            }).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(authorize(context, null, "view").getFields().allows(AuthorizationFields.Use.EXPAND, "customer")).isFalse();
                assertThat(authorize(context, "merchant", "view").getFields().allows(AuthorizationFields.Use.EXPAND, "customer")).isTrue();
            });
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALL", "EXCLUDE", "INCLUDE"})
    void 公共展开不能开放整个关联(String mode) {
        runner("crud:\n  access:\n    public:\n      actions: [view]\n      scope: all\n      fields:\n"
            + "        RETURN: {mode: INCLUDE, fields: [id]}\n        EXPAND: {mode: " + mode
            + ("INCLUDE".equals(mode) ? ", fields: [customer]" : "") + "}")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 动态规则只能收窄YAML范围与字段且公共分支不携带身份() {
        runner("""
            crud:
              access:
                public:
                  actions: [view]
                  rules: {active: true}
                  fields:
                    RETURN: {mode: INCLUDE, fields: [id, title]}
            """)
            .withBean(BusinessPermissionEvaluator.class, () -> (visitor, operation, rule, target) -> {
                assertThat(visitor.getIdentity()).isEmpty();
                assertThat(visitor.getSubject()).isEmpty();
                return BusinessPermissionDecision.allow(AuthorizationScope.equal("id", 42L),
                    AuthorizationFields.of(Map.of(AuthorizationFields.Use.RETURN, Set.of("id", "merchantId"))));
            }).run(context -> {
                assertThat(context).hasNotFailed();
                var result = authorize(context, null, "view");
                assertThat(result.getScope().getKind()).isEqualTo(AuthorizationScope.Kind.AND);
                assertThat(result.getFields().allows(AuthorizationFields.Use.RETURN, "id")).isTrue();
                assertThat(result.getFields().allows(AuthorizationFields.Use.RETURN, "title")).isFalse();
                assertThat(result.getFields().allows(AuthorizationFields.Use.RETURN, "merchantId")).isFalse();
            });
    }

    @Test
    void 客户身份可按可信所属商家属性过滤而不提升为商家() {
        String global = GLOBAL.replace("    customer: {id-type: LONG}\n", "")
            .replace("    customer: {field: customerId}",
                "    customer: {field: customerId}\n    customer-merchant: {field: merchantId, identity-field: merchantId}");
        runner(global, "crud: {access: {customer: {actions: [view], scope: customer-merchant}}}")
            .withAllowBeanDefinitionOverriding(true)
            .withBean("customerIdentity", BusinessIdentityRegistration.class, () -> new BusinessIdentityRegistration<>(
                "customer", LinkedCustomerIdentity.class, (subject, id) -> new LinkedCustomerIdentity(id, 1001L)))
            .run(context -> {
                assertThat(context).hasNotFailed();
                var result = authorize(context, "customer", "view");
                assertThat(result.getScope().getField()).isEqualTo("merchantId");
                assertThat(result.getScope().getValues()).containsExactly("1001");
                assertThat(context.getBean(ConfiguredAccessPolicies.class).snapshot().get("order").grants().get("customer"))
                    .containsExactly("view");
            });
    }

    public record LinkedCustomerIdentity(String customerId, Long merchantId) implements BusinessIdentity {
        @Override public String getIdentityCode() { return "customer"; }
        @Override public String getIdentityId() { return customerId; }
    }

    @Test
    void 单条与批量更新独立授权映射不可混用() {
        runner("""
            crud:
              access:
                policy: maintainable
                actions: [view, update, batch-update]
                merchant:
                  actions: [view, update]
                customer:
                  actions: [batch-update]
            """).run(context -> {
            assertThat(context).hasNotFailed();
            var mappings = context.getBean(CrudOperationMappingRegistry.class);
            assertThat(mappings.require("order", CrudOperationKey.of(CommandOperation.UPDATE_BATCH), null).getOperationCode()).isEqualTo("batch-update");
            assertThatThrownBy(() -> authorize(context, "merchant", "batch-update")).hasMessageContaining("未声明");
            assertThatThrownBy(() -> authorize(context, "customer", "update")).hasMessageContaining("未声明");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"INCLUDE", "EXCLUDE", "ALL", "NONE"})
    void 字段模式复用静态字段能力上限(String mode) {
        runner("crud:\n  access:\n    merchant:\n      fields:\n        RETURN:\n          mode: " + mode
            + (Set.of("INCLUDE", "EXCLUDE").contains(mode) ? "\n          fields: [title]" : "")).run(context -> {
            assertThat(context).hasNotFailed();
            var fields = authorize(context, "merchant", "view").getFields();
            assertThat(fields.allows(AuthorizationFields.Use.RETURN, "title")).isEqualTo(Set.of("INCLUDE", "ALL").contains(mode));
            assertThat(fields.allows(AuthorizationFields.Use.RETURN, "id")).isEqualTo(Set.of("EXCLUDE", "ALL").contains(mode));
        });
    }

    @Test
    void 未使用范围属性不产生授权且空动作实体保持默认拒绝() {
        runner("crud: {access: {actions: []}}").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(BusinessAuthorizationService.class).governs("order")).isTrue();
            assertThat(context.getBean(ConfiguredAccessPolicies.class).snapshot().get("order").actions()).isEmpty();
        });
    }
    @Test
    void 全局字段按用途继承且实体只需覆盖特殊用途() {
        String global = GLOBAL.replace("scope: merchant", """
            scope: merchant
                    fields:
                      RETURN: {mode: INCLUDE, fields: [id, title]}
            """.stripTrailing());
        runner(global, "crud: {access: {merchant: {fields: {SORT: {mode: NONE}}}}}").run(context -> {
            assertThat(context).hasNotFailed();
            var fields = authorize(context, "merchant", "view").getFields();
            assertThat(fields.allows(AuthorizationFields.Use.RETURN, "title")).isTrue();
            assertThat(fields.allows(AuthorizationFields.Use.FILTER, "title")).isTrue();
            assertThat(fields.allows(AuthorizationFields.Use.RETURN, "customerId")).isFalse();
            assertThat(fields.allows(AuthorizationFields.Use.SORT, "title")).isFalse();
            assertThat(fields.allows(AuthorizationFields.Use.WRITE, "title")).isFalse();
            assertThat(fields.allows(AuthorizationFields.Use.STATS, "title")).isFalse();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "crud: {fields: {merchant: {RETURN: {mode: ALL}}}}",
        "crud: {access: {grants: {merchant: [view]}}}",
        "crud: {access: {scopes: {merchant: all}}}",
        "crud: {access: {merchant: {fields: [id, title]}}}",
        "crud: {access: {merchant: {scpoe: all}}}"
    })
    void 拒绝旧入口列表字段简写及未知主体属性(String entity) {
        runner(entity).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("order");
        });
    }

    @Test
    void 全局主体同样拒绝未知属性及列表字段简写() {
        for (String extra : List.of("scpoe: all", "fields: [id, title]")) {
            runner(GLOBAL.replace("scope: merchant", "scope: merchant\n        " + extra), "crud: {}")
                .run(context -> assertThat(context).hasFailed());
        }
    }

    private static BusinessIdentityRegistration<?> registration(String code, String field) {
        var definition = new BusinessIdentityDefinition(code, field, BusinessIdentityIdType.LONG);
        return new BusinessIdentityRegistration<>(code, ConfiguredBusinessIdentity.class, (subject, id) -> definition.snapshot(id));
    }
    private static class Order { }
}
