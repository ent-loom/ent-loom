package com.entloom.meta.starter;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.adapter.ResourceCatalogAdapter;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.spring.config.CrudProperties;
import com.entloom.crud.spring.config.module.CrudCommonConfiguration;
import com.entloom.doc.annotations.EntDocEntity;
import com.entloom.doc.annotations.EntDocField;
import com.entloom.doc.core.spi.DocEntityOverride;
import com.entloom.doc.core.spi.DocFieldOverride;
import com.entloom.doc.core.spi.DocOverrideProvider;
import com.entloom.meta.adapter.doc.MetaDocAdapter;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.enums.EntFieldKind;

import java.util.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

class EntLoomMetaAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(MinimalCrudRegistryConfiguration.class, EntLoomMetaAutoConfiguration.class);

    @Test
    void metaEntitiesShouldAutoAssembleCrudRegistryAndDocAdapter() {
        contextRunner
            .withPropertyValues(entityClasses(MetaOrder.class, MetaCustomer.class))
            .run(context -> {
                Assertions.assertTrue(context.containsBean("entLoomMetaCrudAdapter"));
                Assertions.assertTrue(context.containsBean("entLoomMetaDocAdapter"));

                EntityMetaRegistry registry = context.getBean(EntityMetaRegistry.class);
                Assertions.assertTrue(registry instanceof CrudRuntimeModelBackedEntityMetaRegistry);
                Assertions.assertEquals("p1_meta_order", registry.getResourceDescriptor(MetaOrder.class).getResourceCode());
                Assertions.assertEquals("p1_meta_customer", registry.getResourceDescriptor(MetaCustomer.class).getResourceCode());
                Assertions.assertEquals("order_no", registry.getEntityMeta(MetaOrder.class).resolveColumn("orderNo"));

                MetaDocAdapter docAdapter = context.getBean(MetaDocAdapter.class);
                Map<String, Object> doc = docAdapter.buildOne(MetaOrder.class);
                Assertions.assertEquals("p1_meta_order", doc.get("resourceCode"));
                Assertions.assertEquals("Meta Order", doc.get("entityName"));
            });
    }

    @Test
    void nativeOnlyEntitiesShouldKeepCrudAndDocPathsIndependent() {
        contextRunner
            .withPropertyValues(entityClasses(CrudOnlyOrder.class, DocOnlyOrder.class))
            .run(context -> {
                EntityMetaRegistry registry = context.getBean(EntityMetaRegistry.class);
                Assertions.assertTrue(registry instanceof CrudRuntimeModelBackedEntityMetaRegistry);
                Assertions.assertEquals("p1_crud_only_order", registry.getResourceDescriptor(CrudOnlyOrder.class).getResourceCode());
                Assertions.assertThrows(ValidationException.class, () -> registry.getResourceDescriptor(DocOnlyOrder.class));

                MetaDocAdapter docAdapter = context.getBean(MetaDocAdapter.class);
                Assertions.assertNull(docAdapter.buildOne(CrudOnlyOrder.class));
                Map<String, Object> doc = docAdapter.buildOne(DocOnlyOrder.class);
                Assertions.assertEquals("Doc Only Order", doc.get("entityName"));
            });
    }

    @Test
    void basePackageShouldAutoAssembleCrudRegistryAndDocAdapter() {
        contextRunner
            .withPropertyValues("ent.loom.meta.base-packages=com.entloom.meta.starter.fixtures")
            .run(context -> {
                Assertions.assertTrue(context.containsBean("entLoomMetaCrudAdapter"));
                Assertions.assertTrue(context.containsBean("entLoomMetaDocAdapter"));

                EntityMetaRegistry registry = context.getBean(EntityMetaRegistry.class);
                Assertions.assertEquals("p1_scanned_meta_order",
                    registry.getResourceDescriptor(com.entloom.meta.starter.fixtures.ScannedMetaOrder.class).getResourceCode());

                MetaDocAdapter docAdapter = context.getBean(MetaDocAdapter.class);
                Map<String, Object> doc = docAdapter.buildOne(com.entloom.meta.starter.fixtures.ScannedMetaOrder.class);
                Assertions.assertEquals("Scanned Meta Order", doc.get("entityName"));
            });
    }

    @Test
    void invalidEntityClassNameShouldFailFast() {
        contextRunner
            .withPropertyValues("ent.loom.meta.entity-class-names=com.entloom.missing.NoSuchEntity")
            .run(context -> {
                Assertions.assertNotNull(context.getStartupFailure());
                Assertions.assertTrue(context.getStartupFailure().getMessage()
                    .contains("无法加载 ent.loom.meta.entity-class-names 配置的实体类"));
            });
    }

    @Test
    void configuredButEmptyBasePackageShouldFailFast() {
        contextRunner
            .withPropertyValues("ent.loom.meta.base-packages=com.entloom.missing")
            .run(context -> {
                Assertions.assertNotNull(context.getStartupFailure());
                Assertions.assertTrue(context.getStartupFailure().getMessage()
                    .contains("已配置 ent.loom.meta.entity-class-names 或 ent.loom.meta.base-packages"));
            });
    }

    @Test
    void disabledOrEmptyEntityClassListShouldFailWithoutRuntimeModelAdapter() {
        new ApplicationContextRunner()
            .withUserConfiguration(MinimalCrudRegistryConfiguration.class, EntLoomMetaAutoConfiguration.class)
            .run(context -> {
                Assertions.assertNotNull(context.getStartupFailure());
                Assertions.assertTrue(context.getStartupFailure().getMessage().contains("未找到 ResourceCatalogAdapter"));
            });

        contextRunner
            .withPropertyValues(
                "ent.loom.meta.enabled=false",
                entityClasses(MetaOrder.class, MetaCustomer.class)[0]
            )
            .run(context -> {
                Assertions.assertNotNull(context.getStartupFailure());
                Assertions.assertTrue(context.getStartupFailure().getMessage().contains("未找到 ResourceCatalogAdapter"));
            });
    }

    @Test
    void customBusinessAdapterAndMetaAdapterShouldStayAtRegistryBoundary() {
        new ApplicationContextRunner()
            .withUserConfiguration(
                MinimalCrudRegistryConfiguration.class,
                EntLoomMetaAutoConfiguration.class,
                BusinessCatalogConfiguration.class
            )
            .withPropertyValues(entityClasses(MetaOrder.class, MetaCustomer.class))
            .run(context -> {
                ResourceCatalogAdapter[] adapters = context.getBeansOfType(ResourceCatalogAdapter.class)
                    .values()
                    .toArray(new ResourceCatalogAdapter[0]);
                Assertions.assertEquals(2, adapters.length);

                EntityMetaRegistry registry = context.getBean(EntityMetaRegistry.class);
                Assertions.assertTrue(registry instanceof CrudRuntimeModelBackedEntityMetaRegistry);
                Assertions.assertEquals("business_invoice", registry.getResourceDescriptor(BusinessInvoice.class).getResourceCode());
                Assertions.assertEquals("p1_meta_order", registry.getResourceDescriptor(MetaOrder.class).getResourceCode());
            });
    }

    @Test
    void customDocOverrideProviderShouldJoinMetaDocAdapter() {
        new ApplicationContextRunner()
            .withUserConfiguration(
                MinimalCrudRegistryConfiguration.class,
                EntLoomMetaAutoConfiguration.class,
                BusinessDocOverrideConfiguration.class
            )
            .withPropertyValues(entityClasses(MetaOrder.class, MetaCustomer.class))
            .run(context -> {
                MetaDocAdapter docAdapter = context.getBean(MetaDocAdapter.class);
                Map<String, Object> doc = docAdapter.buildOne(MetaOrder.class);
                Assertions.assertEquals("业务订单文档", doc.get("entityName"));
                Assertions.assertEquals("交易", doc.get("group"));

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> fields = (List<Map<String, Object>>) doc.get("fields");
                Map<String, Object> orderNo = findBy(fields, "property", "orderNo");
                Assertions.assertEquals("业务订单号", orderNo.get("name"));
                Assertions.assertEquals(Arrays.asList("admin"), orderNo.get("visibleFor"));
            });
    }

    @Test
    void duplicateAdapterOutputShouldFailFastAtRegistryBoundary() {
        new ApplicationContextRunner()
            .withUserConfiguration(
                MinimalCrudRegistryConfiguration.class,
                EntLoomMetaAutoConfiguration.class,
                DuplicateBusinessCatalogConfiguration.class
            )
            .withPropertyValues(entityClasses(MetaOrder.class, MetaCustomer.class))
            .run(context -> {
                Assertions.assertNotNull(context.getStartupFailure());
                Assertions.assertTrue(context.getStartupFailure().getMessage().contains("资源编码或别名重复"));
            });
    }

    private static String[] entityClasses(Class<?>... entityClasses) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < entityClasses.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(entityClasses[i].getName());
        }
        return new String[] {"ent.loom.meta.entity-class-names=" + builder};
    }

    private static ResourceCatalogAdapter adapter(final Class<?> type, final String code, final String table) {
        return new ResourceCatalogAdapter() {
            @Override
            public com.entloom.crud.core.runtime.model.CrudRuntimeModel runtimeModel() {
                Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
                fields.put("id", new EntityFieldMeta("id", Long.class, "id", false, false, true, true));
                fields.put("name", new EntityFieldMeta("name", String.class, "name", true, false, true, true));
                EntityMeta meta = new EntityMeta(
                    type,
                    new ResourceDescriptor(type, code, "business-service", Collections.<String>emptyList()),
                    table,
                    "id",
                    "",
                    fields
                );
                return com.entloom.crud.core.runtime.model.CrudRuntimeModel.from(
                    Collections.singletonList(meta),
                    Collections.<com.entloom.crud.core.runtime.meta.RelationEdge>emptyList()
                );
            }
        };
    }

    private static Map<String, Object> findBy(List<Map<String, Object>> items, String key, String value) {
        for (Map<String, Object> item : items) {
            if (value.equals(item.get(key))) {
                return item;
            }
        }
        Assertions.fail("Missing item " + key + "=" + value);
        return null;
    }

    @Configuration
    @EnableConfigurationProperties(CrudProperties.class)
    @Import(CrudCommonConfiguration.class)
    static class MinimalCrudRegistryConfiguration {
    }

    @Configuration
    static class BusinessCatalogConfiguration {
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        ResourceCatalogAdapter businessCatalogAdapter() {
            return adapter(BusinessInvoice.class, "business_invoice", "business_invoice");
        }
    }

    @Configuration
    static class DuplicateBusinessCatalogConfiguration {
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        ResourceCatalogAdapter duplicateBusinessCatalogAdapter() {
            return adapter(BusinessInvoice.class, "p1_meta_order", "business_invoice");
        }
    }

    @Configuration
    static class BusinessDocOverrideConfiguration {
        @Bean
        DocOverrideProvider docOverrideProvider() {
            return new DocOverrideProvider() {
                @Override
                public DocEntityOverride overrideFor(Class<?> entityClass, String resourceCode) {
                    if (entityClass != MetaOrder.class) {
                        return null;
                    }
                    return DocEntityOverride.builder()
                        .entityName("业务订单文档")
                        .group("交易")
                        .field(DocFieldOverride.builder("orderNo")
                            .name("业务订单号")
                            .visibleFor(Arrays.asList("admin"))
                            .build())
                        .build();
                }
            };
        }
    }

    @EntEntity(entity = "p1_meta_order", label = "Meta Order", service = "order-service")
    static class MetaOrder {
        @EntField(value = EntFieldKind.ID, required = OptionalBoolean.TRUE)
        private Long id;

        @EntField(value = EntFieldKind.TEXT, label = "Order No", required = OptionalBoolean.TRUE)
        private String orderNo;

        @EntField(value = EntFieldKind.REF_ID)
        private Long customerId;
    }

    @EntEntity(entity = "p1_meta_customer", label = "Meta Customer", service = "order-service")
    static class MetaCustomer {
        @EntField(value = EntFieldKind.ID, required = OptionalBoolean.TRUE)
        private Long id;

        @EntField(value = EntFieldKind.TEXT)
        private String name;
    }

    @EntCrudEntity(name = "p1_crud_only_order", table = "p1_crud_only_order")
    static class CrudOnlyOrder {
        private Long id;
        private String orderNo;
    }

    @EntDocEntity(name = "Doc Only Order", description = "DOC-only fixture")
    static class DocOnlyOrder {
        @EntDocField(name = "Order No", required = OptionalBoolean.TRUE)
        private String orderNo;
    }

    static class BusinessInvoice {
    }
}
