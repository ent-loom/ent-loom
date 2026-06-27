package com.entloom.crud.core.idempotency;

import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.exception.IdempotencyPayloadConflictException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class IdempotencyManagerTest {
    @Test
    void should_store_successful_result_and_replay_without_invoking_supplier_again() {
        IdempotencyManager manager = new IdempotencyManager(new InMemoryIdempotencyStore());
        AtomicInteger calls = new AtomicInteger();
        Map<String, Object> payload = payload("orderNo", "ORD-1");

        CommandResult<String> first = manager.executeWithIdempotency("key-1", payload, () -> {
            calls.incrementAndGet();
            return CommandResult.success("created");
        });
        CommandResult<String> replay = manager.executeWithIdempotency("key-1", payload, () -> {
            calls.incrementAndGet();
            return CommandResult.success("should-not-run");
        });

        Assertions.assertEquals(1, calls.get());
        Assertions.assertFalse(first.isIdempotentReplay());
        Assertions.assertTrue(replay.isIdempotentReplay());
        Assertions.assertEquals("created", replay.getData());
        Assertions.assertNotSame(first, replay);
    }

    @Test
    void should_reject_same_key_with_different_payload() {
        IdempotencyManager manager = new IdempotencyManager(new InMemoryIdempotencyStore());

        manager.executeWithIdempotency("key-2", payload("orderNo", "ORD-1"), () -> "ok");

        Assertions.assertThrows(
            IdempotencyPayloadConflictException.class,
            () -> manager.executeWithIdempotency("key-2", payload("orderNo", "ORD-2"), () -> "conflict")
        );
    }

    @Test
    void should_release_key_when_supplier_fails_so_request_can_retry() {
        IdempotencyManager manager = new IdempotencyManager(new InMemoryIdempotencyStore());
        AtomicInteger calls = new AtomicInteger();
        Map<String, Object> payload = payload("orderNo", "ORD-1");

        Assertions.assertThrows(IllegalStateException.class, () ->
            manager.executeWithIdempotency("key-3", payload, () -> {
                calls.incrementAndGet();
                throw new IllegalStateException("boom");
            })
        );
        String result = manager.executeWithIdempotency("key-3", payload, () -> {
            calls.incrementAndGet();
            return "retried";
        });

        Assertions.assertEquals("retried", result);
        Assertions.assertEquals(2, calls.get());
    }

    @Test
    void should_normalize_scene_when_building_storage_key() {
        IdempotencyManager manager = new IdempotencyManager(new InMemoryIdempotencyStore());

        String key = manager.buildStorageKey("tenant-a", "Order|CREATE", " Order.Place ", "idem-1");

        Assertions.assertEquals("tenant-a|Order|CREATE|order.place|idem-1", key);
    }

    @Test
    void should_reacquire_expired_processing_record() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(1L);
        IdempotencyManager manager = new IdempotencyManager(store);
        AtomicInteger calls = new AtomicInteger();

        Assertions.assertThrows(IllegalStateException.class, () ->
            manager.executeWithIdempotency("key-4", Collections.singletonMap("id", 1), () -> {
                calls.incrementAndGet();
                throw new IllegalStateException("transient");
            })
        );
        Thread.sleep(3L);
        String result = manager.executeWithIdempotency("key-4", Collections.singletonMap("id", 1), () -> {
            calls.incrementAndGet();
            return "ok";
        });

        Assertions.assertEquals("ok", result);
        Assertions.assertEquals(2, calls.get());
    }

    private Map<String, Object> payload(String key, Object value) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put(key, value);
        return payload;
    }
}
