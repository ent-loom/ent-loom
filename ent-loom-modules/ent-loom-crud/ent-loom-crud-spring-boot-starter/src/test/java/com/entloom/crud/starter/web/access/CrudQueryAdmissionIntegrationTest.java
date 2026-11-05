package com.entloom.crud.starter.web.access;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.admission.AccessContextScope;
import com.entloom.crud.core.governance.admission.CredentialAuthentication;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.authorization.BusinessPermissionDecision;
import com.entloom.crud.core.governance.authorization.BusinessPermissionEvaluator;
import com.entloom.crud.core.governance.policy.BusinessOperationDeclaration;
import com.entloom.crud.core.governance.policy.CrudOperationMapping;
import com.entloom.crud.core.governance.policy.EntityPolicyBinding;
import com.entloom.crud.core.governance.policy.EntityPolicyTemplate;
import com.entloom.crud.core.governance.policy.PolicyIdentityBranch;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.crud.starter.config.CrudAccessWebAutoConfiguration;
import com.entloom.crud.starter.config.CrudAutoConfiguration;
import com.entloom.crud.starter.config.CrudPolicyAutoConfiguration;
import com.entloom.crud.starter.support.StarterJdbcTestSupportConfiguration;
import com.entloom.crud.starter.support.TestOrderEntity;
import com.entloom.crud.starter.web.registry.ExposedEntityRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 内置 HTTP 查询复用真实准入、Gateway、授权、JDBC 与审计，不要求业务 Controller 或旧主体。 */
class CrudQueryAdmissionIntegrationTest {
    private static final String ROOT = "/api/ent-crud/TestOrderEntity";
    private final WebApplicationContextRunner base = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CrudAutoConfiguration.class,
            CrudPolicyAutoConfiguration.class, CrudAccessWebAutoConfiguration.class))
        .withUserConfiguration(StarterJdbcTestSupportConfiguration.class, MvcConfiguration.class)
        .withPropertyValues("ent.loom.crud.controller.enabled=true");
    private final WebApplicationContextRunner governed = base.withUserConfiguration(PolicyConfiguration.class);
    private final WebApplicationContextRunner authenticated = governed.withUserConfiguration(AuthenticationConfiguration.class);

    @Test
    void 通用四种读取复用授权范围字段投影计数和审计且无需旧主体() {
        authenticated.run(context -> {
            MockMvc mvc = mvc(context.getSourceApplicationContext());
            mvc.perform(post(ROOT + "/list").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].orderNo").value("ORD-4"))
                .andExpect(jsonPath("$.data.items[0].id").doesNotExist());
            mvc.perform(post(ROOT + "/page").header("Authorization", "Bearer valid")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"options\":{\"page\":2,\"limit\":1}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].orderNo").value("ORD-2"))
                .andExpect(jsonPath("$.data.page.total").value(2));
            for (String operation : List.of("findOne", "detail")) {
                mvc.perform(post(ROOT + "/" + operation).header("Authorization", "Bearer valid")
                        .contentType(MediaType.APPLICATION_JSON).content(operation.equals("detail")
                            ? "{\"id\":2}" : "{\"options\":{\"filter\":{\"id\":2}}}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.item.orderNo").value("ORD-2"))
                    .andExpect(jsonPath("$.data.item.id").doesNotExist());
            }
            assertThat(context.getBean(State.class).events).hasSize(4).allSatisfy(event -> {
                assertThat(event.getOutcome()).isEqualTo(CrudGovernanceAuditOutcome.SUCCESS);
                assertThat(event.getSubject().getSubjectId()).isEqualTo("actor");
                assertThat(event.getVisitor().getAccessEntry()).isEqualTo("trusted-entry");
            });
            assertThat(AccessContextScope.currentVisitor()).isEmpty();
            assertThat(AccessContextScope.isAuthorizationRequired()).isFalse();
        });
    }

    @Test
    void 公共策略允许无凭证但伪造身份头不生效且无效凭证拒绝() {
        authenticated.run(context -> {
            MockMvc mvc = mvc(context.getSourceApplicationContext());
            mvc.perform(post(ROOT + "/list").header("X-Subject-Id", "actor").header("X-Access-Entry", "forged-entry"))
                .andExpect(status().isOk());
            mvc.perform(post(ROOT + "/list").header("Authorization", "Bearer expired"))
                .andExpect(status().isForbidden());
            assertThat(context.getBean(State.class).events).singleElement().satisfies(event -> {
                assertThat(event.getVisitor().isAnonymous()).isTrue();
                assertThat(event.getVisitor().getAccessEntry()).isEqualTo("trusted-entry");
            });
            assertThat(AccessContextScope.currentVisitor()).isEmpty();
        });
    }

    @Test
    void 缺少可信认证来源时已绑定策略的查询拒绝() {
        governed.run(context -> mvc(context.getSourceApplicationContext()).perform(post(ROOT + "/list"))
            .andExpect(status().isForbidden()));
    }

    @Test
    void 新授权入口继续拒绝未适配的实体输出场景和关系展开() {
        authenticated.run(context -> {
            MockMvc mvc = mvc(context.getSourceApplicationContext());
            for (String body : List.of("{\"options\":{\"resultMode\":\"ENTITY\"}}",
                "{\"options\":{\"expandRelations\":[\"items\"]}}",
                "{\"options\":{\"selectFields\":[\"id\"]}}")) {
                mvc.perform(post(ROOT + "/list").header("Authorization", "Bearer valid")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            }
            mvc.perform(post(ROOT + "/list/custom").header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden());
            assertThat(context.getBean(State.class).events).hasSize(4).allSatisfy(event ->
                assertThat(event.getOutcome()).isEqualTo(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED));
            assertThat(AccessContextScope.currentVisitor()).isEmpty();
        });
    }

    @Test
    void 自定义基础路径及实体别名仍进入相同授权且未暴露实体保持拒绝() {
        authenticated.withPropertyValues("ent.loom.crud.controller.base-path=/business/read").run(context -> {
            ExposedEntityRegistry exposed = context.getBean(ExposedEntityRegistry.class);
            exposed.register("orders", TestOrderEntity.class);
            MockMvc mvc = mvc(context.getSourceApplicationContext());
            mvc.perform(post("/business/read/orders/list").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2));
            exposed.setIncludeEntities(java.util.Set.of());
            mvc.perform(post("/business/read/orders/list").header("Authorization", "Bearer valid"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ENTITY_NOT_EXPOSED"));
            assertThat(context.getBean(State.class).events).hasSize(1);
        });
    }

    @Test
    void 未绑定策略的实体继续使用原有治理() {
        base.run(context -> mvc(context.getSourceApplicationContext()).perform(post(ROOT + "/page"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total").value(21)));
    }

    private static MockMvc mvc(WebApplicationContext context) {
        var builder = MockMvcBuilders.webAppContextSetup(context);
        if (context.containsBean("crudAccessAuthenticationFilter")) {
            builder.addFilters(context.getBean("crudAccessAuthenticationFilter", FilterRegistrationBean.class).getFilter());
        }
        return builder.build();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class MvcConfiguration { }

    @Configuration(proxyBeanMethods = false)
    static class AuthenticationConfiguration {
        @Bean CrudHttpAuthenticationSource authentication() {
            return request -> new HttpAccessAuthentication(request.getHeader("Authorization") == null
                ? CredentialAuthentication.absent() : "Bearer valid".equals(request.getHeader("Authorization"))
                ? CredentialAuthentication.authenticated(new AccessSubject("actor", AccessSubjectType.HUMAN))
                : CredentialAuthentication.invalid(), "trusted-entry", "request", "trace");
        }
    }

    static class State {
        final List<CrudGovernanceAuditEvent> events = new ArrayList<>();
    }

    @Configuration(proxyBeanMethods = false)
    static class PolicyConfiguration {
        private static final BusinessOperation VIEW = new BusinessOperation("TestOrderEntity", "view");
        @Bean State state() { return new State(); }
        @Bean CrudGovernanceAuditRecorder audit(State state) { return state.events::add; }
        @Bean @Primary CrudSubjectResolver unusedLegacySubject() {
            return () -> { throw new ValidationException("新授权查询不应解析旧主体"); };
        }
        @Bean BusinessOperationDeclaration view() { return new BusinessOperationDeclaration(VIEW, "test"); }
        @Bean EntityPolicyTemplate policy() {
            return new EntityPolicyTemplate("read", "test", List.of(new EntityPolicyTemplate.Rule(
                "view", PolicyIdentityBranch.publicBranch(), List.of())));
        }
        @Bean EntityPolicyBinding binding() {
            return new EntityPolicyBinding(TestOrderEntity.class, "read", "test", Map.of());
        }
        @Bean CrudOperationMapping listMapping() { return mapping(QueryOperation.LIST); }
        @Bean CrudOperationMapping pageMapping() { return mapping(QueryOperation.PAGE); }
        @Bean CrudOperationMapping findMapping() { return mapping(QueryOperation.FIND_ONE); }
        @Bean CrudOperationMapping detailMapping() { return mapping(QueryOperation.DETAIL); }
        private CrudOperationMapping mapping(QueryOperation operation) {
            return new CrudOperationMapping(CrudOperationKey.of(operation), null, VIEW, "test");
        }
        @Bean BusinessPermissionEvaluator permissions() {
            return (visitor, operation, rule, target) -> BusinessPermissionDecision.allow(
                AuthorizationScope.in("id", List.of(2L, 4L)), AuthorizationFields.of(
                    Map.of(AuthorizationFields.Use.RETURN, List.of("orderNo"),
                        AuthorizationFields.Use.SORT, List.of("createdAt", "id"))));
        }
    }
}
