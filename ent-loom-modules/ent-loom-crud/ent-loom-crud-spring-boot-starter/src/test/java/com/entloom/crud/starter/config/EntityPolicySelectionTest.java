package com.entloom.crud.starter.config;

import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.governance.adapter.CrudBusinessAuthorizationAdapter;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.authorization.BusinessPermissionDecision;
import com.entloom.crud.core.governance.authorization.BusinessPermissionEvaluator;
import com.entloom.crud.core.governance.policy.BusinessOperationDeclaration;
import com.entloom.crud.core.governance.policy.CrudOperationMapping;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyTemplate;
import com.entloom.crud.core.governance.policy.PolicyIdentityBranch;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.declaration.EntityDeclarations;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** policy 对 YAML 与 Java 使用相同绑定入口；不为 Java 策略创建另一种访问模式。 */
class EntityPolicySelectionTest {
    private static final BusinessOperation VIEW = new BusinessOperation("order", "view");

    private ApplicationContextRunner runner(String declaration) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CrudPolicyAutoConfiguration.class))
            .withBean(EntityMetaRegistry.class, () -> CrudPolicyAutoConfigurationTest.metas(Order.class))
            .withBean(EntityDeclarations.class, () -> files(declaration))
            .withPropertyValues("ent.loom.crud.controller.enabled=true");
    }

    private ApplicationContextRunner javaPolicy(String declaration) {
        return runner(declaration)
            .withBean(EntityPolicyTemplate.class, () -> new EntityPolicyTemplate("order-read", "Project#orderRead",
                List.of(new EntityPolicyTemplate.Rule("view", PolicyIdentityBranch.publicBranch(), List.of()))))
            .withBean(BusinessOperationDeclaration.class, () -> new BusinessOperationDeclaration(VIEW, "Project#view"))
            .withBean(CrudOperationMapping.class, () -> new CrudOperationMapping(
                CrudOperationKey.of(QueryOperation.LIST), null, VIEW, "Project#list"));
    }

    @Test
    void 实体policy选择Java只读策略且写入不会退回旧治理() {
        javaPolicy("crud: {access: {policy: order-read}}")
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
                    .contains("order.yml", "crud.access.policy");
            });
    }

    @Test
    void 未注册策略启动失败并定位实体文件() {
        runner("crud: {access: {policy: missing-read}}").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("模板未注册: missing-read")
                .hasStackTraceContaining("order.yml").hasStackTraceContaining("crud.access.policy");
        });
    }

    @Test
    void Yaml与Java同名策略拒绝覆盖并报告双方来源() {
        javaPolicy("crud: {access: {policy: order-read}}")
            .withPropertyValues("ent.loom.crud.access.policies.order-read.actions[0]=view")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("策略模板重复: order-read")
                    .hasStackTraceContaining("Project#orderRead")
                    .hasStackTraceContaining("ent.loom.crud.access.policies.order-read");
            });
    }

    @Test
    void Java策略重名同样拒绝覆盖() {
        javaPolicy("crud: {access: {policy: order-read}}")
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
        runner("crud: {http-exposed: false}").run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void 声明policy却缺少策略编译组件拒绝启动() {
        new ApplicationContextRunner()
            .withUserConfiguration(CrudEntityDeclarationConfiguration.class)
            .withBean(EntityMetaRegistry.class, () -> CrudPolicyAutoConfigurationTest.metas(Order.class))
            .withBean(EntityDeclarations.class, () -> files("crud: {access: {policy: order-read}}"))
            .withPropertyValues("ent.loom.crud.controller.enabled=true")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasMessageContaining("HTTP 实体策略未编译")
                    .hasMessageContaining("order-read").hasMessageContaining("order.yml");
            });
    }

    private EntityDeclarations files(String content) {
        return new EntityDeclarations(new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8), "ent/entities/order.yml") {
            @Override public String getFilename() { return "order.yml"; }
        });
    }

    private static class Order { }
}
