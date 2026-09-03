package com.entloom.crud.runtime.adapter;

import java.time.MonthDay;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeAttributeCodecTest {
    @Test
    void preservesNullAttributeValues() {
        Map<String, Object> decoded = RuntimeAttributeCodec.toCrudAttributes(
            RuntimeAttributeCodec.toRuntimeAttributes(Collections.<String, Object>singletonMap("optional", null))
        );

        assertEquals(true, decoded.containsKey("optional"));
        assertEquals(null, decoded.get("optional"));
    }

    @Test
    void rejectsTemporalTypesThatCannotBeDecoded() {
        assertThrows(IllegalArgumentException.class,
            () -> RuntimeAttributeCodec.encode(MonthDay.of(9, 1)));
    }
}
