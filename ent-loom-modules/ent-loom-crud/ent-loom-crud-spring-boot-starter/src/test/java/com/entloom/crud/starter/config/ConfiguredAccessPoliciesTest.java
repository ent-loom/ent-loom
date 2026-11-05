package com.entloom.crud.starter.config;

import com.entloom.crud.api.access.*;
import com.entloom.crud.api.enums.*;
import com.entloom.crud.core.governance.authorization.*;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistration;
import com.entloom.crud.core.governance.policy.*;
import com.entloom.crud.core.runtime.meta.*;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.annotations.EntCrudAccess;
import com.entloom.crud.annotations.EntCrudAccessField;
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

/** 以最终授权行为验证应用共享配置与实体注解的继承。 */
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

    private ApplicationContextRunner runner(Class<?> entity) { return runner(GLOBAL, entity); }
    private ApplicationContextRunner runner(String global, Class<?> entity) {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CrudPolicyAutoConfiguration.class))
            .withBean("merchantIdentity", BusinessIdentityRegistration.class, () -> registration("merchant", "merchantId"))
            .withBean("customerIdentity", BusinessIdentityRegistration.class, () -> registration("customer", "customerId"))
            .withBean("adminIdentity", BusinessIdentityRegistration.class, () -> registration("super-admin", "adminId"))
            .withBean(EntityMetaRegistry.class, this::metas)
            .withBean(CrudEntityDeclarations.class, () -> new CrudEntityDeclarations(entity == null ? List.of() : List.of(entity)))
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
        runner(DefaultOrder.class).run(context -> {
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
        for (Class<?> entity : Arrays.asList(null, HiddenOrder.class)) runner(entity).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CrudEntityDeclarations.class).httpExposed("order")).isFalse();
            assertThat(context.getBean(ConfiguredAccessPolicies.class).snapshot()).isEmpty();
        });
    }

    @Test
    void 实体动作整体替换继承授权裁剪且通配符按最终动作展开() {
        runner(ReplacementOrder.class).run(context -> {
            assertThat(context).hasNotFailed();
            var result = context.getBean(ConfiguredAccessPolicies.class).snapshot().get("order");
            assertThat(result.grants().get("merchant")).containsExactly("view");
            assertThat(result.grants().get("super-admin")).containsExactly("view");
        });
    }

    @Test
    void 实体动作增量继承且移除优先并在授权前展开最终动作() {
        runner(IncrementalOrder.class).run(context -> {
                assertThat(context).hasNotFailed();
                var effective = context.getBean(ConfiguredAccessPolicies.class).snapshot().get("order");
                assertThat(effective.actions()).containsExactly("view", "create", "update", "batch-update");
                assertThat(effective.grants().get("merchant")).containsExactlyInAnyOrderElementsOf(effective.actions());
                assertThat(effective.grants().get("super-admin")).containsExactlyInAnyOrderElementsOf(effective.actions());
                assertThat(effective.sources().get("actions")).contains("@EntCrudEntity(order)", "access.actions");
                assertThat(effective.sources()).containsEntry("actions.inherited", "ent.loom.crud.access.policies.maintainable.actions");
                assertThat(authorize(context, "merchant", "batch-update").getDecision()).isEqualTo(AccessDecision.ALLOW);
                assertThatThrownBy(() -> authorize(context, "merchant", "delete")).isInstanceOf(RuntimeException.class);
            });
    }

    @Test
    void 增加动作不自动授予身份且已知未开放动作可幂等移除() {
        runner(AddedWithoutGrant.class).run(context -> {
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
        runner(evolved, AddBatch.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ConfiguredAccessPolicies.class).snapshot().get("order").actions())
                .containsExactly("view", "update", "batch-update");
        });
        runner(evolved, FixedBatch.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ConfiguredAccessPolicies.class).snapshot().get("order").actions())
                .containsExactly("view", "batch-update");
        });
    }

    @Test
    void 未声明动作差异保持继承() {
        runner(DefaultOrder.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ConfiguredAccessPolicies.class).snapshot().get("order").actions()).containsExactly("view");
        });
    }

    @Test
    void 动作增量拒绝未知通配符重复项和未接入能力() {
        for (Class<?> entity : List.of(UnknownAction.class, WildcardAction.class, DuplicateAction.class, ExportAction.class)) {
            runner(entity).run(context -> assertThat(context).as(entity.getSimpleName()).hasFailed());
        }
    }

    @Test
    void 移除动作后显式身份重授权仍启动失败() {
        runner(RemovedGrant.class)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("超出实体最终 actions");
            });
    }

    @Test
    void 全局策略继续只接受完整动作列表() {
        runner(GLOBAL.replace("actions: [view]", "actions: {add: [view]}"), DefaultOrder.class)
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 列表替换空列表撤权与后层重新授权() {
        runner(GLOBAL.replace("          actions: [view, create, update, delete]", "          actions: {add: [view], remove: [update]}"), RegrantOrder.class).run(context -> {
            assertThat(context).hasNotFailed();
            var grants = context.getBean(ConfiguredAccessPolicies.class).snapshot().get("order").grants();
            assertThat(grants.get("merchant")).containsExactly("view", "update");
            assertThat(grants.get("super-admin")).isEmpty();
            assertThat(authorize(context, "customer", "view").getScope().getField()).isEqualTo("customerId");
            assertThatThrownBy(() -> authorize(context, "super-admin", "view")).hasMessageContaining("未声明");
        });
    }

    @Test
    void 错误注解启动失败且保留实体来源() {
        for (Class<?> entity : List.of(UnknownPolicy.class, UnknownAction.class, UnknownIdentity.class,
                UnopenedGrant.class, UnknownScope.class, ExcessGrant.class, StatsAction.class, ExportAction.class,
                InvalidMapping.class)) {
            runner(entity).run(context -> assertThat(context).as(entity.getSimpleName()).hasFailed());
        }
    }

    @Test
    void 范围缺失或关系字段缺失拒绝全量退化() {
        runner(GLOBAL.replace("scope: merchant", "scope: missing"), DefaultOrder.class).run(context -> assertThat(context).hasFailed());
        runner(GLOBAL.replace("field: merchantId", "field: absent"), DefaultOrder.class).run(context -> assertThat(context).hasFailed());
        runner(GLOBAL.replace("        scope: merchant\n", ""), DefaultOrder.class).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 公共查询必须有实体显式范围及返回白名单() {
        for (Class<?> entity : List.of(PublicMissingScope.class, PublicMissingReturn.class)) runner(entity)
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 实体规则与字段限制保持一致且动态规则只能收窄() {
        runner(PublicRules.class)
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
        runner(PublicScope.class).withAllowBeanDefinitionOverriding(true).withBean(EntityMetaRegistry.class, () -> {
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

    @Test
    void 公共展开不能开放整个关联() {
        for (Class<?> entity : List.of(PublicExpandAll.class, PublicExpandExclude.class, PublicExpandRelation.class)) {
            runner(entity).run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void 分页映射要求静态主键排序() {
        runner(ReturnOnly.class)
            .run(context -> assertThat(context).hasFailed().getFailure().hasMessageContaining("分页必须在字段上限开放主键排序: order/id"));
        runner(ReturnDetail.class)
            .run(context -> assertThat(context).hasNotFailed());
        runner(ReturnSorted.class)
            .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void 自定义策略注册表仍经过分页启动检查() {
        var custom = mock(EntityPolicyRegistry.class);
        runner(DefaultOrder.class)
            .withBean(EntityPolicyRegistry.class, () -> custom)
            .run(context -> {
                assertThat(context).hasNotFailed();
                org.mockito.Mockito.verify(custom).validatePageMappings(
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
            });
    }

    @Test
    void 实体字段上限限制配置与动态授权并校验元数据() {
        runner(FieldLimits.class).withBean(BusinessPermissionEvaluator.class, () -> (visitor, operation, rule, target) ->
                BusinessPermissionDecision.allow(AuthorizationScope.all(), AuthorizationFields.of(Map.of(
                    AuthorizationFields.Use.RETURN, Set.of("id", "title", "merchantId"),
                    AuthorizationFields.Use.SORT, Set.of("id", "title"),
                    AuthorizationFields.Use.FILTER, Set.of("active", "merchantId")))))
            .run(context -> {
                assertThat(context).hasNotFailed();
                var fields = authorize(context, "merchant", "view").getFields();
                assertThat(fields.allows(AuthorizationFields.Use.RETURN, "id")).isTrue();
                assertThat(fields.allows(AuthorizationFields.Use.RETURN, "merchantId")).isFalse();
                assertThat(fields.allows(AuthorizationFields.Use.SORT, "id")).isTrue();
                assertThat(fields.allows(AuthorizationFields.Use.SORT, "title")).isFalse();
                assertThat(fields.allows(AuthorizationFields.Use.WRITE, "title")).isFalse();
                assertThat(context.getBean(ConfiguredAccessPolicies.class).snapshot().get("order").fields()
                    .get("merchant").get(AuthorizationFields.Use.RETURN)).containsExactlyInAnyOrder("id", "title");
            });
        runner(InvalidWrite.class)
            .run(context -> assertThat(context).hasFailed());
        runner(InvalidField.class)
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 声明式策略保留动态目标拒绝类别() {
        runner(DefaultOrder.class)
            .withBean(BusinessPermissionEvaluator.class, () -> (visitor, operation, rule, target) ->
                BusinessPermissionDecision.targetInaccessible())
            .run(context -> {
                assertThat(context).hasNotFailed();
                var subject = new AccessSubject("actor", AccessSubjectType.HUMAN);
                var visitor = VisitorContext.authenticated(subject,
                    context.getBean(BusinessIdentityRegistry.class).resolve(subject, "merchant", "42"), "commerce", null, null);
                var request = new AuthorizationRequest(visitor, new BusinessOperation("order", "view"), "42");
                assertThat(context.getBean(BusinessAuthorizationService.class).authorize(request).getKind())
                    .isEqualTo(BusinessPermissionDecision.Kind.TARGET_INACCESSIBLE);
            });
    }

    @Test
    void 动态规则只能收窄实体范围与字段且公共分支不携带身份() {
        runner(PublicRules.class)
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
        runner(global, CustomerMerchant.class)
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
        runner(BatchGrant.class).run(context -> {
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
        Class<?> entity = switch (mode) {
            case "INCLUDE" -> IncludeField.class;
            case "EXCLUDE" -> ExcludeField.class;
            case "ALL" -> AllFields.class;
            default -> NoFields.class;
        };
        runner(entity).run(context -> {
            assertThat(context).hasNotFailed();
            var fields = authorize(context, "merchant", "view").getFields();
            assertThat(fields.allows(AuthorizationFields.Use.RETURN, "title")).isEqualTo(Set.of("INCLUDE", "ALL").contains(mode));
            assertThat(fields.allows(AuthorizationFields.Use.RETURN, "id")).isEqualTo(Set.of("EXCLUDE", "ALL").contains(mode));
        });
    }

    @Test
    void 未使用范围属性不产生授权且空动作实体保持默认拒绝() {
        runner(NoActions.class).run(context -> {
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
        runner(global, SortNone.class).run(context -> {
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

    @Test
    void 全局主体同样拒绝未知属性及列表字段简写() {
        for (String extra : List.of("scpoe: all", "fields: [id, title]")) {
            runner(GLOBAL.replace("scope: merchant", "scope: merchant\n        " + extra), DefaultOrder.class)
                .run(context -> assertThat(context).hasFailed());
        }
    }

    private static BusinessIdentityRegistration<?> registration(String code, String field) {
        var definition = new BusinessIdentityDefinition(code, field, BusinessIdentityIdType.LONG);
        return new BusinessIdentityRegistration<>(code, ConfiguredBusinessIdentity.class, (subject, id) -> definition.snapshot(id));
    }

    @EntCrudEntity(name = "order") static class DefaultOrder { }
    @EntCrudEntity(name = "order", httpExposed = false) static class HiddenOrder { }
    @EntCrudEntity(name = "order", policy = "maintainable", actions = {"view", "update"})
    @EntCrudAccess(identity = "merchant", addActions = "view", removeActions = "update")
    @EntCrudAccess(identity = "super-admin", removeActions = "update") static class ReplacementOrder { }
    @EntCrudEntity(name = "order", policy = "maintainable", addActions = {"view", "batch-update", "delete"}, removeActions = "delete")
    @EntCrudAccess(identity = "merchant", addActions = "batch-update") static class IncrementalOrder { }
    @EntCrudEntity(name = "order", addActions = "batch-update", removeActions = "delete") static class AddedWithoutGrant { }
    @EntCrudEntity(name = "order", addActions = "batch-update") static class AddBatch { }
    @EntCrudEntity(name = "order", actions = {"view", "batch-update"}) static class FixedBatch { }
    @EntCrudEntity(name = "order", addActions = "shpi") static class UnknownAction { }
    @EntCrudEntity(name = "order", addActions = "*") static class WildcardAction { }
    @EntCrudEntity(name = "order", addActions = {"view", "view"}) static class DuplicateAction { }
    @EntCrudEntity(name = "order", actions = "export") static class ExportAction { }
    @EntCrudEntity(name = "order", actions = "stats") static class StatsAction { }
    @EntCrudEntity(name = "order", policy = "maintainable", removeActions = "delete")
    @EntCrudAccess(identity = "merchant", addActions = "delete") static class RemovedGrant { }
    @EntCrudEntity(name = "order", policy = "maintainable")
    @EntCrudAccess(identity = "merchant", addActions = "update", removeActions = "delete")
    @EntCrudAccess(identity = "super-admin", actionsSpecified = true)
    @EntCrudAccess(identity = "customer", actions = "view") static class RegrantOrder { }
    @EntCrudEntity(name = "order", policy = "unknown") static class UnknownPolicy { }
    @EntCrudEntity(name = "order") @EntCrudAccess(identity = "stranger", actions = "view") static class UnknownIdentity { }
    @EntCrudEntity(name = "order") @EntCrudAccess(identity = "merchant", actions = "update") static class UnopenedGrant { }
    @EntCrudEntity(name = "order") @EntCrudAccess(identity = "merchant", scope = "missing") static class UnknownScope { }
    @EntCrudEntity(name = "order", policy = "maintainable", actions = "view")
    @EntCrudAccess(identity = "merchant", actions = {"view", "delete"}) static class ExcessGrant { }
    @EntCrudEntity(name = "order", policy = "maintainable", operations = "update=UPDATE_BATCH") static class InvalidMapping { }
    @EntCrudEntity(name = "order") @EntCrudAccess(identity = "public", actions = "view") static class PublicMissingScope { }
    @EntCrudEntity(name = "order") @EntCrudAccess(identity = "public", actions = "view", scope = "all") static class PublicMissingReturn { }
    @EntCrudEntity(name = "order")
    @EntCrudAccess(identity = "public", actions = "view", rules = "active=true", fields = {
        @EntCrudAccessField(use = "RETURN", mode = "INCLUDE", fields = {"id", "title"}),
        @EntCrudAccessField(use = "EXPAND", mode = "NONE")}) static class PublicRules { }
    @EntCrudEntity(name = "order")
    @EntCrudAccess(identity = "public", actions = "view", scope = "all", fields =
        @EntCrudAccessField(use = "RETURN", mode = "INCLUDE", fields = {"id", "title"})) static class PublicScope { }
    @EntCrudEntity(name = "order")
    @EntCrudAccess(identity = "public", actions = "view", scope = "all", fields = {
        @EntCrudAccessField(use = "RETURN", mode = "INCLUDE", fields = "id"),
        @EntCrudAccessField(use = "EXPAND", mode = "ALL")}) static class PublicExpandAll { }
    @EntCrudEntity(name = "order")
    @EntCrudAccess(identity = "public", actions = "view", scope = "all", fields = {
        @EntCrudAccessField(use = "RETURN", mode = "INCLUDE", fields = "id"),
        @EntCrudAccessField(use = "EXPAND", mode = "EXCLUDE")}) static class PublicExpandExclude { }
    @EntCrudEntity(name = "order")
    @EntCrudAccess(identity = "public", actions = "view", scope = "all", fields = {
        @EntCrudAccessField(use = "RETURN", mode = "INCLUDE", fields = "id"),
        @EntCrudAccessField(use = "EXPAND", mode = "INCLUDE", fields = "customer")}) static class PublicExpandRelation { }
    @EntCrudEntity(name = "order", fieldLimits = "RETURN:id") static class ReturnOnly { }
    @EntCrudEntity(name = "order", fieldLimits = "RETURN:id", operations = "view=DETAIL") static class ReturnDetail { }
    @EntCrudEntity(name = "order", fieldLimits = {"RETURN:id", "SORT:id"}) static class ReturnSorted { }
    @EntCrudEntity(name = "order", fieldLimits = {"RETURN:id,title", "FILTER:active", "SORT:id"}) static class FieldLimits { }
    @EntCrudEntity(name = "order", fieldLimits = "WRITE:id") static class InvalidWrite { }
    @EntCrudEntity(name = "order", fieldLimits = "RETURN:missing") static class InvalidField { }
    @EntCrudEntity(name = "order") @EntCrudAccess(identity = "customer", actions = "view", scope = "customer-merchant") static class CustomerMerchant { }
    @EntCrudEntity(name = "order", policy = "maintainable", actions = {"view", "update", "batch-update"})
    @EntCrudAccess(identity = "merchant", actions = {"view", "update"})
    @EntCrudAccess(identity = "customer", actions = "batch-update") static class BatchGrant { }
    @EntCrudEntity(name = "order") @EntCrudAccess(identity = "merchant", fields = @EntCrudAccessField(use = "RETURN", mode = "INCLUDE", fields = "title")) static class IncludeField { }
    @EntCrudEntity(name = "order") @EntCrudAccess(identity = "merchant", fields = @EntCrudAccessField(use = "RETURN", mode = "EXCLUDE", fields = "title")) static class ExcludeField { }
    @EntCrudEntity(name = "order") @EntCrudAccess(identity = "merchant", fields = @EntCrudAccessField(use = "RETURN", mode = "ALL")) static class AllFields { }
    @EntCrudEntity(name = "order") @EntCrudAccess(identity = "merchant", fields = @EntCrudAccessField(use = "RETURN", mode = "NONE")) static class NoFields { }
    @EntCrudEntity(name = "order", actionsSpecified = true) static class NoActions { }
    @EntCrudEntity(name = "order") @EntCrudAccess(identity = "merchant", fields = @EntCrudAccessField(use = "SORT", mode = "NONE")) static class SortNone { }
    private static class Order { }
}
