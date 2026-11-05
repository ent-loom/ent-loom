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
    void 有Crud声明的实体开放正式编码及别名而无声明实体不发布() {
        ExposedEntityRegistry registry = new CrudWebAutoConfiguration().exposedEntityRegistry(metadata, declarations());
        assertThat(registry.resolveOrThrow("customer")).isSameAs(Customer.class);
        assertThat(registry.resolveOrThrow(Customer.class.getName())).isSameAs(Customer.class);
        assertThatThrownBy(() -> registry.resolveOrThrow("product")).isInstanceOf(CrudException.class);
    }

    @Test
    void 实体注解关闭接口及全部别名() {
        ExposedEntityRegistry registry = new CrudWebAutoConfiguration().exposedEntityRegistry(metadata, declarations());
        assertThatThrownBy(() -> registry.resolveOrThrow("internal")).isInstanceOf(CrudException.class);
        assertThatThrownBy(() -> registry.resolveOrThrow(Internal.class.getName())).isInstanceOf(CrudException.class);
        assertThatThrownBy(() -> registry.resolveOrThrow("unknown")).isInstanceOf(CrudException.class);
        assertThat(metadata.getEntityMeta(Internal.class)).isNotNull();
    }

    private com.entloom.crud.starter.config.CrudEntityDeclarations declarations() {
        return new com.entloom.crud.starter.config.CrudEntityDeclarations(Arrays.<Class<?>>asList(Customer.class, Internal.class));
    }

    @EntCrudEntity(name = "customer", table = "customer")
    static class Customer { Long id; }
    @EntCrudEntity(name = "product", table = "product")
    static class Product { Long id; }
    @EntCrudEntity(name = "internal", table = "internal", httpExposed = false)
    static class Internal { Long id; }
}
