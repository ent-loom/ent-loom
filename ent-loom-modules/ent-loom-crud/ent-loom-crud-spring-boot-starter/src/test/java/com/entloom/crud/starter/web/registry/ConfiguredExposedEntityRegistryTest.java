package com.entloom.crud.starter.web.registry;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.crud.starter.config.CrudAccessProperties;
import com.entloom.crud.starter.config.CrudWebAutoConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.*;

class ConfiguredExposedEntityRegistryTest {
    private final EntityMetaRegistry metadata = new CrudRuntimeModelBackedEntityMetaRegistry(
        new CrudNativeRuntimeModelParser().parse(Arrays.<Class<?>>asList(Customer.class, Product.class, Internal.class)));

    @Test
    void 配置实体不限制未配置实体且默认开放正式编码和别名() {
        CrudAccessProperties access = new CrudAccessProperties();
        access.getEntities().put("customer", new CrudAccessProperties.Entity());
        ExposedEntityRegistry registry = registry(access);
        assertThat(registry.resolveOrThrow("customer")).isSameAs(Customer.class);
        assertThat(registry.resolveOrThrow(Customer.class.getName())).isSameAs(Customer.class);
        assertThat(registry.resolveOrThrow("product")).isSameAs(Product.class);
        assertThatThrownBy(() -> registry.resolveOrThrow("internal")).isInstanceOf(CrudException.class);
        assertThatThrownBy(() -> registry.resolveOrThrow(Internal.class.getName())).isInstanceOf(CrudException.class);
    }

    @Test
    void 空差异配置和仅策略绑定或业务差异均沿用默认开放() {
        CrudAccessProperties access = new CrudAccessProperties();
        access.getPolicyBindings().put("customer-record", List.of("customer"));
        access.getEntityOptions().put("customer", new CrudAccessProperties.EntityOptions());
        assertThat(registry(access).resolveOrThrow("customer")).isSameAs(Customer.class);
        assertThat(registry(new CrudAccessProperties()).resolveOrThrow("customer")).isSameAs(Customer.class);
        assertThat(metadata.getEntityMeta(Customer.class)).isNotNull();
    }

    @Test
    void 显式关闭接口同时关闭实体别名() {
        CrudAccessProperties access = new CrudAccessProperties();
        CrudAccessProperties.Entity customer = new CrudAccessProperties.Entity();
        customer.setHttpExposed(false);
        access.getEntities().put("customer", customer);
        ExposedEntityRegistry registry = registry(access);
        assertThatThrownBy(() -> registry.resolveOrThrow("customer")).isInstanceOf(CrudException.class);
        assertThatThrownBy(() -> registry.resolveOrThrow(Customer.class.getName())).isInstanceOf(CrudException.class);
    }

    @Test
    void 显式开启不能覆盖实体注解的禁止声明() {
        CrudAccessProperties access = new CrudAccessProperties();
        access.getEntities().put("internal", new CrudAccessProperties.Entity());
        ExposedEntityRegistry registry = registry(access);
        assertThatThrownBy(() -> registry.resolveOrThrow("internal")).isInstanceOf(CrudException.class);
        assertThatThrownBy(() -> registry.resolveOrThrow(Internal.class.getName())).isInstanceOf(CrudException.class);
    }

    @Test
    void 未知资源与两处重复差异配置在启动时拒绝() {
        CrudAccessProperties access = new CrudAccessProperties();
        access.getEntities().put("missing", new CrudAccessProperties.Entity());
        assertThatThrownBy(() -> registry(access)).isInstanceOf(ValidationException.class).hasMessageContaining("未知实体");
        access.getEntities().clear();
        access.getEntities().put("customer", new CrudAccessProperties.Entity());
        access.getEntityOptions().put("customer", new CrudAccessProperties.EntityOptions());
        assertThatThrownBy(() -> registry(access)).isInstanceOf(ValidationException.class).hasMessageContaining("同时出现");
    }

    @Test
    void 真实Yaml支持空值缺省和显式关闭且不影响未配置实体() {
        String yaml = """
            ent.loom.crud.access:
              entities:
                product:
                customer:
                  http-exposed: false
              entity-options:
                note:
                  ownership: []
            """;
        new ApplicationContextRunner().withUserConfiguration(BindingConfiguration.class)
            .withInitializer(context -> {
                try {
                    new YamlPropertySourceLoader().load("实体差异", new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)))
                        .forEach(source -> context.getEnvironment().getPropertySources().addFirst(source));
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }).run(context -> {
                assertThat(context).hasNotFailed();
                CrudAccessProperties access = context.getBean(CrudAccessProperties.class);
                assertThat(access.getEntities()).containsOnlyKeys("product", "customer");
                assertThat(access.getEntities().get("product").isHttpExposed()).isTrue();
                assertThat(access.getEntities().get("customer").isHttpExposed()).isFalse();
                assertThat(access.getEntityOptions().get("note").getOwnership()).isEmpty();
                assertThat(registry(access).resolveOrThrow("product")).isSameAs(Product.class);
                assertThatThrownBy(() -> registry(access).resolveOrThrow("customer")).isInstanceOf(CrudException.class);
            });
    }

    private ExposedEntityRegistry registry(CrudAccessProperties access) {
        return new CrudWebAutoConfiguration().exposedEntityRegistry(access, metadata);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CrudAccessProperties.class)
    static class BindingConfiguration { }

    @EntCrudEntity(name = "customer", table = "customer")
    static class Customer { Long id; }
    @EntCrudEntity(name = "product", table = "product")
    static class Product { Long id; }
    @EntCrudEntity(name = "internal", table = "internal", httpExposed = false)
    static class Internal { Long id; }
}
