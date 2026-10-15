package com.entloom.crud.starter.config;

import com.entloom.crud.core.adapter.ResourceCatalogAdapter;
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证真实 YAML 绑定和标准 adapter 冻结链，覆盖没有策略消费者的实体。 */
class ConfiguredOwnershipTest {
    private static final String YAML = """
        ent.loom.crud.access:
          identities:
            customer:
              id-type: LONG
            merchant:
              id-type: LONG
          relations:
            customer:
              field: customerId
            merchant:
              field: merchantId
          entity-options:
            reference:
              ownership: []
            sale:
              ownership: [merchant]
        """;

    @Test
    void 缺省空列表和显式列表在没有策略时仍独立冻结() {
        runner(YAML).run(context -> {
            assertThat(context).hasNotFailed();
            var config = context.getBean(CrudAccessProperties.class);
            assertThat(config.getEntityOptions()).doesNotContainKey("order");
            assertThat(config.getEntityOptions().get("reference").getOwnership()).isEmpty();
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
        String yaml = YAML.replace("field: customerId", "identity-field: customerId")
            .replace("ownership: [merchant]", "relations:\n        customer: merchantId\n      ownership: [customer]");
        runner(yaml).run(context -> {
            assertThat(context).hasNotFailed();
            var sale = context.getBean(EntityMetaRegistry.class).getEntityMeta(Sale.class);
            assertThat(sale.resolveFieldMeta("customerId").isScopeField()).isFalse();
            assertThat(sale.resolveFieldMeta("merchantId").isScopeField()).isTrue();
        });
    }

    @Test
    void 非策略实体的类型错误未知关系未知字段重复归属均启动失败() {
        for (String yaml : List.of(
            YAML.replace("ownership: [merchant]", "ownership: [unknown]"),
            YAML.replace("ownership: [merchant]", "ownership: [merchant, merchant]"),
            YAML.replace("ownership: [merchant]", "ownership: [' ']"),
            YAML.replace("ownership: [merchant]", "ownership: [merchant]\n      relations:\n        merchant: missingId"),
            YAML.replace("sale:", "unknown:"),
            YAML.replace("id-type: LONG", "id-type: STRING"))) {
            runner(yaml).run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void 旧配置直接拒绝不提供兼容入口() {
        for (String yaml : List.of(
            YAML + "  defaults:\n    scope-fields: [customerId]\n",
            YAML.replace("ownership: []", "exclude-scope-fields: [customerId]"))) {
            runner(yaml).run(context -> assertThat(context).hasFailed());
        }
    }

    private ApplicationContextRunner runner(String yaml) {
        return new ApplicationContextRunner().withUserConfiguration(OwnershipConfiguration.class)
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
    @EnableConfigurationProperties(CrudAccessProperties.class)
    static class OwnershipConfiguration {
        @Bean
        CrudOwnershipRules ownership(CrudAccessProperties properties) {
            return new CrudCommonConfiguration().crudOwnershipRules(properties);
        }

        @Bean
        EntityMetaRegistry metas(ObjectProvider<ResourceCatalogAdapter[]> adapters, CrudOwnershipRules ownership) {
            return new CrudCommonConfiguration().metaRegistry(adapters, ownership);
        }
    }

    static class Order { }
    static class Reference { }
    static class Sale { }
    static class Undeclared { }
}
