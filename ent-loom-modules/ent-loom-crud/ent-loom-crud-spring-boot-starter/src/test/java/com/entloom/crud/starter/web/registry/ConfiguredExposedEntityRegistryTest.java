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

    @EntCrudEntity(name = "customer", table = "customer")
    static class Customer { Long id; }
    @EntCrudEntity(name = "internal", table = "internal")
    static class Internal { Long id; }
}
