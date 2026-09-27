package com.entloom.crud.core.runtime.model;

import com.entloom.crud.api.enums.CrudIdPolicy;

/**
 * CRUD 实体默认主键策略。
 *
 * <p>默认值保持为 {@link CrudIdPolicy#UNSET}，由解析器继续执行已有的字段元数据推断。
 * 应用可以在运行时覆盖该默认值，例如通过 Spring Boot 配置。</p>
 */
public final class CrudIdPolicyDefaults {
    private final CrudIdPolicy idPolicy;

    public CrudIdPolicyDefaults() {
        this(CrudIdPolicy.UNSET);
    }

    public CrudIdPolicyDefaults(CrudIdPolicy idPolicy) {
        this.idPolicy = idPolicy == null ? CrudIdPolicy.UNSET : idPolicy;
    }

    public CrudIdPolicy idPolicy() {
        return idPolicy;
    }
}
