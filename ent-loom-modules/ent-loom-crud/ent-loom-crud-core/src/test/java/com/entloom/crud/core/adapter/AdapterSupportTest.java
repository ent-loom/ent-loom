package com.entloom.crud.core.adapter;

import com.entloom.crud.core.runtime.context.CrudRequestContextHolder;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AdapterSupportTest {
    @Test
    void attribute_access_entry_resolver_should_normalize_and_fallback() {
        Map<String, Object> attributes = new LinkedHashMap<String, Object>();
        attributes.put(AccessEntryResolver.ATTRIBUTE_KEY, " Moral ");
        com.entloom.crud.core.capability.query.spec.QuerySpec<Object> spec = com.entloom.crud.core.capability.query.spec.QuerySpec.<Object>builder()
            .rootType(TestOrder.class)
            .attributes(attributes)
            .build();

        Assertions.assertEquals("moral", new AttributeAccessEntryResolver().resolveAccessEntry(spec));
        Assertions.assertEquals("base", new AttributeAccessEntryResolver().resolveAccessEntry(null));
    }

    @Test
    void context_access_entry_contributor_should_copy_server_context_only() {
        ContextAccessEntryAttributeContributor contributor = new ContextAccessEntryAttributeContributor();

        Map<String, Object> attributes = CrudRequestContextHolder.withAttribute(
            AccessEntryResolver.ATTRIBUTE_KEY,
            "moral",
            () -> contributor.contribute(null)
        );

        Assertions.assertEquals("moral", attributes.get(AccessEntryResolver.ATTRIBUTE_KEY));
        Assertions.assertFalse(contributor.contribute(null).containsKey(AccessEntryResolver.ATTRIBUTE_KEY));
    }

    private static final class TestOrder {
    }
}
