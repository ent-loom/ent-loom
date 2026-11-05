package com.entloom.crud.starter.config;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.governance.adapter.CrudBusinessAuthorizationAdapter;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.authorization.BusinessAuthorizationService;
import com.entloom.crud.core.governance.authorization.BusinessPermissionDecision;
import com.entloom.crud.core.governance.authorization.BusinessPermissionEvaluator;
import com.entloom.crud.core.governance.policy.BusinessOperationCatalog;
import com.entloom.crud.core.governance.policy.EntityPolicyTemplate;
import com.entloom.crud.core.governance.policy.PolicyIdentityBranch;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

/** 验证真实按次授权链：静态边界不能被 Java 决策或显式目录绕过。 */
class CrudPolicyDeclarationsTest {
    private static final BusinessOperation VIEW = new BusinessOperation("order", "view");

    private ApplicationContextRunner runner(Class<?> type, String... actions) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CrudPolicyAutoConfiguration.class))
            .withBean(EntityMetaRegistry.class, () -> CrudPolicyAutoConfigurationTest.metas(type))
            .withBean(CrudEntityDeclarations.class, () -> new CrudEntityDeclarations(List.of(type)))
            .withBean(EntityPolicyTemplate.class, () -> new EntityPolicyTemplate("read", "Test#read",
                List.of(actions).stream().map(action -> new EntityPolicyTemplate.Rule(action,
                    PolicyIdentityBranch.publicBranch(), List.of())).toList()));
    }

    @Test
    void Java策略声明本身不授权() {
        runner(ReadOrder.class, "view").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(BusinessAuthorizationService.class).authorize(request()).getDecision())
                .isEqualTo(AccessDecision.DENY);
        });
    }

    @Test
    void CRUD与MVC复用范围且动态字段不能越过实体上限() {
        runner(ReadOrder.class, "view")
            .withBean(BusinessPermissionEvaluator.class, () -> (visitor, operation, rule, target) ->
                BusinessPermissionDecision.allow(AuthorizationScope.equal("ownerId", "owner-1"),
                    AuthorizationFields.of(Map.of(AuthorizationFields.Use.RETURN, List.of("id", "ownerId"),
                        AuthorizationFields.Use.WRITE, List.of("ownerId")))))
            .run(context -> {
                assertThat(context).hasNotFailed();
                var mvc = context.getBean(BusinessAuthorizationService.class).authorize(request());
                var crud = context.getBean(CrudBusinessAuthorizationAdapter.class).authorize(request().getVisitor(),
                    QuerySpec.builder().rootType(ReadOrder.class).op(QueryOperation.PAGE).build(), null);
                assertThat(mvc.getScope().getField()).isEqualTo(crud.getScope().getField()).isEqualTo("ownerId");
                assertThat(mvc.getScope().getValues()).containsExactly("owner-1");
                assertThat(mvc.getFields().snapshot()).isEqualTo(crud.getFields().snapshot());
                assertThat(mvc.getFields().allows(AuthorizationFields.Use.RETURN, "ownerId")).isFalse();
                assertThat(mvc.getFields().allows(AuthorizationFields.Use.WRITE, "ownerId")).isFalse();
                assertThat(mvc.getFields().allows(AuthorizationFields.Use.RETURN, "id")).isTrue();
            });
    }

    @Test
    void 无数据范围不会退化全量() {
        runner(ReadOrder.class, "view")
            .withBean(BusinessPermissionEvaluator.class, () -> (visitor, operation, rule, target) ->
                BusinessPermissionDecision.allow(AuthorizationScope.none(), rule.getFieldLimit()))
            .run(context -> assertThat(context.getBean(BusinessAuthorizationService.class)
                .authorize(request()).getScope().getKind()).isEqualTo(AuthorizationScope.Kind.NONE));
    }

    @Test
    void Java策略不能声明实体未开放的写入() {
        runner(ReadOrder.class, "view", "update").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("Java 策略超出实体动作上限: update");
        });
    }

    @Test
    void 空动作集合关闭目录() {
        runner(ClosedOrder.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(BusinessOperationCatalog.class).snapshot()).isEmpty();
        });
    }

    @Test
    void 不支持的动作和跨动作映射启动失败() {
        runner(UnsupportedOrder.class, "export").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("动作缺少已支持的执行入口: export");
        });
        runner(CrossMappingOrder.class, "view").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("通用映射不能跨动作授权: UPDATE");
        });
    }

    private AuthorizationRequest request() {
        return new AuthorizationRequest(VisitorContext.anonymous("base", null, null), VIEW, null);
    }

    @EntCrudEntity(name = "order", policy = "read", fieldLimits = {"RETURN:id", "SORT:id"})
    static class ReadOrder { }
    @EntCrudEntity(name = "order", policy = "read", actions = {})
    static class ClosedOrder { }
    @EntCrudEntity(name = "order", policy = "read", actions = "export")
    static class UnsupportedOrder { }
    @EntCrudEntity(name = "order", policy = "read", operations = "view=UPDATE")
    static class CrossMappingOrder { }
}
