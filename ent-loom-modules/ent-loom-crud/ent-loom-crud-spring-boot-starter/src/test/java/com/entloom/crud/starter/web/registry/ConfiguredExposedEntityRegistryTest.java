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
        new CrudNativeRuntimeModelParser().parse(Arrays.<Class<?>>asList(Customer.class, Internal.class)));

    @Test
    void 接口清单缺省开放正式编码及别名且不放行清单外实体() {
        CrudAccessProperties access = new CrudAccessProperties();
        access.getEntities().put("customer", new CrudAccessProperties.Entity());
        ExposedEntityRegistry registry = registry(access);
        assertThat(registry.resolveOrThrow("customer")).isSameAs(Customer.class);
        assertThat(registry.resolveOrThrow(Customer.class.getName())).isSameAs(Customer.class);
        assertThatThrownBy(() -> registry.resolveOrThrow("internal")).isInstanceOf(CrudException.class);
        assertThatThrownBy(() -> registry.resolveOrThrow(Internal.class.getName())).isInstanceOf(CrudException.class);
    }

    @Test
    void 空清单和仅策略绑定或内部配置均不自动开放接口() {
        CrudAccessProperties access = new CrudAccessProperties();
        access.getPolicyBindings().put("customer-record", List.of("customer"));
        access.getEntityOptions().put("customer", new CrudAccessProperties.EntityOptions());
        assertThatThrownBy(() -> registry(access).resolveOrThrow("customer")).isInstanceOf(CrudException.class);
        assertThatThrownBy(() -> registry(new CrudAccessProperties()).resolveOrThrow("customer"))
            .isInstanceOf(CrudException.class);
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
    void 清单不能覆盖实体注解的禁止声明() {
        CrudAccessProperties access = new CrudAccessProperties();
        access.getEntities().put("internal", new CrudAccessProperties.Entity());
        ExposedEntityRegistry registry = registry(access);
        assertThatThrownBy(() -> registry.resolveOrThrow("internal")).isInstanceOf(CrudException.class);
        assertThatThrownBy(() -> registry.resolveOrThrow(Internal.class.getName())).isInstanceOf(CrudException.class);
    }

    @Test
    void 未知资源与接口内部重复配置在启动时拒绝() {
        CrudAccessProperties access = new CrudAccessProperties();
        access.getEntities().put("missing", new CrudAccessProperties.Entity());
        assertThatThrownBy(() -> registry(access)).isInstanceOf(ValidationException.class).hasMessageContaining("未知实体");
        access.getEntities().clear();
        access.getEntities().put("customer", new CrudAccessProperties.Entity());
        access.getEntityOptions().put("customer", new CrudAccessProperties.EntityOptions());
        assertThatThrownBy(() -> registry(access)).isInstanceOf(ValidationException.class).hasMessageContaining("同时出现");
    }

    @Test
    void 真实Yaml空值可登记接口并默认开放且支持显式关闭() {
        String yaml = """
            ent.loom.crud.access:
              entities:
                customer:
                internal:
                  http-exposed: false
              entity-options:
                note:
                  exclude-scope-fields: [customerId]
            """;
        new ApplicationContextRunner().withUserConfiguration(BindingConfiguration.class)
            .withInitializer(context -> {
                try {
                    new YamlPropertySourceLoader().load("接口清单", new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)))
                        .forEach(source -> context.getEnvironment().getPropertySources().addFirst(source));
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }).run(context -> {
                assertThat(context).hasNotFailed();
                CrudAccessProperties access = context.getBean(CrudAccessProperties.class);
                assertThat(access.getEntities()).containsOnlyKeys("customer", "internal");
                assertThat(access.getEntities().get("customer").isHttpExposed()).isTrue();
                assertThat(access.getEntities().get("internal").isHttpExposed()).isFalse();
                assertThat(access.getEntityOptions().get("note").getExcludeScopeFields()).containsExactly("customerId");
                assertThat(registry(access).resolveOrThrow("customer")).isSameAs(Customer.class);
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
    @EntCrudEntity(name = "internal", table = "internal", httpExposed = false)
    static class Internal { Long id; }
}
