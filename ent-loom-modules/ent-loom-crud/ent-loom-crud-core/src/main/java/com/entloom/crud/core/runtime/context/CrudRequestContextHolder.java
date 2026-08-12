package com.entloom.crud.core.runtime.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 当前 crud 调用的服务端请求上下文。
 */
public final class CrudRequestContextHolder {
    private static final ThreadLocal<Map<String, Object>> ATTRIBUTES = new ThreadLocal<Map<String, Object>>();

    private CrudRequestContextHolder() {
    }

    public static <T> T withAttribute(String key, Object value, Supplier<T> supplier) {
        Map<String, Object> attributes = new LinkedHashMap<String, Object>();
        attributes.put(key, value);
        return withAttributes(attributes, supplier);
    }

    public static <T> T withAttributes(Map<String, ?> attributes, Supplier<T> supplier) {
        if (supplier == null) {
            throw new IllegalArgumentException("supplier must not be null");
        }
        Map<String, Object> previous = ATTRIBUTES.get();
        Map<String, Object> next = new LinkedHashMap<String, Object>();
        if (previous != null) {
            next.putAll(previous);
        }
        if (attributes != null) {
            for (Map.Entry<String, ?> entry : attributes.entrySet()) {
                String key = normalizeKey(entry.getKey());
                if (entry.getValue() == null) {
                    next.remove(key);
                } else {
                    next.put(key, entry.getValue());
                }
            }
        }
        bind(next);
        try {
            return supplier.get();
        } finally {
            bind(previous);
        }
    }

    public static Object getAttribute(String key) {
        Map<String, Object> attributes = ATTRIBUTES.get();
        return attributes == null ? null : attributes.get(normalizeKey(key));
    }

    public static String getStringAttribute(String key) {
        Object value = getAttribute(key);
        return value == null ? null : String.valueOf(value);
    }

    public static Map<String, Object> attributes() {
        Map<String, Object> attributes = ATTRIBUTES.get();
        if (attributes == null || attributes.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<String, Object>(attributes));
    }

    public static void clear() {
        ATTRIBUTES.remove();
    }

    private static void bind(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            ATTRIBUTES.remove();
        } else {
            ATTRIBUTES.set(new LinkedHashMap<String, Object>(attributes));
        }
    }

    private static String normalizeKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("context attribute key must not be blank");
        }
        return key.trim();
    }
}
