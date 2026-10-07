package com.entloom.crud.starter.config;

import com.entloom.crud.core.governance.admission.AccessAdmissionService;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistration;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.starter.web.access.AccessAdmissionInterceptor;
import com.entloom.crud.starter.web.access.AccessAuthenticationFilter;
import com.entloom.crud.starter.web.access.AccessContextArgumentResolver;
import com.entloom.crud.starter.web.access.AccessHttpErrorWriter;
import com.entloom.crud.starter.web.access.CrudHttpAuthenticationSource;
import com.entloom.crud.starter.web.support.CrudResponseBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 独立 MVC 接入，不要求开启内置 CRUD Controller；未提供可信来源时声明接口拒绝。 */
@AutoConfiguration(after = {CrudAutoConfiguration.class, CrudPolicyAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({DispatcherServlet.class, HttpServletRequest.class})
public class CrudAccessWebAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public BusinessIdentityRegistry businessIdentityRegistry(ObjectProvider<BusinessIdentityRegistration<?>> registrations) {
        return new BusinessIdentityRegistry(registrations.orderedStream().collect(Collectors.toList()));
    }

    @Bean
    @ConditionalOnMissingBean
    public AccessAdmissionService accessAdmissionService(BusinessIdentityRegistry registry) {
        return new AccessAdmissionService(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public AccessHttpErrorWriter accessHttpErrorWriter(@Qualifier("crudObjectMapper") ObjectProvider<ObjectMapper> mapper,
        ObjectProvider<CrudResponseBuilder> responseBuilder) {
        return new AccessHttpErrorWriter(mapper.getIfAvailable(ObjectMapper::new), responseBuilder.getIfAvailable(CrudResponseBuilder::new));
    }

    @Bean
    @ConditionalOnMissingBean
    public AccessAdmissionInterceptor accessAdmissionInterceptor(AccessAdmissionService service, AccessHttpErrorWriter errorWriter) {
        return new AccessAdmissionInterceptor(service, errorWriter);
    }

    @Bean
    @ConditionalOnMissingBean
    public AccessContextArgumentResolver accessContextArgumentResolver() {
        return new AccessContextArgumentResolver();
    }

    @Bean
    public WebMvcConfigurer crudAccessWebMvcConfigurer(AccessAdmissionInterceptor interceptor, AccessContextArgumentResolver resolver) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
                registry.addInterceptor(interceptor);
            }

            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(resolver);
            }
        };
    }

    @Bean
    @ConditionalOnBean(CrudHttpAuthenticationSource.class)
    @ConditionalOnMissingBean(name = "crudAccessAuthenticationFilter")
    public FilterRegistrationBean<AccessAuthenticationFilter> crudAccessAuthenticationFilter(
        CrudHttpAuthenticationSource source, AccessHttpErrorWriter errorWriter) {
        FilterRegistrationBean<AccessAuthenticationFilter> registration = new FilterRegistrationBean<>(new AccessAuthenticationFilter(source, errorWriter));
        registration.setName("crudAccessAuthenticationFilter");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 100);
        registration.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
        registration.setAsyncSupported(true);
        return registration;
    }
}
