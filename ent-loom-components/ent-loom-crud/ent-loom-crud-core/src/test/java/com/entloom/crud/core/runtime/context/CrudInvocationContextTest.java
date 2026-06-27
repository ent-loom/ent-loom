package com.entloom.crud.core.runtime.context;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CrudInvocationContextTest {

    @Test
    void builder_should_create_immutable_attribute_snapshot() {
        Map<String, Object> source = new LinkedHashMap<String, Object>();
        source.put(" accessEntry ", "custom");
        CrudInvocationContext context = CrudInvocationContext.builder()
            .attributes(source)
            .build();

        source.put("accessEntry", "changed");

        Assertions.assertEquals("custom", context.getAttributes().get("accessEntry"));
        Assertions.assertThrows(UnsupportedOperationException.class, () ->
            context.getAttributes().put("other", "value")
        );
    }

    @Test
    void empty_context_should_be_reused() {
        Assertions.assertSame(CrudInvocationContext.empty(), CrudInvocationContext.builder().build());
    }
}
