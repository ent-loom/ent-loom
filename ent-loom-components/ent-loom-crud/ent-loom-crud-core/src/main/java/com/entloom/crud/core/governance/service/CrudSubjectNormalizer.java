package com.entloom.crud.core.governance.service;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.spec.SpecSnapshotFactory;

/**
 * 主体上下文规范化器。
 */
final class CrudSubjectNormalizer {
    /** 默认租户标识。 */
    static final String DEFAULT_TENANT_ID = "default";

    /**
     * 复制并规范化主体信息，保证不修改外部传入对象。
     */
    SubjectContext normalizeCopy(SubjectContext subject) {
        if (subject == null) {
            throw new ValidationException("操作主体(subject)不能为空");
        }
        SubjectContext normalized = SpecSnapshotFactory.copySubject(subject);
        normalizeInPlace(normalized);
        return normalized;
    }

    private void normalizeInPlace(SubjectContext subject) {
        String normalizedSubjectId = normalizeText(subject.getSubjectId());
        if (normalizedSubjectId == null) {
            throw new ValidationException("操作主体(subject.subjectId)不能为空");
        }
        String normalizedTenantId = normalizeText(subject.getTenantId());
        subject.setSubjectId(normalizedSubjectId);
        subject.setTenantId(normalizedTenantId == null ? DEFAULT_TENANT_ID : normalizedTenantId);
        subject.setOrgId(normalizeText(subject.getOrgId()));
    }

    private String normalizeText(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
