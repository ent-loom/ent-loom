package com.entloom.crud.core.idempotency;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StablePayloadCanonicalizerTest {
    private final StablePayloadCanonicalizer canonicalizer = new StablePayloadCanonicalizer();

    @Test
    void should_sort_map_keys_for_stable_output() {
        Map<String, Object> first = new LinkedHashMap<String, Object>();
        first.put("b", 2);
        first.put("a", 1);
        Map<String, Object> second = new LinkedHashMap<String, Object>();
        second.put("a", 1);
        second.put("b", 2);

        Assertions.assertEquals(canonicalizer.canonicalize(first), canonicalizer.canonicalize(second));
        Assertions.assertEquals("{\"a\":1,\"b\":2}", canonicalizer.canonicalize(first));
    }

    @Test
    void should_sort_bean_properties_and_keep_collection_order() {
        PayloadBean bean = new PayloadBean();
        bean.setName("order");
        bean.setItems(Arrays.asList("first", "second"));

        String canonical = canonicalizer.canonicalize(bean);

        Assertions.assertEquals("{\"items\":[\"first\",\"second\"],\"name\":\"order\"}", canonical);
    }

    @Test
    void should_escape_string_values() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("text", "a\"b\nc\\d");

        Assertions.assertEquals("{\"text\":\"a\\\"b\\nc\\\\d\"}", canonicalizer.canonicalize(payload));
    }

    @Test
    void should_reject_cycle_references() {
        List<Object> list = new ArrayList<Object>();
        list.add("root");
        list.add(list);

        Assertions.assertThrows(IllegalArgumentException.class, () -> canonicalizer.canonicalize(list));
    }

    public static final class PayloadBean {
        private String name;
        private List<String> items;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getItems() {
            return items;
        }

        public void setItems(List<String> items) {
            this.items = items;
        }
    }
}
