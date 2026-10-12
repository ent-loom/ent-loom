package com.entloom.crud.starter.config;

import com.entloom.crud.annotations.EntAccess;
import com.entloom.crud.api.access.AccessContext;
import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.BusinessIdentityDefinition;
import com.entloom.crud.api.access.BusinessIdentityIdType;
import com.entloom.crud.api.access.ConfiguredBusinessIdentity;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.admission.CredentialAuthentication;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistration;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.starter.web.access.CrudHttpAuthenticationSource;
import com.entloom.crud.starter.web.access.HttpAccessAuthentication;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConfiguredExampleAccountsTest {
    private static final BusinessIdentityDefinition CUSTOMER = new BusinessIdentityDefinition("customer", "customerId", BusinessIdentityIdType.LONG);
    private static final String PREFIX = "ent.loom.crud.example.";
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CrudExampleAutoConfiguration.class,
            CrudPolicyAutoConfiguration.class, CrudAccessWebAutoConfiguration.class))
        .withUserConfiguration(MvcConfiguration.class)
        .withPropertyValues("ent.loom.crud.access.identities.customer.id-type=LONG",
            "ent.loom.crud.access.identities.merchant.id-type=LONG", PREFIX + "enabled=true", PREFIX + "default-access-entry=consumer",
            PREFIX + "routes[/api/merchant/**]=merchant",
            PREFIX + "accounts[0].token=customer-a", PREFIX + "accounts[0].subject-id=user-a",
            PREFIX + "accounts[0].customerId=301",
            PREFIX + "accounts[1].token=merchant-a", PREFIX + "accounts[1].subject-id=user-b",
            PREFIX + "accounts[1].merchantId=1001");

    @Test
    void 按Yaml身份码注册同类快照并在创建Controller前完成装配() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            BusinessIdentityRegistry registry = context.getBean(BusinessIdentityRegistry.class);
            assertThat(registry.snapshot()).containsOnlyKeys("customer", "merchant");
            AccessSubject customer = new AccessSubject("user-a", AccessSubjectType.HUMAN);
            assertThat(registry.resolve(customer, "customer", "301")).isEqualTo(CUSTOMER.snapshot("301"));
            assertThatThrownBy(() -> registry.resolve(customer, "customer", "302"))
                .isInstanceOf(PermissionDeniedException.class);
            assertThatThrownBy(() -> registry.resolve(customer, "merchant", "1001"))
                .isInstanceOf(PermissionDeniedException.class);
            assertThatThrownBy(() -> registry.resolve(new AccessSubject("user-a", AccessSubjectType.SERVICE), "customer", "301"))
                .isInstanceOf(PermissionDeniedException.class);
        });
    }

    @Test
    void 静态认证接入真实MVC且不能伪造身份或入口() {
        runner.run(context -> {
            FilterRegistrationBean<?> filter = context.getBean("crudAccessAuthenticationFilter", FilterRegistrationBean.class);
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext())
                .addFilters(filter.getFilter()).build();
            mvc.perform(get("/api/consumer/current").header("Authorization", "Bearer customer-a")
                    .header("X-Customer-Id", "302").header("X-Access-Entry", "merchant").param("customerId", "302"))
                .andExpect(status().isOk()).andExpect(content().string("301|consumer"));
            mvc.perform(get("/api/merchant/current").header("Authorization", "Bearer merchant-a"))
                .andExpect(status().isOk()).andExpect(content().string("1001|merchant"));
            mvc.perform(get("/api/merchant/current").header("Authorization", "Bearer customer-a"))
                .andExpect(status().isForbidden());
            mvc.perform(get("/api/consumer/current").header("Authorization", "Bearer merchant-a"))
                .andExpect(status().isForbidden());
            mvc.perform(get("/api/consumer/current")).andExpect(status().isForbidden());
            mvc.perform(get("/api/consumer/current").header("Authorization", "Bearer unknown"))
                .andExpect(status().isForbidden());
        });
    }

    @Test
    void 认证状态与ContextPath不依赖客户端入口() {
        runner.run(context -> {
            CrudHttpAuthenticationSource source = context.getBean(CrudHttpAuthenticationSource.class);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/shop/api/merchant/current");
            request.setContextPath("/shop");
            assertThat(source.authenticate(request).getAuthentication().getStatus()).isEqualTo(CredentialAuthentication.Status.ABSENT);
            request.addHeader("Authorization", "Bearer merchant-a");
            HttpAccessAuthentication authentication = source.authenticate(request);
            assertThat(authentication.getAccessEntry()).isEqualTo("merchant");
            assertThat(authentication.getRequestId()).isNotBlank();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts[0].customerId=bad", "accounts[0].customerId=0", "accounts[0].customerId=-1",
        "accounts[0].customerId=0301", "accounts[0].token=", "accounts[0].subject-id=",
        "accounts[1].token=customer-a", "accounts[1].subject-id=user-a",
        "accounts[0].merchantId=1001", "accounts[0].unknownId=1"})
    void 非法账号启动失败(String invalidProperty) {
        runner.withPropertyValues(PREFIX + invalidProperty).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 兼容账号字段的连字符命名() {
        runner.withPropertyValues(PREFIX + "accounts[2].token=customer-c", PREFIX + "accounts[2].subject-id=user-c",
            PREFIX + "accounts[2].customer-id=303").run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(BusinessIdentityRegistry.class).resolve(
                    new AccessSubject("user-c", AccessSubjectType.HUMAN), "customer", "303"))
                    .isEqualTo(CUSTOMER.snapshot("303"));
            });
    }

    @Test
    void 重复身份字段未知字段或缺失类型启动失败() {
        runner.withPropertyValues("ent.loom.crud.access.identities.merchant.id-field=customerId")
            .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(PREFIX + "accounts[2].token=wrong", PREFIX + "accounts[2].subject-id=user-c",
            PREFIX + "accounts[2].unknownId=1").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("ent.loom.crud.access.identities.merchant.id-type=")
            .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("ent.loom.crud.access.identities.merchant.id-field=token")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 显式解析器优先并在准入时重新核验归属() {
        AtomicBoolean active = new AtomicBoolean(true);
        runner.withBean("customCustomer", BusinessIdentityRegistration.class, () ->
            new BusinessIdentityRegistration<>("customer", ConfiguredBusinessIdentity.class, (subject, id) ->
                active.get() ? CUSTOMER.snapshot(id) : null))
            .run(context -> {
                assertThat(context).hasNotFailed();
                BusinessIdentityRegistry registry = context.getBean(BusinessIdentityRegistry.class);
                AccessSubject subject = new AccessSubject("user-a", AccessSubjectType.HUMAN);
                assertThat(registry.resolve(subject, "customer", "301")).isEqualTo(CUSTOMER.snapshot("301"));
                active.set(false);
                assertThatThrownBy(() -> registry.resolve(subject, "customer", "301"))
                    .isInstanceOf(PermissionDeniedException.class);
            });
    }

    @Test
    void 自定义解析器返回不同结构或坐标时拒绝() {
        runner.withBean("customCustomer", BusinessIdentityRegistration.class, () ->
            new BusinessIdentityRegistration<>("customer", ConfiguredBusinessIdentity.class, (subject, id) ->
                new BusinessIdentityDefinition("customer", "wrongId", BusinessIdentityIdType.LONG).snapshot(id)))
            .run(context -> assertThatThrownBy(() -> context.getBean(BusinessIdentityRegistry.class).resolve(
                new AccessSubject("user-a", AccessSubjectType.HUMAN), "customer", "301"))
                .isInstanceOf(PermissionDeniedException.class));
    }

    @Test
    void 入口引用未知身份码时启动失败且方法声明可以覆盖类声明() {
        runner.withBean(UnknownController.class, UnknownController::new).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("引用未注册");
        });
        runner.withBean(OverriddenController.class, OverriddenController::new).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void 自定义认证来源优先且演示关闭时不自动注册() {
        CrudHttpAuthenticationSource custom = request -> new HttpAccessAuthentication(CredentialAuthentication.absent(), "custom", null, null);
        runner.withBean(CrudHttpAuthenticationSource.class, () -> custom).run(context ->
            assertThat(context.getBean(CrudHttpAuthenticationSource.class)).isSameAs(custom));
        runner.withPropertyValues(PREFIX + "enabled=false").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("缺少可信归属解析器");
        });
        new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(CrudExampleAutoConfiguration.class,
            CrudAccessWebAutoConfiguration.class)).withPropertyValues(PREFIX + "enabled=false", PREFIX + "accounts[0].token=ignored")
            .run(context -> {
                assertThat(context).hasNotFailed().doesNotHaveBean(ConfiguredExampleAccounts.class).doesNotHaveBean(CrudHttpAuthenticationSource.class);
                assertThat(context.getBean(BusinessIdentityRegistry.class).snapshot()).isEmpty();
            });
    }

    @Test
    void 没有静态账号时保持既有演示主体装配() {
        new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(CrudExampleAutoConfiguration.class))
            .withPropertyValues(PREFIX + "enabled=true").run(context -> {
                assertThat(context).hasNotFailed().doesNotHaveBean(CrudHttpAuthenticationSource.class);
                assertThat(context.getBean(BusinessIdentityRegistry.class).snapshot()).isEmpty();
            });
    }

    @Test
    void 路由匹配到不同入口时拒绝() {
        runner.withPropertyValues(PREFIX + "routes[/api/**]=consumer").run(context ->
            assertThatThrownBy(() -> context.getBean(CrudHttpAuthenticationSource.class)
                .authenticate(new MockHttpServletRequest("GET", "/api/merchant/current")))
                .isInstanceOf(PermissionDeniedException.class));
    }

    @Test
    void 文本令牌false仍装配认证且非Web应用不加载Servlet账号支持() {
        runner.withPropertyValues(PREFIX + "accounts[0].token=false").run(context -> {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer false");
            assertThat(context.getBean(CrudHttpAuthenticationSource.class).authenticate(request).getAuthentication().getStatus())
                .isEqualTo(CredentialAuthentication.Status.AUTHENTICATED);
        });
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CrudExampleAutoConfiguration.class))
            .withClassLoader(new FilteredClassLoader("jakarta.servlet"))
            .withPropertyValues(PREFIX + "enabled=true").run(context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(ConfiguredExampleAccounts.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class MvcConfiguration {
        @Bean CustomerController customerController(BusinessIdentityRegistry registry) { return new CustomerController(registry); }
        @Bean MerchantController merchantController() { return new MerchantController(); }
    }

    @RestController
    @EntAccess(identity = "customer", accessEntries = "consumer")
    static class CustomerController {
        CustomerController(BusinessIdentityRegistry registry) {}
        @GetMapping("/api/consumer/current")
        String current(AccessContext access) {
            return access.getIdentity().getIdentityId() + "|" + access.getVisitorContext().getAccessEntry();
        }
    }

    @RestController
    static class MerchantController {
        @GetMapping("/api/merchant/current")
        @EntAccess(identity = "merchant", accessEntries = "merchant")
        String current(AccessContext access) {
            return access.getIdentity().getIdentityId() + "|" + access.getVisitorContext().getAccessEntry();
        }
    }

    @RestController
    @EntAccess(identity = "unknown")
    static class UnknownController {
        @GetMapping("/api/unknown/current") String current() { return "unknown"; }
    }

    @RestController
    @EntAccess(identity = "unused")
    static class OverriddenController {
        @GetMapping("/api/override/current")
        @EntAccess(identity = "customer")
        String current() { return "customer"; }
    }

}
