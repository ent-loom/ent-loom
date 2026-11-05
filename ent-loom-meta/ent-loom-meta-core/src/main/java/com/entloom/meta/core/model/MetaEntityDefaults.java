package com.entloom.meta.core.model;

import com.entloom.meta.contract.convention.CreatedTimeFields;
import com.entloom.meta.enums.EntIdPolicy;

/**
 * Meta 全局默认值与字段命名约定。
 *
 * <p>默认约定不覆盖显式注解和更高优先级的项目约定。</p>
 */
public final class MetaEntityDefaults {
    private final String service;
    private final EntIdPolicy idPolicy;
    private final CreatedTimeFields createdTimeFields;

    public MetaEntityDefaults() {
        this(null);
    }

    public MetaEntityDefaults(String service) {
        this(service, EntIdPolicy.UNSET);
    }

    public MetaEntityDefaults(String service, EntIdPolicy idPolicy) {
        this(service, idPolicy, new CreatedTimeFields());
    }

    public MetaEntityDefaults(String service, EntIdPolicy idPolicy, CreatedTimeFields createdTimeFields) {
        this.service = normalize(service);
        this.idPolicy = idPolicy == null ? EntIdPolicy.UNSET : idPolicy;
        this.createdTimeFields = java.util.Objects.requireNonNull(createdTimeFields, "创建时间字段约定不能为空");
    }

    public CreatedTimeFields createdTimeFields() {
        return createdTimeFields;
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
