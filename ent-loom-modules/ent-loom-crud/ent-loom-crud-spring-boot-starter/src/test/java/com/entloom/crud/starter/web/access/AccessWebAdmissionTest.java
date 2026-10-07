package com.entloom.crud.starter.web.access;

import com.entloom.crud.annotations.EntAccess;
import com.entloom.crud.api.access.AccessContext;
import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.AuthenticationMode;
import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.core.capability.query.gateway.QueryGateway;
import com.entloom.crud.core.capability.query.gateway.QueryGatewayImpl;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.execution.ExecutionPipeline;
import com.entloom.crud.core.governance.service.CrudGovernanceService;
import com.entloom.crud.core.runtime.router.QueryRouter;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.admission.AccessContextScope;
import com.entloom.crud.core.governance.admission.CredentialAuthentication;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistration;
import com.entloom.crud.starter.config.CrudAccessWebAutoConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.DispatcherType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccessWebAdmissionTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CrudAccessWebAutoConfiguration.class))
        .withUserConfiguration(MvcConfiguration.class, AuthenticationConfiguration.class);

    @Test
    void http_should_enforce_full_credential_matrix_independent_of_context_parameter() {
        runner.run(context -> {
            MockMvc mvc = mvc(context.getSourceApplicationContext());
            for (String path : new String[]{"/access/required", "/access/optional", "/access/anonymous"}) {
                mvc.perform(get(path)).andExpect(path.endsWith("required") ? status().isForbidden() : status().isOk());
                mvc.perform(get(path).header("Authorization", "Bearer merchant")).andExpect(status().isOk());
                mvc.perform(get(path).header("Authorization", "Bearer expired")).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
            }
            assertThat(context.getBean(Calls.class).controllerCalls.get()).isEqualTo(5);
            assertThat(context.getBean(Calls.class).authenticationCalls.get()).isEqualTo(9);
        });
    }

    @Test
    void typed_context_should_ignore_forged_payload_headers_query_and_request_attributes() {
        runner.run(context -> {
            mvc(context.getSourceApplicationContext()).perform(post("/access/typed")
                    .header("Authorization", "Bearer merchant")
                    .header("X-Subject-Id", "actor-forged").header("X-Access-Entry", "system")
                    .queryParam("identityCode", "teacher").queryParam("identityId", "teacher-forged")
                    .requestAttr(AccessWebRequestContext.class.getName(), Map.of("subjectId", "actor-forged"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"subjectId\":\"actor-forged\",\"identityId\":\"merchant-forged\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("actor-1|merchant-1|merchant|trusted-entry|request-1|trace-1"));
            assertThat(context.getBean(Calls.class).authenticationCalls.get()).isEqualTo(1);
            assertThat(AccessWebRequestContext.find(context.getBean(Calls.class).request.get())).isNull();
        });
    }

    @Test
    void required_identity_should_be_enforced_even_without_context_parameter() {
        runner.run(context -> {
            MockMvc mvc = mvc(context.getSourceApplicationContext());
            mvc.perform(get("/access/merchant-only").header("Authorization", "Bearer teacher")).andExpect(status().isForbidden());
            mvc.perform(get("/access/merchant-only").header("Authorization", "Bearer merchant")).andExpect(status().isOk());
            assertThat(context.getBean(Calls.class).controllerCalls.get()).isEqualTo(1);
        });
    }

    @Test
    void interface_should_restrict_trusted_entry_and_authenticated_subject_type() {
        runner.run(context -> {
            MockMvc mvc = mvc(context.getSourceApplicationContext());
            mvc.perform(get("/access/merchant-entry").header("Authorization", "Bearer merchant")
                    .header("X-Access-Entry", "internal-entry"))
                .andExpect(status().isOk());
            mvc.perform(get("/access/merchant-entry").header("Authorization", "Bearer merchant-other-entry")
                    .header("X-Access-Entry", "trusted-entry"))
                .andExpect(status().isForbidden());
            mvc.perform(get("/access/service-entry").header("Authorization", "Bearer service"))
                .andExpect(status().isOk());
            mvc.perform(get("/access/service-entry").header("Authorization", "Bearer merchant"))
                .andExpect(status().isForbidden());
            mvc.perform(get("/access/service-entry").header("Authorization", "Bearer service-other-entry"))
                .andExpect(status().isForbidden());
        });
    }

    @Test
    void public_branch_cannot_use_authenticated_subject_type_as_permission() {
        runner.run(context -> {
            MockMvc mvc = mvc(context.getSourceApplicationContext());
            mvc.perform(get("/access/public-human").header("Authorization", "Bearer merchant"))
                .andExpect(status().isForbidden());
            mvc.perform(get("/access/public-human")).andExpect(status().isForbidden());
        });
    }

    @Test
    void wrong_typed_identity_and_unknown_identity_should_deny_before_controller() {
        runner.run(context -> {
            MockMvc mvc = mvc(context.getSourceApplicationContext());
            for (String token : new String[]{"teacher", "unknown", "unassigned"}) {
                mvc.perform(post("/access/typed").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
            }
            assertThat(context.getBean(Calls.class).controllerCalls.get()).isZero();
        });
    }

    @Test
    void public_context_should_not_merge_authenticated_identity_or_require_typed_identity() {
        runner.run(context -> {
            MockMvc mvc = mvc(context.getSourceApplicationContext());
            mvc.perform(get("/access/anonymous").header("Authorization", "Bearer merchant"))
                .andExpect(status().isOk()).andExpect(content().string("public"));
            mvc.perform(get("/access/anonymous-typed").header("Authorization", "Bearer merchant"))
                .andExpect(status().isForbidden());
        });
    }

    @Test
    void optional_context_should_not_retain_previous_request_identity() {
        runner.run(context -> {
            MockMvc mvc = mvc(context.getSourceApplicationContext());
            mvc.perform(get("/access/optional").header("Authorization", "Bearer merchant"))
                .andExpect(status().isOk()).andExpect(content().string("merchant"));
            mvc.perform(get("/access/optional")).andExpect(status().isOk()).andExpect(content().string("public"));
            assertThat(context.getBean(Calls.class).authenticationCalls.get()).isEqualTo(2);
            assertThat(AccessWebRequestContext.find(context.getBean(Calls.class).request.get())).isNull();
        });
    }

    @Test
    void declared_interface_should_expose_scope_only_during_synchronous_dispatch() {
        runner.run(context -> {
            MockMvc mvc = mvc(context.getSourceApplicationContext());
            mvc.perform(get("/access/scope").header("Authorization", "Bearer merchant"))
                .andExpect(status().isOk()).andExpect(content().string("merchant"));
            assertThat(AccessContextScope.currentVisitor()).isEmpty();
            mvc.perform(get("/access/legacy-scope").header("Authorization", "Bearer merchant"))
                .andExpect(status().isOk()).andExpect(content().string("unscoped"));
            mvc.perform(get("/access/scope")).andExpect(status().isForbidden());
            assertThat(AccessContextScope.currentVisitor()).isEmpty();
        });
    }

    @Test
    void async_worker_should_not_inherit_scope_and_initial_dispatch_should_clear_it() {
        runner.run(context -> {
            MockMvc mvc = mvc(context.getSourceApplicationContext());
            var initial = mvc.perform(get("/access/async-scope").header("Authorization", "Bearer merchant"))
                .andExpect(status().isOk()).andReturn();
            assertThat(initial.getRequest().isAsyncStarted()).isTrue();
            assertThat(AccessContextScope.currentVisitor()).isEmpty();
            mvc.perform(asyncDispatch(initial)).andExpect(status().isOk())
                .andExpect(content().string("worker-unscoped"));
            assertThat(AccessContextScope.currentVisitor()).isEmpty();
        });
    }

    @Test
    void concurrent_requests_should_keep_identity_separate_and_clear_each_request_scope() {
        runner.run(context -> {
            MockMvc mvc = mvc(context.getSourceApplicationContext());
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                List<Future<?>> tasks = new ArrayList<>();
                for (String identity : new String[]{"merchant", "teacher"}) {
                    tasks.add(executor.submit(() -> {
                        for (int requestIndex = 0; requestIndex < 10; requestIndex++) {
                            var result = mvc.perform(get("/access/optional").header("Authorization", "Bearer " + identity))
                                .andExpect(status().isOk()).andExpect(content().string(identity)).andReturn();
                            assertThat(AccessWebRequestContext.find(result.getRequest())).isNull();
                        }
                        return null;
                    }));
                }
                for (Future<?> task : tasks) {
                    task.get(10, TimeUnit.SECONDS);
                }
                assertThat(context.getBean(Calls.class).authenticationCalls.get()).isEqualTo(20);
            } finally {
                executor.shutdownNow();
            }
        });
    }

    @Test
    void context_parameters_should_reject_raw_wildcard_and_body_binding() {
        runner.run(context -> {
            MockMvc mvc = mvc(context.getSourceApplicationContext());
            for (String path : new String[]{"/access/raw", "/access/wildcard", "/access/implicit-identity"}) {
                mvc.perform(get(path).header("Authorization", "Bearer merchant")).andExpect(status().isBadRequest());
            }
            mvc.perform(post("/access/body-context").header("Authorization", "Bearer merchant")
                .contentType(MediaType.APPLICATION_JSON).content("{\"subjectId\":\"forged\"}"))
                .andExpect(status().isBadRequest());
            assertThat(context.getBean(Calls.class).controllerCalls.get()).isZero();
        });
    }

    @Test
    void context_parameter_without_declaration_should_not_create_implicit_admission() {
        runner.run(context -> mvc(context.getSourceApplicationContext())
            .perform(get("/access/undeclared").header("Authorization", "Bearer merchant"))
            .andExpect(status().isForbidden()));
    }

    @Test
    void class_declaration_should_apply_and_method_declaration_should_override_explicitly() {
        runner.run(context -> {
            MockMvc mvc = mvc(context.getSourceApplicationContext());
            mvc.perform(get("/access/class-default")).andExpect(status().isForbidden());
            mvc.perform(get("/access/class-default").header("Authorization", "Bearer teacher")).andExpect(status().isForbidden());
            mvc.perform(get("/access/class-default").header("Authorization", "Bearer merchant")).andExpect(status().isOk());
            mvc.perform(get("/access/class-default").header("Authorization", "Bearer merchant-other-entry"))
                .andExpect(status().isForbidden());
            mvc.perform(get("/access/class-public")).andExpect(status().isOk()).andExpect(content().string("public"));
        });
    }

    @Test
    void missing_http_source_should_deny_declared_interfaces_and_leave_legacy_routes_unchanged() {
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CrudAccessWebAutoConfiguration.class))
            .withUserConfiguration(MvcConfiguration.class)
            .run(context -> {
                assertThat(context).doesNotHaveBean("crudAccessAuthenticationFilter");
                MockMvc mvc = mvc(context.getSourceApplicationContext());
                mvc.perform(get("/access/required")).andExpect(status().isForbidden());
                mvc.perform(get("/access/anonymous")).andExpect(status().isForbidden());
                mvc.perform(get("/access/legacy")).andExpect(status().isOk());
            });
    }

    @Test
    void source_failure_should_not_leak_sensitive_message_or_fall_back_to_public() {
        runner.run(context -> {
            mvc(context.getSourceApplicationContext()).perform(get("/access/anonymous").header("Authorization", "Bearer failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("可信认证来源处理失败"));
            assertThat(context.getBean(Calls.class).controllerCalls.get()).isZero();
            assertThat(AccessWebRequestContext.find(context.getBean(Calls.class).request.get())).isNull();
        });
    }

    @Test
    void null_source_result_should_fail_closed() {
        runner.run(context -> {
            mvc(context.getSourceApplicationContext()).perform(get("/access/anonymous").header("Authorization", "Bearer null"))
                .andExpect(status().isForbidden());
            assertThat(context.getBean(Calls.class).controllerCalls.get()).isZero();
        });
    }

    @Test
    void source_permission_failure_should_use_generic_response_without_credential_details() {
        runner.run(context -> mvc(context.getSourceApplicationContext())
            .perform(get("/access/anonymous").header("Authorization", "Bearer denied"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("凭证认证失败")));
    }

    @Test
    void filter_should_clear_context_even_when_controller_throws() {
        runner.run(context -> {
            assertThatThrownBy(() -> mvc(context.getSourceApplicationContext())
                .perform(get("/access/crash").header("Authorization", "Bearer merchant"))).hasRootCauseMessage("业务失败");
            assertThat(AccessWebRequestContext.find(context.getBean(Calls.class).request.get())).isNull();
            assertThat(AccessContextScope.currentVisitor()).isEmpty();
        });
    }

    @Test
    void adapter_opt_in_should_reject_invalid_credentials_on_legacy_routes_too() {
        runner.run(context -> mvc(context.getSourceApplicationContext())
            .perform(get("/access/legacy").header("Authorization", "Bearer expired")).andExpect(status().isForbidden()));
    }

    @Test
    void non_web_context_should_not_load_mvc_admission() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CrudAccessWebAutoConfiguration.class))
            .run(context -> assertThat(context).doesNotHaveBean(AccessAdmissionInterceptor.class));
    }

    @Test
    void nested_forward_should_admit_target_and_restore_outer_scope_even_when_target_fails() {
        runner.run(context -> {
            var request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer merchant");
            var response = new MockHttpServletResponse();
            var filter = context.getBean("crudAccessAuthenticationFilter", FilterRegistrationBean.class).getFilter();
            var interceptor = context.getBean(AccessAdmissionInterceptor.class);
            var controller = context.getBean(AccessController.class);
            var outer = new HandlerMethod(controller, AccessController.class.getDeclaredMethod("scope", VisitorContext.class));
            var inner = new HandlerMethod(controller, AccessController.class.getDeclaredMethod("anonymous", VisitorContext.class));
            filter.doFilter(request, response, (req, res) -> {
                try {
                    assertThat(interceptor.preHandle(request, response, outer)).isTrue();
                    VisitorContext outerVisitor = AccessContextScope.requireVisitor();
                    request.setDispatcherType(DispatcherType.FORWARD);
                    filter.doFilter(request, response, (forwardReq, forwardRes) -> {
                        try {
                            assertThat(interceptor.preHandle(request, response, inner)).isTrue();
                            VisitorContext innerVisitor = AccessContextScope.requireVisitor();
                            assertThat(innerVisitor.isAnonymous()).isTrue();
                            assertThat(AccessWebRequestContext.find(request).admission(inner.getMethod()).getVisitorContext())
                                .isSameAs(innerVisitor);
                            assertThat(AccessWebRequestContext.find(request).admission(outer.getMethod())).isNull();
                            try {
                                throw new IllegalStateException("内层业务失败");
                            } catch (IllegalStateException failure) {
                                interceptor.afterCompletion(request, response, inner, failure);
                            }
                        } catch (Exception failure) { throw new jakarta.servlet.ServletException(failure); }
                    });
                    assertThat(AccessContextScope.requireVisitor()).isSameAs(outerVisitor);
                    assertThat(AccessWebRequestContext.find(request).admission(outer.getMethod()).getVisitorContext())
                        .isSameAs(outerVisitor);
                    interceptor.afterCompletion(request, response, outer, null);
                } catch (Exception failure) { throw new jakarta.servlet.ServletException(failure); }
            });
            assertThat(AccessContextScope.currentVisitor()).isEmpty();
            assertThat(AccessWebRequestContext.find(request)).isNull();
            assertThat(context.getBean(Calls.class).authenticationCalls.get()).isEqualTo(1);
        });
    }

    @Test
    void undeclared_forward_should_mask_outer_identity_and_denied_target_should_leave_outer_scope_intact() {
        runner.run(context -> {
            var request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer merchant");
            var response = new MockHttpServletResponse();
            var filter = context.getBean("crudAccessAuthenticationFilter", FilterRegistrationBean.class).getFilter();
            var interceptor = context.getBean(AccessAdmissionInterceptor.class);
            var controller = context.getBean(AccessController.class);
            var outer = new HandlerMethod(controller, AccessController.class.getDeclaredMethod("scope", VisitorContext.class));
            var legacy = new HandlerMethod(controller, AccessController.class.getDeclaredMethod("legacyScope"));
            var denied = new HandlerMethod(controller, AccessController.class.getDeclaredMethod("serviceEntry"));
            filter.doFilter(request, response, (req, res) -> {
                try {
                    assertThat(interceptor.preHandle(request, response, outer)).isTrue();
                    VisitorContext outerVisitor = AccessContextScope.requireVisitor();
                    assertThat(interceptor.preHandle(request, response, legacy)).isTrue();
                    assertThat(AccessContextScope.currentVisitor()).isEmpty();
                    assertThat(AccessWebRequestContext.find(request).admission(outer.getMethod())).isNull();
                    interceptor.afterCompletion(request, response, legacy, null);
                    assertThat(AccessContextScope.requireVisitor()).isSameAs(outerVisitor);
                    assertThat(interceptor.preHandle(request, response, denied)).isFalse();
                    assertThat(response.getStatus()).isEqualTo(403);
                    assertThat(AccessContextScope.requireVisitor()).isSameAs(outerVisitor);
                    interceptor.afterCompletion(request, response, outer, null);
                } catch (Exception failure) { throw new jakarta.servlet.ServletException(failure); }
            });
            assertThat(AccessContextScope.currentVisitor()).isEmpty();
        });
    }

    @Test
    void declared_callable_should_not_fall_back_to_legacy_for_unbound_resources() {
        QueryRouter router = mock(QueryRouter.class);
        CrudGovernanceService legacy = mock(CrudGovernanceService.class);
        QueryGateway gateway = new QueryGatewayImpl(router, new ExecutionPipeline(legacy));
        runner.withBean(AsyncGatewayController.class, () -> new AsyncGatewayController(gateway)).run(context -> {
            MockMvc mvc = mvc(context.getSourceApplicationContext());
            var initial = mvc.perform(get("/access/async-gateway").header("Authorization", "Bearer merchant")).andReturn();
            assertThat(initial.getAsyncResult(5000)).isInstanceOf(PermissionDeniedException.class);
            verifyNoInteractions(router, legacy);
            assertThat(AccessContextScope.currentVisitor()).isEmpty();
            assertThat(AccessContextScope.isAuthorizationRequired()).isFalse();
        });
    }

    @RestController
    static class AsyncGatewayController {
        private final QueryGateway gateway;
        AsyncGatewayController(QueryGateway gateway) { this.gateway = gateway; }

        @EntAccess
        @GetMapping("/access/async-gateway")
        Callable<List<Object>> query() {
            return () -> gateway.list(QuerySpec.builder().rootType(Object.class).resultType(Object.class).build());
        }
    }

    private static MockMvc mvc(org.springframework.web.context.WebApplicationContext context) {
        var builder = MockMvcBuilders.webAppContextSetup(context);
        if (context.containsBean("crudAccessAuthenticationFilter")) {
            FilterRegistrationBean<?> registration = context.getBean("crudAccessAuthenticationFilter", FilterRegistrationBean.class);
            builder.addFilters(registration.getFilter());
        }
        return builder.build();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class MvcConfiguration {
        @Bean
        Calls calls() {
            return new Calls();
        }

        @Bean
        AccessController accessController(Calls calls) {
            return new AccessController(calls);
        }

        @Bean
        ClassAccessController classAccessController() {
            return new ClassAccessController();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AuthenticationConfiguration {
        @Bean
        BusinessIdentityRegistration<MerchantIdentity> merchantIdentity() {
            return new BusinessIdentityRegistration<>("merchant", MerchantIdentity.class, (subject, identityId) ->
                "actor-1".equals(subject.getSubjectId()) && "merchant-1".equals(identityId) ? new MerchantIdentity() : null);
        }

        @Bean
        BusinessIdentityRegistration<TeacherIdentity> teacherIdentity() {
            return new BusinessIdentityRegistration<>("teacher", TeacherIdentity.class, (subject, identityId) ->
                "actor-1".equals(subject.getSubjectId()) && "teacher-1".equals(identityId) ? new TeacherIdentity() : null);
        }

        @Bean
        CrudHttpAuthenticationSource authenticationSource(Calls calls) {
            return request -> {
                calls.authenticationCalls.incrementAndGet();
                calls.request.set(request);
                String credential = request.getHeader("Authorization");
                CredentialAuthentication authentication;
                AccessSubject subject = new AccessSubject("actor-1", AccessSubjectType.HUMAN);
                if (credential == null) {
                    authentication = CredentialAuthentication.absent();
                } else if ("Bearer merchant".equals(credential)) {
                    authentication = CredentialAuthentication.authenticated(subject, "merchant", "merchant-1");
                } else if ("Bearer teacher".equals(credential)) {
                    authentication = CredentialAuthentication.authenticated(subject, "teacher", "teacher-1");
                } else if ("Bearer merchant-other-entry".equals(credential)) {
                    authentication = CredentialAuthentication.authenticated(subject, "merchant", "merchant-1");
                } else if ("Bearer service".equals(credential) || "Bearer service-other-entry".equals(credential)) {
                    authentication = CredentialAuthentication.authenticated(new AccessSubject("service-1", AccessSubjectType.SERVICE));
                } else if ("Bearer unknown".equals(credential)) {
                    authentication = CredentialAuthentication.authenticated(subject, "unknown", "identity-1");
                } else if ("Bearer unassigned".equals(credential)) {
                    authentication = CredentialAuthentication.authenticated(subject);
                } else if ("Bearer failure".equals(credential)) {
                    throw new IllegalStateException("不得泄漏 secret-token");
                } else if ("Bearer denied".equals(credential)) {
                    throw new PermissionDeniedException("不得泄漏 secret-token");
                } else if ("Bearer null".equals(credential)) {
                    return null;
                } else {
                    authentication = CredentialAuthentication.invalid();
                }
                String entry = credential != null && credential.endsWith("other-entry") ? "other-entry"
                    : credential != null && credential.startsWith("Bearer service") ? "internal-entry" : "trusted-entry";
                return new HttpAccessAuthentication(authentication, entry, "request-1", "trace-1");
            };
        }
    }

    static class Calls {
        final AtomicInteger authenticationCalls = new AtomicInteger();
        final AtomicInteger controllerCalls = new AtomicInteger();
        final AtomicReference<HttpServletRequest> request = new AtomicReference<>();
    }

    @RestController
    static class AccessController {
        private final Calls calls;

        AccessController(Calls calls) {
            this.calls = calls;
        }

        @EntAccess
        @GetMapping("/access/required")
        String required() {
            calls.controllerCalls.incrementAndGet();
            return "authenticated";
        }

        @EntAccess(AuthenticationMode.OPTIONAL)
        @GetMapping("/access/optional")
        String optional(VisitorContext visitor) {
            calls.controllerCalls.incrementAndGet();
            return visitor.getIdentity().map(BusinessIdentity::getIdentityCode).orElse("public");
        }

        @EntAccess(AuthenticationMode.ANONYMOUS)
        @GetMapping("/access/anonymous")
        String anonymous(VisitorContext visitor) {
            calls.controllerCalls.incrementAndGet();
            return visitor.isAnonymous() && !visitor.getIdentity().isPresent() ? "public" : "incorrect";
        }

        @EntAccess(identity = MerchantIdentity.class)
        @PostMapping("/access/typed")
        String typed(AccessContext<MerchantIdentity> access, @RequestBody Map<String, Object> payload) {
            calls.controllerCalls.incrementAndGet();
            VisitorContext visitor = access.getVisitorContext();
            return access.getSubject().getSubjectId() + "|" + access.getIdentity().getIdentityId() + "|"
                + access.getIdentity().getIdentityCode() + "|" + visitor.getAccessEntry() + "|" + visitor.getRequestId() + "|" + visitor.getTraceId();
        }

        @EntAccess(identity = MerchantIdentity.class)
        @GetMapping("/access/merchant-only")
        String merchantOnly() {
            calls.controllerCalls.incrementAndGet();
            return "merchant";
        }

        @EntAccess(identity = MerchantIdentity.class, accessEntries = "trusted-entry", subjectTypes = AccessSubjectType.HUMAN)
        @GetMapping("/access/merchant-entry")
        String merchantEntry() {
            calls.controllerCalls.incrementAndGet();
            return "merchant";
        }

        @EntAccess(accessEntries = "internal-entry", subjectTypes = AccessSubjectType.SERVICE)
        @GetMapping("/access/service-entry")
        String serviceEntry() {
            calls.controllerCalls.incrementAndGet();
            return "service";
        }

        @EntAccess(value = AuthenticationMode.ANONYMOUS, subjectTypes = AccessSubjectType.HUMAN)
        @GetMapping("/access/public-human")
        String publicHuman() {
            calls.controllerCalls.incrementAndGet();
            return "incorrect";
        }

        @EntAccess(value = AuthenticationMode.ANONYMOUS, identity = MerchantIdentity.class)
        @GetMapping("/access/anonymous-typed")
        String anonymousTyped(AccessContext<MerchantIdentity> access) {
            return "incorrect";
        }

        @EntAccess
        @GetMapping("/access/raw")
        @SuppressWarnings("rawtypes")
        String raw(AccessContext access) {
            calls.controllerCalls.incrementAndGet();
            return "incorrect";
        }

        @EntAccess
        @GetMapping("/access/wildcard")
        String wildcard(AccessContext<? extends BusinessIdentity> access) {
            calls.controllerCalls.incrementAndGet();
            return "incorrect";
        }

        @EntAccess
        @GetMapping("/access/implicit-identity")
        String implicitIdentity(AccessContext<MerchantIdentity> access) {
            calls.controllerCalls.incrementAndGet();
            return "incorrect";
        }

        @EntAccess
        @PostMapping("/access/body-context")
        String bodyContext(@RequestBody VisitorContext visitor) {
            calls.controllerCalls.incrementAndGet();
            return "incorrect";
        }

        @GetMapping("/access/undeclared")
        String undeclared(VisitorContext visitor) {
            return "incorrect";
        }

        @GetMapping("/access/legacy")
        String legacy() {
            return "legacy";
        }

        @EntAccess
        @GetMapping("/access/scope")
        String scope(VisitorContext visitor) {
            return AccessContextScope.requireVisitor() == visitor
                ? visitor.getIdentity().map(BusinessIdentity::getIdentityCode).orElse("public") : "incorrect";
        }

        @GetMapping("/access/legacy-scope")
        String legacyScope() {
            return AccessContextScope.currentVisitor().isPresent() ? "incorrect" : "unscoped";
        }

        @EntAccess
        @GetMapping("/access/async-scope")
        Callable<String> asyncScope(VisitorContext visitor) {
            if (AccessContextScope.requireVisitor() != visitor) throw new IllegalStateException("同步访问者不一致");
            return () -> AccessContextScope.currentVisitor().isPresent() ? "incorrect" : "worker-unscoped";
        }

        @EntAccess
        @GetMapping("/access/crash")
        String crash() {
            AccessContextScope.requireVisitor();
            throw new IllegalStateException("业务失败");
        }
    }

    @RestController
    @EntAccess(identity = MerchantIdentity.class, accessEntries = "trusted-entry")
    static class ClassAccessController {
        @GetMapping("/access/class-default")
        String classDefault() {
            return "merchant";
        }

        @EntAccess(AuthenticationMode.ANONYMOUS)
        @GetMapping("/access/class-public")
        String classPublic(VisitorContext visitor) {
            return visitor.isAnonymous() ? "public" : "incorrect";
        }
    }

    static final class MerchantIdentity implements BusinessIdentity {
        @Override
        public String getIdentityCode() {
            return "merchant";
        }

        @Override
        public String getIdentityId() {
            return "merchant-1";
        }
    }

    static final class TeacherIdentity implements BusinessIdentity {
        @Override
        public String getIdentityCode() {
            return "teacher";
        }

        @Override
        public String getIdentityId() {
            return "teacher-1";
        }
    }
}
