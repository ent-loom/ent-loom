package com.entloom.crud.starter.config;

import com.entloom.crud.core.adapter.ResourceCatalogAdapter;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.runtime.model.CrudOwnershipRules;
import com.entloom.crud.core.runtime.model.CrudRuntimeModel;
import com.entloom.crud.starter.config.module.CrudCommonConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Import;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证应用关系配置、实体注解和标准 adapter 冻结链。 */
class ConfiguredOwnershipTest {
    private static final String YAML = """
        ent.loom.crud.access:
          identities:
            customer:
              id-type: long
            merchant:
              id-type: long
          relations:
            customer:
              field: customerId
            merchant:
              field: merchantId
        """;

    @Test
    void 缺省空列表和显式列表在没有策略时仍独立冻结() {
        runner(YAML, Sale.class).run(context -> {
            assertThat(context).hasNotFailed();
            var config = context.getBean(CrudAccessProperties.class);
            assertThat(context.getBean(CrudEntityDeclarations.class).options()).doesNotContainKey("order");
            assertThat(context.getBean(CrudEntityDeclarations.class).options().get("reference").getOwnership()).isEmpty();
            var metas = context.getBean(EntityMetaRegistry.class);
            var order = metas.getEntityMeta(Order.class);
            assertThat(order.resolveFieldMeta("customerId").isScopeField()).isTrue();
            assertThat(order.resolveFieldMeta("merchantId").isScopeField()).isTrue();
            assertThat(order.resolveFieldMeta("customerId").isWritable()).isFalse();
            assertThat(metas.getEntityMeta(Reference.class).resolveFieldMeta("customerId").isScopeField()).isFalse();
            assertThat(metas.getEntityMeta(Reference.class).resolveFieldMeta("customerId").isWritable()).isTrue();
            assertThat(metas.getEntityMeta(Sale.class).resolveFieldMeta("customerId").isScopeField()).isFalse();
            assertThat(metas.getEntityMeta(Sale.class).resolveFieldMeta("merchantId").isScopeField()).isTrue();
            // 未进入接口清单或实体差异的实体也统一落实默认保护。
            assertThat(metas.getEntityMeta(Undeclared.class).resolveFieldMeta("customerId").isScopeField()).isTrue();
            config.getRelations().clear();
            assertThat(order.resolveFieldMeta("customerId").isScopeField()).isTrue();
        });
    }

    @Test
    void 实体关系映射不依赖策略且缺省采用关系名Id() {
        String yaml = YAML.replace("field: customerId", "identity-field: customerId");
        runner(yaml, SaleMapped.class).run(context -> {
            assertThat(context).hasNotFailed();
            var sale = context.getBean(EntityMetaRegistry.class).getEntityMeta(Sale.class);
            assertThat(sale.resolveFieldMeta("customerId").isScopeField()).isFalse();
            assertThat(sale.resolveFieldMeta("merchantId").isScopeField()).isTrue();
        });
    }

    @Test
    void 非策略实体的类型错误未知关系未知字段重复归属均启动失败() {
        for (Class<?> entity : List.of(SaleUnknown.class, SaleDuplicate.class, SaleBlank.class,
                SaleMissing.class, UnknownResource.class)) {
            runner(YAML, entity).run(context -> assertThat(context).hasFailed());
        }
        runner(YAML.replace("id-type: long", "id-type: string"), Sale.class)
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 旧配置直接拒绝不提供兼容入口() {
        runner(YAML + "  defaults:\n    scope-fields: [customerId]\n", Sale.class)
            .run(context -> assertThat(context).hasFailed());
    }

    private ApplicationContextRunner runner(String yaml, Class<?> saleDeclaration) {
        return new ApplicationContextRunner().withUserConfiguration(OwnershipConfiguration.class)
            .withBean(CrudEntityDeclarations.class, () -> new CrudEntityDeclarations(List.of(Reference.class, saleDeclaration)))
            .withBean(ResourceCatalogAdapter.class, () -> {
                var adapter = mock(ResourceCatalogAdapter.class);
                when(adapter.runtimeModel()).thenReturn(CrudRuntimeModel.from(List.of(
                    meta(Order.class, "order"), meta(Reference.class, "reference"),
                    meta(Sale.class, "sale"), meta(Undeclared.class, "undeclared")), List.of()));
                return adapter;
            }).withInitializer(context -> {
                try {
                    new YamlPropertySourceLoader().load("归属配置", new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)))
                        .forEach(source -> context.getEnvironment().getPropertySources().addFirst(source));
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
    }

    private EntityMeta meta(Class<?> type, String resource) {
        var fields = Map.of(
            "id", new EntityFieldMeta("id", Long.class, "id", false, false, true, true),
            "customerId", new EntityFieldMeta("customerId", Long.class, "customer_id", false, false, true, true),
            "merchantId", new EntityFieldMeta("merchantId", Long.class, "merchant_id", false, false, true, true));
        return new EntityMeta(type, new ResourceDescriptor(type, resource, null, List.of()), resource, "id", null, fields);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CrudPropertiesConfiguration.class)
    static class OwnershipConfiguration {
        @Bean
        CrudOwnershipRules ownership(CrudAccessProperties properties, CrudEntityDeclarations declarations) {
            return new CrudCommonConfiguration().crudOwnershipRules(properties, declarations);
        }

        @Bean
        EntityMetaRegistry metas(ObjectProvider<ResourceCatalogAdapter[]> adapters, CrudOwnershipRules ownership) {
            return new CrudCommonConfiguration().metaRegistry(adapters, ownership);
        }
    }

    static class Order { }
    @EntCrudEntity(name = "reference", ownershipSpecified = true)
    static class Reference { }
    @EntCrudEntity(name = "sale", ownership = "merchant")
    static class Sale { }
    static class Undeclared { }

    @EntCrudEntity(name = "sale", ownership = "customer", relations = "customer=merchantId")
    static class SaleMapped { }
    @EntCrudEntity(name = "sale", ownership = "unknown")
    static class SaleUnknown { }
    @EntCrudEntity(name = "sale", ownership = {"merchant", "merchant"})
    static class SaleDuplicate { }
    @EntCrudEntity(name = "sale", ownership = " ")
    static class SaleBlank { }
    @EntCrudEntity(name = "sale", ownership = "merchant", relations = "merchant=missingId")
    static class SaleMissing { }
    @EntCrudEntity(name = "unknown", ownership = "merchant")
    static class UnknownResource { }
}
