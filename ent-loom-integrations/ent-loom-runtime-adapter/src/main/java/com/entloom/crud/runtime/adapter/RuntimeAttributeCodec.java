package com.entloom.crud.runtime.adapter;

import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 适配器边界上的标量属性编码工具。 */
final class RuntimeAttributeCodec {
    private RuntimeAttributeCodec() {
    }

    static Map<String, String> toRuntimeAttributes(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> target = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (!isScalar(entry.getValue())) {
                throw new IllegalArgumentException("仅支持标量运行时属性: " + entry.getKey());
            }
            target.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return target;
    }

    static Map<String, Object> toCrudAttributes(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return new LinkedHashMap<String, Object>(source);
    }

    private static boolean isScalar(Object value) {
        return value instanceof CharSequence
            || value instanceof Number
            || value instanceof Boolean
            || value instanceof Character
            || value instanceof Enum<?>
            || value instanceof TemporalAccessor;
    }
}
