package com.entloom.crud.starter.config;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.DataScopeDeniedException;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.governance.scope.ReadVisibilityContributor;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.crud.starter.config.module.ReadVisibilityConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ByteArrayResource;
import static org.junit.jupiter.api.Assertions.*;

class ReadVisibilityConfigurationTest {
    private final EntityMetaRegistry registry = new CrudRuntimeModelBackedEntityMetaRegistry(
        new CrudNativeRuntimeModelParser().parse(Collections.<Class<?>>singletonList(Product.class)));

    @Test
    void 真实Yaml保留放行入口及集合条件并按入口强制执行() throws Exception {
        CrudProperties properties = bind(
            "product:\n  consumer:\n    active: true\n    id: [1, 2]\n  management: all\n");
        ReadVisibilityContributor contributor = compile(properties);
        CrudDataScope consumer = scope(contributor, "consumer");
        assertEquals(true, consumer.readScope(Product.class).getConditions().get("active"));
        assertEquals(Arrays.asList(1L, 2L), consumer.readScope(Product.class).getConditions().get("id"));
        assertTrue(scope(contributor, "management").readScope(Product.class).getConditions().isEmpty());
        assertThrows(DataScopeDeniedException.class, () -> scope(contributor, "unknown"));
    }

    @Test
    void 启动校验拒绝未知实体字段类型及非法条件结构() throws Exception {
        for (String declaration : Arrays.asList(
            "missing:\n  consumer:\n    active: true\n",
            "product:\n  consumer:\n    missing: true\n",
            "product:\n  consumer:\n    active: invalid\n",
            "product:\n  consumer: true\n",
            "product:\n  consumer:\n    active:\n      nested: true\n")) {
            CrudProperties properties = bind(declaration);
            assertThrows(IllegalArgumentException.class, () -> compile(properties));
        }
    }

    @Test
    void 空配置不纳管实体() {
        assertNull(scope(compile(new CrudProperties()), "consumer").readScope(Product.class));
    }

    @Test
    void 空映射不会误放行入口() throws Exception {
        ReadVisibilityContributor contributor = compile(bind(
            "product:\n  consumer:\n    active: true\n  management: {}\n"));
        assertThrows(DataScopeDeniedException.class, () -> scope(contributor, "management"));
    }

    private CrudProperties bind(String declaration) throws Exception {
        String yaml = "ent:\n  loom:\n    crud:\n      governance:\n        read-visibility:\n"
            + declaration.replaceAll("(?m)^", "          ");
        ByteArrayResource resource = new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
        return new Binder(ConfigurationPropertySources.from(
            new YamlPropertySourceLoader().load("visibility", resource).get(0)))
            .bind("ent.loom.crud", Bindable.of(CrudProperties.class)).get();
    }

    private ReadVisibilityContributor compile(CrudProperties properties) {
        return new ReadVisibilityConfiguration().configuredReadVisibilityContributor(properties, registry);
    }

    private CrudDataScope scope(ReadVisibilityContributor contributor, String entry) {
        return contributor.contribute(new CrudResourceAction(registry.getResourceDescriptor(Product.class),
            CrudOperationKey.of(QueryOperation.PAGE), "default", null, entry, "http", false),
            null, QuerySpec.<Product>builder().rootType(Product.class).build(), CrudDataScope.allowAll());
    }

    @EntCrudEntity(name = "product")
    static class Product {
        Long id;
        Boolean active;
    }
}
