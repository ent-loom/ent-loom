package com.entloom.crud.starter.config;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.scope.AllowAllCrudDataScopeResolver;
import com.entloom.crud.core.governance.scope.CrudDataScopeResolver;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 显式开启的演示治理装配；不授予操作权限，不开放 HTTP 或文档。 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(CrudAutoConfiguration.class)
@ConditionalOnProperty(prefix = "entloom.crud.example", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CrudExampleProperties.class)
public class CrudExampleAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(CrudSubjectResolver.class)
    public CrudSubjectResolver exampleCrudSubjectResolver(CrudExampleProperties properties) {
        String subjectId = properties.getSubjectId();
        if (subjectId == null || subjectId.trim().isEmpty()) {
            throw new IllegalArgumentException("entloom.crud.example.subject-id 不能为空");
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
    @ConditionalOnProperty(prefix = "entloom.crud.example", name = "allow-all-data-scope",
        havingValue = "true", matchIfMissing = true)
    public CrudDataScopeResolver exampleCrudDataScopeResolver() {
        return new AllowAllCrudDataScopeResolver();
    }
}
