package com.entloom.crud.runtime.adapter;

import com.entloom.crud.api.model.SubjectContext;

/**
 * CRUD 主体上下文与 runtime 主体上下文之间的最小映射。
 *
 * <p>CRUD 当前没有主体类型，适配层通过构造参数提供默认值；该类不负责认证、授权或上下文传播。</p>
 */
public final class RuntimeSubjectContextMapper {
    private final String defaultSubjectType;

    public RuntimeSubjectContextMapper() {
        this("user");
    }

    public RuntimeSubjectContextMapper(String defaultSubjectType) {
        if (defaultSubjectType == null || defaultSubjectType.trim().isEmpty()) {
            throw new IllegalArgumentException("defaultSubjectType 不能为空");
        }
        this.defaultSubjectType = defaultSubjectType.trim();
    }

    public com.entloom.runtime.contract.context.SubjectContext toRuntime(SubjectContext source) {
        if (source == null) {
            throw new IllegalArgumentException("CRUD 主体上下文不能为空");
        }
        return com.entloom.runtime.contract.context.SubjectContext.builder()
            .subjectId(source.getSubjectId())
            .subjectType(defaultSubjectType)
            .tenantId(source.getTenantId())
            .orgId(source.getOrgId())
            .build();
    }

    public SubjectContext toCrud(com.entloom.runtime.contract.context.SubjectContext source) {
        if (source == null) {
            throw new IllegalArgumentException("runtime 主体上下文不能为空");
        }
        SubjectContext target = new SubjectContext();
        target.setSubjectId(source.getSubjectId());
        target.setTenantId(source.getTenantId());
        target.setOrgId(source.getOrgId());
        return target;
    }

    public String getDefaultSubjectType() {
        return defaultSubjectType;
    }
}
