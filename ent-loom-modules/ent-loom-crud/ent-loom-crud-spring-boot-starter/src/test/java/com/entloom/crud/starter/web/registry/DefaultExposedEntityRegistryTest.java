package com.entloom.crud.starter.web.registry;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.crud.starter.config.CrudWebAutoConfiguration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 通用 HTTP 直接复用实体元数据，不再维护 YAML 实体清单。 */
class DefaultExposedEntityRegistryTest {
    private final EntityMetaRegistry metadata = new CrudRuntimeModelBackedEntityMetaRegistry(
        new CrudNativeRuntimeModelParser().parse(Arrays.<Class<?>>asList(Customer.class, Product.class, Internal.class)));

    @Test
    void 已注册实体默认开放正式编码和别名() {
        ExposedEntityRegistry registry = new CrudWebAutoConfiguration().exposedEntityRegistry(metadata);
        assertThat(registry.resolveOrThrow("customer")).isSameAs(Customer.class);
        assertThat(registry.resolveOrThrow(Customer.class.getName())).isSameAs(Customer.class);
        assertThat(registry.resolveOrThrow("product")).isSameAs(Product.class);
    }

    @Test
    void 实体注解关闭接口及全部别名() {
        ExposedEntityRegistry registry = new CrudWebAutoConfiguration().exposedEntityRegistry(metadata);
        assertThatThrownBy(() -> registry.resolveOrThrow("internal")).isInstanceOf(CrudException.class);
        assertThatThrownBy(() -> registry.resolveOrThrow(Internal.class.getName())).isInstanceOf(CrudException.class);
        assertThatThrownBy(() -> registry.resolveOrThrow("unknown")).isInstanceOf(CrudException.class);
        assertThat(metadata.getEntityMeta(Internal.class)).isNotNull();
    }

    @EntCrudEntity(name = "customer", table = "customer")
    static class Customer { Long id; }
    @EntCrudEntity(name = "product", table = "product")
    static class Product { Long id; }
    @EntCrudEntity(name = "internal", table = "internal", httpExposed = false)
    static class Internal { Long id; }
}
