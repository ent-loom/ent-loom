package com.example.minicommerce.configuration;

import com.example.minicommerce.customer.entity.Customer;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.product.entity.Product;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.starter.web.registry.ExposedEntityRegistry;
import com.entloom.doc.core.contract.EntityDocumentationExposurePolicy;
import com.entloom.doc.core.model.DocEntityModel;
import com.entloom.doc.core.model.DocFieldModel;
import com.entloom.meta.starter.doc.EntityDocumentationExposurePolicyResolver;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 统一维护示例中商品和客户的通用 CRUD 与文档暴露边界。 */
@Configuration(proxyBeanMethods = false)
@Profile("example")
public class MasterDataExposureConfiguration {
    private static final Set<Class<?>> HTTP_ROUTE_ENTITIES = Set.of(Product.class, Customer.class, Order.class);
    private static final Set<Class<?>> DOCUMENTED_ENTITIES = Set.of(Product.class, Customer.class);
    private static final Map<Class<?>, Set<String>> EXPOSED_FIELDS = Map.of(
        Product.class, Set.of("id", "name", "price", "active"),
        Customer.class, Set.of("id", "displayName", "email")
    );

    @Bean
    ExposedEntityRegistry entityRouteRegistry(EntityMetaRegistry entityMetaRegistry) {
        ExposedEntityRegistry registry = new ExposedEntityRegistry(entityMetaRegistry);
        HTTP_ROUTE_ENTITIES.forEach(registry::expose);
        return registry;
    }

    /** 根据示例主体规则生成文档字段策略，统一实体和字段白名单。 */
    static EntityDocumentationExposurePolicyResolver documentationPolicy(
        Predicate<SubjectContext> subjectAllowed
    ) {
        return subject -> {
            boolean allowed = subjectAllowed.test(subject);
            return new EntityDocumentationExposurePolicy() {
                @Override
                public boolean isEntityExposed(DocEntityModel entity) {
                    return allowed && entity != null && DOCUMENTED_ENTITIES.contains(entity.entityClass());
                }

                @Override
                public boolean isFieldExposed(DocEntityModel entity, DocFieldModel field) {
                    return allowed && entity != null && DOCUMENTED_ENTITIES.contains(entity.entityClass())
                        && field != null && EXPOSED_FIELDS.getOrDefault(entity.entityClass(), Set.of())
                            .contains(field.property());
                }
            };
        };
    }
}
