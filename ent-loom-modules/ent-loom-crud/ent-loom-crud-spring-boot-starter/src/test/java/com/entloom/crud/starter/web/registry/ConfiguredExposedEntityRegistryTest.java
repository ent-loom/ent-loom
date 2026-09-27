package com.entloom.crud.starter.web.registry;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.crud.starter.config.CrudProperties;
import com.entloom.crud.starter.config.CrudWebAutoConfiguration;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConfiguredExposedEntityRegistryTest {
    private final EntityMetaRegistry metadata = new CrudRuntimeModelBackedEntityMetaRegistry(
        new CrudNativeRuntimeModelParser().parse(Arrays.<Class<?>>asList(Customer.class, Internal.class)));

    @Test
    void 白名单自动注册正式编码及别名但不放行其他实体() {
        CrudProperties properties = new CrudProperties();
        properties.getController().setIncludeEntities(Collections.singleton("customer"));
        ExposedEntityRegistry registry = new CrudWebAutoConfiguration().exposedEntityRegistry(properties, metadata);
        assertThat(registry.resolveOrThrow("customer")).isSameAs(Customer.class);
        assertThat(registry.resolveOrThrow(Customer.class.getName())).isSameAs(Customer.class);
        assertThatThrownBy(() -> registry.resolveOrThrow("internal")).isInstanceOf(CrudException.class);
        assertThatThrownBy(() -> registry.resolveOrThrow(Internal.class.getName())).isInstanceOf(CrudException.class);
    }

    @Test
    void 空白名单不自动公开任何实体() {
        ExposedEntityRegistry registry = new CrudWebAutoConfiguration().exposedEntityRegistry(new CrudProperties(), metadata);
        assertThatThrownBy(() -> registry.resolveOrThrow("customer")).isInstanceOf(CrudException.class);
    }

    @Test
    void 全量模式开放注册实体但注解禁止项及其别名始终关闭() {
        CrudProperties properties = new CrudProperties();
        properties.getController().setExposureMode(EntityExposureMode.ALL_REGISTERED);
        ExposedEntityRegistry registry = new CrudWebAutoConfiguration().exposedEntityRegistry(properties, metadata);
        assertThat(registry.resolveOrThrow("customer")).isSameAs(Customer.class);
        assertThat(registry.resolveOrThrow(Customer.class.getName())).isSameAs(Customer.class);
        assertThatThrownBy(() -> registry.resolveOrThrow("internal")).isInstanceOf(CrudException.class);
        assertThatThrownBy(() -> registry.resolveOrThrow(Internal.class.getName())).isInstanceOf(CrudException.class);
        assertThat(metadata.getEntityMeta(Internal.class)).isNotNull();
    }

    @Test
    void 全量模式支持通过配置黑名单关闭实体及其别名() {
        CrudProperties properties = new CrudProperties();
        properties.getController().setExposureMode(EntityExposureMode.ALL_REGISTERED);
        properties.getController().setExcludeEntities(Collections.singleton("internal"));
        ExposedEntityRegistry registry = new CrudWebAutoConfiguration().exposedEntityRegistry(properties, metadata);

        assertThatThrownBy(() -> registry.resolveOrThrow("internal")).isInstanceOf(CrudException.class);
        assertThatThrownBy(() -> registry.resolveOrThrow(Internal.class.getName())).isInstanceOf(CrudException.class);
    }

    @Test
    void 显式包含也不能覆盖实体关闭声明() {
        CrudProperties properties = new CrudProperties();
        properties.getController().setIncludeEntities(Collections.singleton("internal"));
        ExposedEntityRegistry registry = new CrudWebAutoConfiguration().exposedEntityRegistry(properties, metadata);
        assertThatThrownBy(() -> registry.resolveOrThrow("internal")).isInstanceOf(CrudException.class);
        assertThatThrownBy(() -> registry.resolveOrThrow(Internal.class.getName())).isInstanceOf(CrudException.class);
    }

    @Test
    void 全量模式不能同时配置包含清单() {
        CrudProperties properties = new CrudProperties();
        properties.getController().setExposureMode(EntityExposureMode.ALL_REGISTERED);
        properties.getController().setIncludeEntities(Collections.singleton("customer"));
        assertThatThrownBy(() -> new CrudWebAutoConfiguration().exposedEntityRegistry(properties, metadata))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("include-entities");
    }

    @Test
    void 配置绑定支持枚举且默认显式模式() {
        assertThat(new CrudProperties().getController().getExposureMode()).isEqualTo(EntityExposureMode.EXPLICIT);
        org.springframework.boot.context.properties.bind.Binder binder = new org.springframework.boot.context.properties.bind.Binder(
            new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(
                Collections.singletonMap("ent.loom.crud.controller.exposure-mode", "ALL_REGISTERED")));
        CrudProperties properties = binder.bind("ent.loom.crud", CrudProperties.class).get();
        assertThat(properties.getController().getExposureMode()).isEqualTo(EntityExposureMode.ALL_REGISTERED);
    }

    @EntCrudEntity(name = "customer", table = "customer")
    static class Customer { Long id; }
    @EntCrudEntity(name = "internal", table = "internal", httpExposed = false)
    static class Internal { Long id; }
}
