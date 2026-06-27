package com.entloom.crud.starter.web.registry;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import java.util.Collections;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ExposedEntityRegistryTest {
    @Test
    void should_resolve_official_resource_code_and_descriptor_aliases() {
        EntityMetaRegistry metaRegistry = new CrudRuntimeModelBackedEntityMetaRegistry(
            new CrudNativeRuntimeModelParser().parse(Collections.<Class<?>>singletonList(WebOrderEntity.class))
        );
        ExposedEntityRegistry registry = new ExposedEntityRegistry(metaRegistry);
        registry.expose(WebOrderEntity.class);
        registry.setIncludeEntities(Collections.singleton("order-resource"));

        Assertions.assertSame(WebOrderEntity.class, registry.resolveOrThrow("order-resource"));
        Assertions.assertSame(WebOrderEntity.class, registry.resolveOrThrow(WebOrderEntity.class.getSimpleName()));
        Assertions.assertSame(WebOrderEntity.class, registry.resolveOrThrow(WebOrderEntity.class.getName()));
        Assertions.assertEquals("order-resource", registry.canonicalCode(WebOrderEntity.class.getSimpleName()));
    }

    @EntCrudEntity(name = "order-resource", table = "t_web_order")
    private static class WebOrderEntity {
        Long id;
    }
}
