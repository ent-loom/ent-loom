package com.entloom.crud.starter.config;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.EntityDaoScopeResolver;
import com.entloom.crud.core.governance.scope.AllowAllCrudDataScopeResolver;
import com.entloom.crud.core.governance.scope.CrudDataScopeResolver;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.crud.starter.web.access.CrudHttpAuthenticationSource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** 显式开启的演示治理装配；不授予操作权限，不开放 HTTP 或文档。 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore({CrudAutoConfiguration.class, CrudPolicyAutoConfiguration.class, CrudAccessWebAutoConfiguration.class})
@ConditionalOnProperty(prefix = "ent.loom.crud.example", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CrudExampleProperties.class)
@Import(CrudIdentityConfiguration.class)
public class CrudExampleAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(CrudSubjectResolver.class)
    public CrudSubjectResolver exampleCrudSubjectResolver(CrudExampleProperties properties) {
        String subjectId = properties.getSubjectId();
        if (subjectId == null || subjectId.trim().isEmpty()) {
            throw new IllegalArgumentException("ent.loom.crud.example.subject-id 不能为空");
        }
        String resolvedSubjectId = subjectId.trim();
        // 主体可变，每次解析创建独立实例，避免请求之间共享状态。
        return () -> {
            SubjectContext subject = new SubjectContext();
            subject.setSubjectId(resolvedSubjectId);
            return subject;
        };
    }

    @Bean
    @ConditionalOnMissingBean(CrudDataScopeResolver.class)
    @ConditionalOnProperty(prefix = "ent.loom.crud.example", name = "allow-all-data-scope",
        havingValue = "true", matchIfMissing = true)
    public CrudDataScopeResolver exampleCrudDataScopeResolver() {
        return new AllowAllCrudDataScopeResolver();
    }

    @Bean
    @ConditionalOnMissingBean(EntityDaoScopeResolver.class)
    @ConditionalOnProperty(prefix = "ent.loom.crud.example", name = "allow-all-data-scope",
        havingValue = "true", matchIfMissing = true)
    public EntityDaoScopeResolver exampleEntityDaoScopeResolver() {
        return entityType -> EntityAccessScope.unrestricted();
    }

    /** 静态账号仅进入 Servlet 应用，非 Web 演示继续只装配主体与范围。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(HttpServletRequest.class)
    static class WebAccountsConfiguration {
        @Bean
        ConfiguredExampleAccounts configuredExampleAccounts(CrudExampleProperties properties,
            ConfiguredBusinessIdentities identities) {
            return new ConfiguredExampleAccounts(properties, identities);
        }

        @Bean
        @Conditional(HasAccounts.class)
        @ConditionalOnMissingBean(CrudHttpAuthenticationSource.class)
        CrudHttpAuthenticationSource exampleHttpAuthenticationSource(ConfiguredExampleAccounts accounts) {
            return accounts::authenticate;
        }
    }

    static class HasAccounts extends SpringBootCondition {
        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            // token 是文本凭证，合法值 "false" 也必须启用认证来源。
            return context.getEnvironment().containsProperty("ent.loom.crud.example.accounts[0].token")
                ? ConditionOutcome.match("已配置静态账号") : ConditionOutcome.noMatch("未配置静态账号");
        }
    }
}
