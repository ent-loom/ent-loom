package com.entloom.crud.api.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrudRecordTest {
    @Test
    void copyOf_should_create_read_only_value_object() {
        Map<String, Object> nested = new LinkedHashMap<String, Object>();
        nested.put("flag", 1);

        Map<String, Object> source = new LinkedHashMap<String, Object>();
        source.put("id", 1L);
        source.put("active", "true");
        source.put("detail", nested);
        source.put("items", Arrays.asList(Collections.singletonMap("code", "A")));

        CrudRecord record = CrudRecord.copyOf(source);

        assertEquals(Long.valueOf(1L), record.requiredLong("id"));
        assertTrue(record.requiredBoolean("active"));
        assertNotNull(record.requiredRecord("detail"));
        assertEquals(Boolean.TRUE, record.requiredRecord("detail").get("flag", Boolean.class));
        assertEquals("A", record.records("items").get(0).requiredString("code"));
        assertThrows(UnsupportedOperationException.class, () -> record.asMap().put("id", 2L));
        assertThrows(UnsupportedOperationException.class, () -> record.records("items").add(new CrudRecord()));
    }

    @Test
    void equals_should_follow_value_semantics() {
        CrudRecord left = CrudRecord.copyOf(Collections.<String, Object>singletonMap("id", 1));
        CrudRecord right = CrudRecord.copyOf(Collections.<String, Object>singletonMap("id", 1));

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertFalse(left.isEmpty());
    }
}
