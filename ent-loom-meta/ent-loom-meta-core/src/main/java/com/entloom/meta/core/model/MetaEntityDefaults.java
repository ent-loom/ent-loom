package com.entloom.meta.core.model;

import com.entloom.meta.enums.EntIdPolicy;
/**
 * Meta 实体默认值。
 *
 * <p>默认值只在实体注解没有显式声明时生效。</p>
 */
public final class MetaEntityDefaults {
    private final String service;
    private final EntIdPolicy idPolicy;

    public MetaEntityDefaults() {
        this(null);
    }

    public MetaEntityDefaults(String service) {
        this(service, EntIdPolicy.UNSET);
    }

    public MetaEntityDefaults(String service, EntIdPolicy idPolicy) {
        this.service = normalize(service);
        this.idPolicy = idPolicy == null ? EntIdPolicy.UNSET : idPolicy;
    }

    public String service() {
        return service;
    }

    public EntIdPolicy idPolicy() {
        return idPolicy;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
