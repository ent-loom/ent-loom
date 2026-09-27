package com.entloom.meta.core.model;

/**
 * Meta 实体默认值。
 *
 * <p>默认值只在实体注解没有显式声明时生效。</p>
 */
public final class MetaEntityDefaults {
    private final String service;

    public MetaEntityDefaults() {
        this(null);
    }

    public MetaEntityDefaults(String service) {
        this.service = normalize(service);
    }

    public String service() {
        return service;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
