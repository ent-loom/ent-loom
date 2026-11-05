package com.entloom.crud.starter.config;

import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.governance.adapter.CrudBusinessAuthorizationAdapter;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.authorization.BusinessPermissionDecision;
import com.entloom.crud.core.governance.authorization.BusinessPermissionEvaluator;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyTemplate;
import com.entloom.crud.core.governance.policy.PolicyIdentityBranch;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.annotations.EntCrudEntity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 实体注解选择 Java 策略，授权仍走统一执行链。 */
class EntityPolicySelectionTest {
    private static final BusinessOperation VIEW = new BusinessOperation("order", "view");

    private ApplicationContextRunner runner(Class<?> declaration) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CrudPolicyAutoConfiguration.class))
            .withBean(EntityMetaRegistry.class, () -> CrudPolicyAutoConfigurationTest.metas(Order.class))
            .withBean(CrudEntityDeclarations.class, () -> new CrudEntityDeclarations(List.of(declaration)))
            .withPropertyValues("ent.loom.crud.controller.enabled=true");
    }

    private ApplicationContextRunner javaPolicy(Class<?> declaration) {
        return runner(declaration)
            .withBean(EntityPolicyTemplate.class, () -> new EntityPolicyTemplate("order-read", "Project#orderRead",
                List.of(new EntityPolicyTemplate.Rule("view", PolicyIdentityBranch.publicBranch(), List.of()))))
;
    }

    @Test
    void 实体policy选择Java只读策略且写入不会退回旧治理() {
        javaPolicy(ReadOrder.class)
            .withBean(BusinessPermissionEvaluator.class, () -> (visitor, operation, rule, target) ->
                BusinessPermissionDecision.allow(AuthorizationScope.equal("ownerId", "owner-1"),
                    AuthorizationFields.of(Map.of(AuthorizationFields.Use.RETURN, List.of("id")))))
            .run(context -> {
                assertThat(context).hasNotFailed();
                var adapter = context.getBean(CrudBusinessAuthorizationAdapter.class);
                var query = QuerySpec.builder().rootType(Order.class).op(QueryOperation.LIST).build();
                assertThat(adapter.governs(query)).isTrue();
                var result = adapter.authorize(VisitorContext.anonymous("base", null, null), query, null);
                assertThat(result.getScope().getField()).isEqualTo("ownerId");
                assertThat(result.getScope().getValues()).containsExactly("owner-1");
                assertThat(result.getFields().allows(AuthorizationFields.Use.RETURN, "id")).isTrue();
                assertThat(result.getFields().allows(AuthorizationFields.Use.RETURN, "ownerId")).isFalse();
                var write = CommandSpec.builder().rootType(Order.class).op(CommandOperation.UPDATE).build();
                assertThat(adapter.governs(write)).isTrue();
                assertThatThrownBy(() -> adapter.authorize(VisitorContext.anonymous("base", null, null), write, "1"))
                    .hasMessageContaining("映射");
                assertThat(context.getBean(EntityPolicyRegistry.class)
                    .requireDeclaration(VIEW, PolicyIdentityBranch.publicBranch()).getBindingSource())
                    .contains("@EntCrudEntity(order)", "policy");
            });
    }

    @Test
    void 未注册策略启动失败并定位实体注解() {
        runner(MissingPolicyOrder.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("未知 Java 策略: missing-read")
                .hasStackTraceContaining("@EntCrudEntity(order)");
        });
    }

    @Test
    void 已移除的Yaml授权配置严格拒绝绑定() {
        javaPolicy(ReadOrder.class)
            .withPropertyValues("ent.loom.crud.access.policies.order-read.actions[0]=view")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("policies.order-read.actions");
            });
    }

    @Test
    void Java策略重名同样拒绝覆盖() {
        javaPolicy(ReadOrder.class)
            .withBean("anotherOrderRead", EntityPolicyTemplate.class, () -> new EntityPolicyTemplate(
                "order-read", "AnotherProject#orderRead", List.of(
                    new EntityPolicyTemplate.Rule("view", PolicyIdentityBranch.publicBranch(), List.of()))))
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("策略模板重复: order-read")
                    .hasStackTraceContaining("Project#orderRead").hasStackTraceContaining("AnotherProject#orderRead");
            });
    }

    @Test
    void 关闭通用Http的内部实体无需选择访问方式() {
        runner(InternalOrder.class).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void 声明policy却缺少策略编译组件拒绝启动() {
        new ApplicationContextRunner()
            .withUserConfiguration(CrudEntityDeclarationConfiguration.class)
            .withBean(EntityMetaRegistry.class, () -> CrudPolicyAutoConfigurationTest.metas(Order.class))
            .withBean(CrudEntityDeclarations.class, () -> new CrudEntityDeclarations(List.of(ReadOrder.class)))
            .withPropertyValues("ent.loom.crud.controller.enabled=true")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasMessageContaining("HTTP 实体策略未编译")
                    .hasMessageContaining("order-read").hasMessageContaining("@EntCrudEntity(order)");
            });
    }

    @EntCrudEntity(name = "order", policy = "order-read")
    private static class ReadOrder { }

    @EntCrudEntity(name = "order", policy = "missing-read")
    private static class MissingPolicyOrder { }

    @EntCrudEntity(name = "order", httpExposed = false)
    private static class InternalOrder { }

    private static class Order { }
}
