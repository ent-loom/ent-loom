package com.entloom.crud.core.adapter;

import com.entloom.crud.core.runtime.spec.BaseSpec;
import java.util.Locale;

/**
 * 基于 Spec attributes 的访问入口解析器。
 */
public class AttributeAccessEntryResolver implements AccessEntryResolver {
    private final String attributeKey;
    private final String defaultEntry;

    public AttributeAccessEntryResolver() {
        this(ATTRIBUTE_KEY, DEFAULT_ENTRY);
    }

    public AttributeAccessEntryResolver(String attributeKey, String defaultEntry) {
        this.attributeKey = normalizeKey(attributeKey);
        this.defaultEntry = normalizeEntry(defaultEntry, DEFAULT_ENTRY);
    }

    @Override
    public String resolveAccessEntry(BaseSpec spec) {
        Object raw = spec == null ? null : spec.getAttributes().get(attributeKey);
        if (raw == null && spec != null && !ATTRIBUTE_KEY.equals(attributeKey)) {
            raw = spec.getAttributes().get(ATTRIBUTE_KEY);
        }
        return normalizeEntry(raw == null ? null : String.valueOf(raw), defaultEntry);
    }

    private String normalizeKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return ATTRIBUTE_KEY;
        }
        return key.trim();
    }

    private String normalizeEntry(String entry, String fallback) {
        if (entry == null || entry.trim().isEmpty()) {
            return fallback;
        }
        return entry.trim().toLowerCase(Locale.ROOT);
    }
}
