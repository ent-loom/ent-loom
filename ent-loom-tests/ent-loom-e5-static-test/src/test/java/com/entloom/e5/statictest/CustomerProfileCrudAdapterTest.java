package com.entloom.e5.statictest;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.core.capability.command.gateway.CommandGateway;
import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.capability.query.gateway.QueryGateway;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.e5.statictest.fixture.CustomerProfile;
import com.entloom.e5.statictest.fixture.CustomerProfileCreateRequest;
import com.entloom.e5.statictest.fixture.CustomerProfileCrudAdapter;
import com.entloom.e5.statictest.fixture.CustomerProfileQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CustomerProfileCrudAdapterTest {
    @Test
    void should_normalize_typed_query_create_and_patch_before_gateway() {
        CapturingQueryGateway queryGateway = new CapturingQueryGateway();
        CapturingCommandGateway commandGateway = new CapturingCommandGateway();
        CustomerProfileCrudAdapter adapter = new CustomerProfileCrudAdapter(queryGateway, commandGateway, metaRegistry());

        adapter.page(new CustomerProfileQuery(" 张 ", 1, 20));
        adapter.create(new CustomerProfileCreateRequest("张三", new BigDecimal("100.00"), LocalDateTime.of(2026, 1, 1, 0, 0), null));

        Assertions.assertEquals(CustomerProfile.class, queryGateway.spec.getResultType());
        Assertions.assertEquals("张", queryGateway.spec.getFilters().get(0).getValue());
        WriteCommand<?> create = (WriteCommand<?>) commandGateway.spec.getPayload();
        Assertions.assertTrue(create.getValues() instanceof Map<?, ?>);
        Assertions.assertFalse(((Map<?, ?>) create.getValues()).containsKey("id"));

        adapter.update(patch());
        WriteCommand<?> update = (WriteCommand<?>) commandGateway.spec.getPayload();
        Assertions.assertEquals(7L, update.getId());
        Assertions.assertTrue(((Map<?, ?>) update.getValues()).containsKey("avatarUrl"));
        Assertions.assertNull(((Map<?, ?>) update.getValues()).get("avatarUrl"));
    }

    private EntityMetaRegistry metaRegistry() {
        return new CrudRuntimeModelBackedEntityMetaRegistry(
            new CrudNativeRuntimeModelParser().parse(Collections.<Class<?>>singletonList(CustomerProfile.class))
        );
    }

    private UpdatePatch<CustomerProfile> patch() {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("avatarUrl", null);
        return new UpdatePatch<CustomerProfile>() {
            public Class<CustomerProfile> getEntityType() { return CustomerProfile.class; }
            public CustomerProfile getEntity() { return null; }
            public Object getId() { return 7L; }
            public Long getLongId() { return 7L; }
            public Set<String> getPresentFields() { return Collections.singleton("avatarUrl"); }
            public Set<String> getPersistableFields() { return Collections.singleton("avatarUrl"); }
            public Map<String, Object> getValuesForDelegate() { return values; }
            @SuppressWarnings("unchecked") public <V> V get(String field) { return (V) values.get(field); }
            public <V> V get(String field, Class<V> targetType) { return targetType.cast(values.get(field)); }
        };
    }

    private static final class CapturingQueryGateway implements QueryGateway {
        private QuerySpec<CustomerProfile> spec;
        @SuppressWarnings("unchecked") public <R> PageResult<R> page(QuerySpec<R> spec) { this.spec = (QuerySpec<CustomerProfile>) spec; return new PageResult<R>(); }
        public <R> List<R> list(QuerySpec<R> spec) { throw new UnsupportedOperationException(); }
        public <R> R findOne(QuerySpec<R> spec) { throw new UnsupportedOperationException(); }
        public <R> R detail(QuerySpec<R> spec) { throw new UnsupportedOperationException(); }
    }

    private static final class CapturingCommandGateway implements CommandGateway {
        private CommandSpec<?> spec;
        @SuppressWarnings("unchecked") public <P, R> R action(CommandSpec<P> spec) { this.spec = spec; return (R) Long.valueOf(1L); }
    }
}
