package com.example.minicommerce.configuration;

import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.meta.starter.doc.EntityDocumentationExposurePolicyResolver;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 生产认证适配示例：只消费 Servlet 容器已经确认的 Principal。
 *
 * <p>真实项目应由 Spring Security、网关或容器完成认证，并按自身组织和租户模型替换授权规则与数据范围。</p>
 */
@Configuration
@Profile("production")
public class ProductionGovernanceConfiguration {
    /** 将容器认证主体接入 ent-loom 主体解析链。 */
    @Bean
    CrudSubjectResolver productionSubjectResolver(ObjectProvider<HttpServletRequest> requestProvider) {
        return new ServletPrincipalCrudSubjectResolver(requestProvider);
    }

    /** 只有配置到文档读权限的认证主体可以查看主数据契约，默认拒绝全部主体。 */
    @Bean
    EntityDocumentationExposurePolicyResolver productionEntityDocumentationExposurePolicyResolver(
        @Value("${entloom.production.documentation-subject-ids:}") String configuredSubjectIds
    ) {
        Set<String> allowedSubjects = parseSubjectIds(configuredSubjectIds);
        return MasterDataExposureConfiguration.documentationPolicy(
            subject -> subject != null && allowedSubjects.contains(subject.getSubjectId())
        );
    }

    private Set<String> parseSubjectIds(String configuredSubjectIds) {
        if (configuredSubjectIds == null || configuredSubjectIds.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(configuredSubjectIds.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }
}
