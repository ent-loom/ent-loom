package com.example.minicommerce.configuration;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.scope.AllowAllCrudDataScopeResolver;
import com.entloom.crud.core.governance.scope.CrudDataScopeResolver;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.meta.starter.doc.EntityDocumentationExposurePolicyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 仅用于示例的开发态治理装配，生产项目应替换为真实身份和数据范围适配器。 */
@Configuration
@Profile("example")
public class DevelopmentGovernanceConfiguration {
    /** 提供固定的示例主体，便于本地观察权限链路。 */
    @Bean
    CrudSubjectResolver developmentSubjectResolver() {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("local-developer");
        return () -> subject;
    }

    /** 示例允许访问全部数据，生产项目应按租户或组织收敛范围。 */
    @Bean
    CrudDataScopeResolver developmentDataScopeResolver() {
        return new AllowAllCrudDataScopeResolver();
    }

    /** 开发态只公开商品和客户的业务字段，订单仍由专用业务接口负责。 */
    @Bean
    EntityDocumentationExposurePolicyResolver developmentEntityDocumentationExposurePolicyResolver() {
        return MasterDataExposureConfiguration.documentationPolicy(
            subject -> subject != null && "local-developer".equals(subject.getSubjectId())
        );
    }
}
