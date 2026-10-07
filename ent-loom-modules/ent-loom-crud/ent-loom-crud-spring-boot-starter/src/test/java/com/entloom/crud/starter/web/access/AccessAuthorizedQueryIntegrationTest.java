package com.entloom.crud.starter.web.access;

import com.entloom.crud.annotations.EntAccess;
import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.AuthenticationMode;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.core.capability.query.engine.AuthorizedQueryExecutor;
import com.entloom.crud.core.capability.query.gateway.QueryGateway;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.admission.AccessAdmissionService;
import com.entloom.crud.core.governance.admission.AccessContextScope;
import com.entloom.crud.core.governance.admission.AccessTaskSnapshot;
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
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.starter.config.CrudAccessWebAutoConfiguration;
import com.entloom.crud.starter.config.CrudAutoConfiguration;
import com.entloom.crud.starter.config.CrudPolicyAutoConfiguration;
import com.entloom.crud.starter.support.StarterJdbcTestSupportConfiguration;
import com.entloom.crud.starter.support.TestOrderEntity;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 验证真实 Starter → MVC → Gateway → 授权 → JDBC → 投影 → 审计闭环。 */
class AccessAuthorizedQueryIntegrationTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CrudAutoConfiguration.class,
            CrudPolicyAutoConfiguration.class, CrudAccessWebAutoConfiguration.class))
        .withUserConfiguration(StarterJdbcTestSupportConfiguration.class, ProjectConfiguration.class);

    @Test
    void mvc_gateway_should_apply_scope_projection_and_count_and_record_real_success() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AuthorizedQueryExecutor.class);
            MockMvc mvc = mvc(context.getSourceApplicationContext());
            mvc.perform(get("/authorized/list")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].orderNo").value("ORD-2"))
                .andExpect(jsonPath("$[1].orderNo").value("ORD-4"))
                .andExpect(jsonPath("$[0].id").doesNotExist());
            mvc.perform(get("/authorized/page")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].orderNo").value("ORD-2"));
            State state = context.getBean(State.class);
            assertThat(state.events).hasSize(2).allSatisfy(event -> {
                assertThat(event.getOutcome()).isEqualTo(CrudGovernanceAuditOutcome.SUCCESS);
                assertThat(event.getSubject().getSubjectId()).isEqualTo("actor-1");
                assertThat(event.getVisitor().isAnonymous()).isTrue();
                assertThat(event.getAuthorizationResult().getScope().getKind()).isEqualTo(AuthorizationScope.Kind.IN);
            });
            state.allowed.set(false);
            mvc.perform(get("/authorized/list")).andExpect(status().isForbidden());
            assertThat(state.events.get(2).getOutcome()).isEqualTo(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED);
            assertThat(AccessContextScope.currentVisitor()).isEmpty();
        });
    }

    @Test
    void mvc_callable_should_deny_without_context_and_execute_only_after_trusted_restore() {
        runner.run(context -> {
            MockMvc mvc = mvc(context.getSourceApplicationContext());
            var rejected = mvc.perform(get("/authorized/async")).andReturn();
            mvc.perform(asyncDispatch(rejected)).andExpect(status().isForbidden());
            State state = context.getBean(State.class);
            assertThat(state.decisions.get()).isZero();
            assertThat(state.events).hasSize(1);
            assertThat(state.events.get(0).getOutcome()).isEqualTo(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED);
            var restored = mvc.perform(get("/authorized/restored")).andReturn();
            mvc.perform(asyncDispatch(restored)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
            assertThat(state.decisions.get()).isEqualTo(1);
            assertThat(state.events.get(1).getOutcome()).isEqualTo(CrudGovernanceAuditOutcome.SUCCESS);
            assertThat(AccessContextScope.currentVisitor()).isEmpty();
        });
    }

    private static MockMvc mvc(org.springframework.web.context.WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean("crudAccessAuthenticationFilter", FilterRegistrationBean.class).getFilter()).build();
    }

    static class State {
        final AtomicBoolean allowed = new AtomicBoolean(true);
        final AtomicInteger decisions = new AtomicInteger();
        final List<CrudGovernanceAuditEvent> events = new CopyOnWriteArrayList<>();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class ProjectConfiguration {
        @Bean State state() { return new State(); }
        @Bean CrudGovernanceAuditRecorder audit(State state) { return state.events::add; }
        @Bean CrudHttpAuthenticationSource authentication() {
            return request -> new HttpAccessAuthentication(credentials(), "trusted-entry", "request-1", "trace-1");
        }
        @Bean BusinessOperationDeclaration view(EntityMetaRegistry entities) {
            return new BusinessOperationDeclaration(operation(entities), "Project#view");
        }
        @Bean EntityPolicyTemplate template() {
            return new EntityPolicyTemplate("public", "Project#public", Collections.singletonList(
                new EntityPolicyTemplate.Rule("view", PolicyIdentityBranch.publicBranch(), Collections.emptyList())));
        }
        @Bean EntityPolicyBinding binding() {
            return new EntityPolicyBinding(TestOrderEntity.class, "public", "Order#binding", Collections.emptyMap());
        }
        @Bean CrudOperationMapping listMapping(EntityMetaRegistry entities) {
            return new CrudOperationMapping(CrudOperationKey.of(QueryOperation.LIST), null, operation(entities), "Project#list");
        }
        @Bean CrudOperationMapping pageMapping(EntityMetaRegistry entities) {
            return new CrudOperationMapping(CrudOperationKey.of(QueryOperation.PAGE), null, operation(entities), "Project#page");
        }
        @Bean BusinessPermissionEvaluator permissions(State state) {
            return (visitor, operation, rule, target) -> {
                state.decisions.incrementAndGet();
                return state.allowed.get() ? BusinessPermissionDecision.allow(
                    AuthorizationScope.in("id", List.of(2L, 4L)), AuthorizationFields.of(
                        Collections.singletonMap(AuthorizationFields.Use.RETURN, Collections.singletonList("orderNo"))))
                    : BusinessPermissionDecision.deny();
            };
        }
        @Bean Controller controller(QueryGateway gateway, AccessAdmissionService admissions) {
            return new Controller(gateway, admissions);
        }
        private BusinessOperation operation(EntityMetaRegistry entities) {
            return new BusinessOperation(entities.getEntityMeta(TestOrderEntity.class).getResourceDescriptor().getResourceCode(), "view");
        }
    }

    private static CredentialAuthentication credentials() {
        return CredentialAuthentication.authenticated(new AccessSubject("actor-1", AccessSubjectType.HUMAN));
    }

    @RestController
    @EntAccess(AuthenticationMode.ANONYMOUS)
    static class Controller {
        private final QueryGateway gateway;
        private final AccessAdmissionService admissions;
        Controller(QueryGateway gateway, AccessAdmissionService admissions) { this.gateway = gateway; this.admissions = admissions; }
        private QuerySpec<CrudRecord> query() {
            return QuerySpec.<CrudRecord>builder().rootType(TestOrderEntity.class).resultType(CrudRecord.class).build();
        }
        @GetMapping("/authorized/list") List<CrudRecord> list() { return gateway.list(query()); }
        @GetMapping("/authorized/page") PageResult<CrudRecord> page() {
            return gateway.page(query().toBuilder().page(new PageRequest(1, 1)).build());
        }
        @GetMapping("/authorized/async") Callable<List<CrudRecord>> async() { return () -> gateway.list(query()); }
        @GetMapping("/authorized/restored") Callable<List<CrudRecord>> restored() {
            AccessTaskSnapshot snapshot = AccessTaskSnapshot.capture(AccessContextScope.currentAdmission().orElseThrow());
            return () -> AccessContextScope.withAdmission(snapshot.restore(admissions, AccessAuthorizedQueryIntegrationTest::credentials),
                () -> gateway.list(query()));
        }
        @ExceptionHandler(PermissionDeniedException.class) ResponseEntity<String> denied() {
            return ResponseEntity.status(403).body("授权拒绝");
        }
    }
}
