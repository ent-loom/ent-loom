package com.example.minicommerce.configuration;

import com.example.minicommerce.customer.entity.Customer;
import com.example.minicommerce.product.entity.Product;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.scope.AllowAllCrudDataScopeResolver;
import com.entloom.crud.core.governance.scope.CrudDataScopeResolver;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.starter.web.registry.ExposedEntityRegistry;
import com.entloom.doc.core.contract.EntityDocumentationExposurePolicy;
import com.entloom.doc.core.model.DocEntityModel;
import com.entloom.doc.core.model.DocFieldModel;
import com.entloom.meta.starter.doc.EntityDocumentationExposurePolicyResolver;
import java.util.Map;
import java.util.Set;
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

    /** 开发态只公开商品和客户的业务字段，订单仍由专用业务接口负责。 */
    @Bean
    EntityDocumentationExposurePolicyResolver developmentEntityDocumentationExposurePolicyResolver() {
        return subject -> {
            boolean subjectAllowed = subject != null && "local-developer".equals(subject.getSubjectId());
            Set<Class<?>> exposedEntities = Set.of(Product.class, Customer.class);
            Map<Class<?>, Set<String>> exposedFields = Map.of(
                Product.class, Set.of("id", "name", "price", "active"),
                Customer.class, Set.of("id", "displayName", "email")
            );
            return new EntityDocumentationExposurePolicy() {
                @Override
                public boolean isEntityExposed(DocEntityModel entity) {
                    return subjectAllowed && entity != null && exposedEntities.contains(entity.entityClass());
                }

                @Override
                public boolean isFieldExposed(DocEntityModel entity, DocFieldModel field) {
                    return subjectAllowed && entity != null && exposedEntities.contains(entity.entityClass())
                        && field != null && exposedFields.getOrDefault(entity.entityClass(), Set.of())
                            .contains(field.property());
                }
            };
        };
    }
}
