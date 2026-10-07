package com.entloom.crud.api.access;

import java.util.Objects;

/** 不可变的已认证主体；组织归属及业务身份不属于主体标识。 */
public final class AccessSubject {
    private final String subjectId;
    private final AccessSubjectType type;

    public AccessSubject(String subjectId, AccessSubjectType type) {
        if (subjectId == null || subjectId.trim().isEmpty()) {
            throw new IllegalArgumentException("subjectId 不能为空");
        }
        this.subjectId = subjectId.trim();
        this.type = Objects.requireNonNull(type, "主体来源类型不能为空");
    }

    public String getSubjectId() {
        return subjectId;
    }

    public AccessSubjectType getType() {
        return type;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AccessSubject)) {
            return false;
        }
        AccessSubject subject = (AccessSubject) other;
        return subjectId.equals(subject.subjectId) && type == subject.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectId, type);
    }
}
