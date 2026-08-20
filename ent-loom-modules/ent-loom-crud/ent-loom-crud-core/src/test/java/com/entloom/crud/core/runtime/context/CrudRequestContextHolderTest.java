package com.entloom.crud.core.runtime.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CrudRequestContextHolderTest {

    @AfterEach
    void tearDown() {
        CrudRequestContextHolder.clear();
    }

    @Test
    void with_attribute_should_bind_and_restore_context() {
        String value = CrudRequestContextHolder.withAttribute(" accessEntry ", "custom", () ->
            CrudRequestContextHolder.getStringAttribute("accessEntry")
        );

        Assertions.assertEquals("custom", value);
        Assertions.assertTrue(CrudRequestContextHolder.attributes().isEmpty());
    }

    @Test
    void with_attributes_should_restore_previous_context_after_exception() {
        String tenant = CrudRequestContextHolder.withAttribute("tenant", "tenant-a", () -> {
            Assertions.assertThrows(IllegalStateException.class, () ->
                CrudRequestContextHolder.withAttribute("accessEntry", "custom", () -> {
                    throw new IllegalStateException("boom");
                })
            );
            return CrudRequestContextHolder.getStringAttribute("tenant");
        });

        Assertions.assertEquals("tenant-a", tenant);
        Assertions.assertTrue(CrudRequestContextHolder.attributes().isEmpty());
    }

    @Test
    void null_attribute_value_should_remove_key_in_nested_context() {
        Map<String, Object> attributes = new LinkedHashMap<String, Object>();
        attributes.put("tenant", "tenant-a");
        attributes.put("entry", "base");

        Map<String, Object> nested = CrudRequestContextHolder.withAttributes(attributes, () -> {
            Assertions.assertEquals("base", CrudRequestContextHolder.getStringAttribute("entry"));
            return CrudRequestContextHolder.withAttribute("entry", null, () ->
                CrudRequestContextHolder.attributes()
            );
        });

        Assertions.assertEquals(Collections.singletonMap("tenant", "tenant-a"), nested);
        Assertions.assertTrue(CrudRequestContextHolder.attributes().isEmpty());
    }

    @Test
    void request_context_should_not_follow_thread_pool_reuse_implicitly() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            String explicit = executor.submit(() -> CrudRequestContextHolder.withAttribute(
                "accessEntry",
                "explicit",
                () -> CrudRequestContextHolder.getStringAttribute("accessEntry")
            )).get();
            String leaked = executor.submit(() -> CrudRequestContextHolder.getStringAttribute("accessEntry")).get();

            Assertions.assertEquals("explicit", explicit);
            Assertions.assertNull(leaked);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            CrudRequestContextHolder.clear();
        }
    }
}
