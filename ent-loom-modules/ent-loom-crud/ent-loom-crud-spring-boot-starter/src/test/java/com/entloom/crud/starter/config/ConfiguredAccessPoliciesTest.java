package com.entloom.crud.starter.config;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.authorization.BusinessAuthorizationService;
import com.entloom.crud.core.governance.authorization.BusinessPermissionDecision;
import com.entloom.crud.core.governance.authorization.BusinessPermissionEvaluator;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistration;
import com.entloom.crud.core.governance.policy.CrudOperationMappingRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyBinding;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.governance.policy.PolicyIdentityBranch;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
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
                field-plans:
                  owner:
                    RETURN: [id]
                    FILTER: [ownerId]
        """;
    private final BusinessOperation view = new BusinessOperation("order", "view");

    private ApplicationContextRunner runner(String yaml) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CrudPolicyAutoConfiguration.class))
            .withBean(EntityMetaRegistry.class, () -> CrudPolicyAutoConfigurationTest.metas(Order.class))
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
            YAML.replace("order:\n            operations", "unknown:\n            operations"),
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
    private static final class Order { }
}
