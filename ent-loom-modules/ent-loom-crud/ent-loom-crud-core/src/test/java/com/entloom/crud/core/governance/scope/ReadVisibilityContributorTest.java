package com.entloom.crud.core.governance.scope;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.StatsOperation;
import com.entloom.crud.api.enums.ExportOperation;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.DataScopeDeniedException;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReadVisibilityContributorTest {
    private final EntityMetaRegistry registry = new CrudRuntimeModelBackedEntityMetaRegistry(
        new CrudNativeRuntimeModelParser().parse(Arrays.<Class<?>>asList(Product.class, Customer.class)));
    private final QuerySpec<Product> spec = QuerySpec.<Product>builder().rootType(Product.class).build();

    @Test
    void 规则绑定入口且不向其他实体传播() {
        ReadVisibilityContributor contributor = contributor("active", "true");
        CrudDataScope consumer = contributor.contribute(action("consumer"), null, spec, CrudDataScope.allowAll());
        assertEquals(Boolean.TRUE, consumer.readScope(Product.class).getConditions().get("active"));
        assertNull(consumer.readScope(Customer.class));
        CrudDataScope admin = contributor.contribute(action("admin"), null, spec, CrudDataScope.allowAll());
        assertTrue(admin.readScope(Product.class).getConditions().isEmpty());
        assertThrows(DataScopeDeniedException.class,
            () -> contributor.contribute(action("unknown"), null, spec, CrudDataScope.allowAll()));
        assertThrows(DataScopeDeniedException.class,
            () -> contributor.contribute(action(null), null, spec, CrudDataScope.allowAll()));
        for (CrudOperationKey operation : Arrays.asList(CrudOperationKey.of(QueryOperation.DETAIL),
            CrudOperationKey.of(StatsOperation.QUERY), CrudOperationKey.of(ExportOperation.PREVIEW))) {
            CrudResourceAction action = new CrudResourceAction(registry.getResourceDescriptor(Product.class), operation,
                "default", null, "consumer", "http", false);
            assertTrue(contributor.supports(action, spec));
        }
        assertFalse(contributor.supports(new CrudResourceAction(registry.getResourceDescriptor(Product.class),
            CrudOperationKey.of(CommandOperation.UPDATE), "default", null), spec));
    }

    @Test
    void 声明时拒绝未知字段类型错误及非法入口() {
        assertThrows(IllegalArgumentException.class, () -> contributor("missing", true));
        assertThrows(IllegalArgumentException.class, () -> contributor("active", "yes"));
        assertThrows(IllegalArgumentException.class, () -> contributor("status", "UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> contributor("id", "1.5"));
        assertThrows(IllegalArgumentException.class, () -> contributor("active", null));
        Map<String, Map<String, Object>> invalid = new LinkedHashMap<>();
        invalid.put(" consumer ", Collections.singletonMap("active", true));
        assertThrows(IllegalArgumentException.class, () -> new ReadVisibilityContributor(registry,
            Collections.singletonMap(Product.class, invalid)));
        invalid.clear();
        invalid.put("consumer", null);
        assertThrows(IllegalArgumentException.class, () -> new ReadVisibilityContributor(registry,
            Collections.singletonMap(Product.class, invalid)));
        ReadVisibilityContributor noBinding = new ReadVisibilityContributor(registry,
            Collections.singletonMap(Product.class, Collections.emptyMap()));
        assertThrows(DataScopeDeniedException.class,
            () -> noBinding.contribute(action("consumer"), null, spec, CrudDataScope.allowAll()));
    }

    @Test
    void 集合枚举条件在启动期校验并冻结() {
        ReadVisibilityContributor contributor = contributor("status", Arrays.asList("PUBLISHED", "DRAFT"));
        CrudDataScope scope = contributor.contribute(action("consumer"), null, spec, CrudDataScope.allowAll());
        assertEquals(Arrays.asList("PUBLISHED", "DRAFT"), scope.readScope(Product.class).getConditions().get("status"));
        assertThrows(UnsupportedOperationException.class, () -> scope.getReadScopes().clear());
        assertThrows(UnsupportedOperationException.class, () -> scope.readScope(Product.class).getConditions().clear());
    }

    private ReadVisibilityContributor contributor(String field, Object value) {
        Map<String, Map<String, Object>> entries = new LinkedHashMap<>();
        entries.put("consumer", Collections.singletonMap(field, value));
        entries.put("admin", Collections.emptyMap());
        return new ReadVisibilityContributor(registry, Collections.singletonMap(Product.class, entries));
    }

    private CrudResourceAction action(String entry) {
        return new CrudResourceAction(registry.getResourceDescriptor(Product.class), CrudOperationKey.of(QueryOperation.PAGE),
            "default", null, entry, "http", false);
    }

    @EntCrudEntity
    static class Product {
        Long id;
        Boolean active;
        Status status;
    }

    @EntCrudEntity
    static class Customer {
        Long id;
        String name;
    }

    enum Status {
        /** 草稿。 */
        DRAFT,
        /** 已发布。 */
        PUBLISHED
    }
}
