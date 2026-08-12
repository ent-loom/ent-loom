package com.entloom.crud.core.runtime.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次 crud 调用的服务端上下文。
 */
public final class CrudInvocationContext {
    private static final CrudInvocationContext EMPTY = new CrudInvocationContext(Collections.<String, Object>emptyMap());

    private final Map<String, Object> attributes;

    private CrudInvocationContext(Map<String, Object> attributes) {
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(attributes));
    }

    public static CrudInvocationContext empty() {
        return EMPTY;
    }

    public static CrudInvocationContext ofAttribute(String key, Object value) {
        return builder().attribute(key, value).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public static final class Builder {
        private final Map<String, Object> attributes = new LinkedHashMap<String, Object>();

        private Builder() {
        }

        public Builder attribute(String key, Object value) {
            String normalizedKey = normalizeKey(key);
            if (value == null) {
                attributes.remove(normalizedKey);
            } else {
                attributes.put(normalizedKey, value);
            }
            return this;
        }

        public Builder attributes(Map<String, ?> values) {
            if (values == null) {
                return this;
            }
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                attribute(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public CrudInvocationContext build() {
            if (attributes.isEmpty()) {
                return CrudInvocationContext.empty();
            }
            return new CrudInvocationContext(attributes);
        }

        private static String normalizeKey(String key) {
            if (key == null || key.trim().isEmpty()) {
                throw new IllegalArgumentException("context attribute key must not be blank");
            }
            return key.trim();
        }
    }
}
