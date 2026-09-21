package com.example.minicommerce.configuration;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.scope.AllowAllCrudDataScopeResolver;
import com.entloom.crud.core.governance.scope.CrudDataScopeResolver;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.meta.starter.doc.EntityDocumentationExposurePolicyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 仅用于示例的治理装配，生产项目应替换为真实身份和数据范围适配器。 */
@Configuration
@Profile("example")
public class ExampleGovernanceConfiguration {
    /** 提供固定的示例主体，便于本地观察权限链路。 */
    @Bean
    CrudSubjectResolver exampleSubjectResolver() {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("local-developer");
        return () -> subject;
    }

    /** 示例允许访问全部数据，生产项目应按租户或组织收敛范围。 */
    @Bean
    CrudDataScopeResolver exampleDataScopeResolver() {
        return new AllowAllCrudDataScopeResolver();
    }

    /** 示例只公开商品和客户的业务字段，订单仍由专用业务接口负责。 */
    @Bean
    EntityDocumentationExposurePolicyResolver exampleEntityDocumentationExposurePolicyResolver() {
        return MasterDataExposureConfiguration.documentationPolicy(
            subject -> subject != null && "local-developer".equals(subject.getSubjectId())
        );
    }
}
