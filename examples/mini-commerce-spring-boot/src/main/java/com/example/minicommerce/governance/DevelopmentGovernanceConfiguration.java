package com.example.minicommerce.governance;

import com.example.minicommerce.catalog.Customer;
import com.example.minicommerce.catalog.Product;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.scope.AllowAllCrudDataScopeResolver;
import com.entloom.crud.core.governance.scope.CrudDataScopeResolver;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.starter.web.registry.ExposedEntityRegistry;
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

    /** 只把商品和客户暴露给通用 CRUD，订单由业务接口专门负责。 */
    @Bean
    ExposedEntityRegistry developmentExposedEntityRegistry(EntityMetaRegistry entityMetaRegistry) {
        ExposedEntityRegistry registry = new ExposedEntityRegistry(entityMetaRegistry);
        registry.expose(Product.class);
        registry.expose(Customer.class);
        return registry;
    }
}
