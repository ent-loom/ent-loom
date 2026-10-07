package com.entloom.crud.core.governance.admission;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.core.exception.ValidationException;

/**
 * 可信来源的不可变凭证认证结果，不保存凭证原文。
 *
 * <p>工厂只校验结构，不验证凭证。身份 code/ID 是待验证的当前选择，不是已获授业务身份。</p>
 */
public final class CredentialAuthentication {
    public enum Status {
        /** 未提供凭证。 */
        ABSENT,
        /** 凭证有效，已确认实际主体。 */
        AUTHENTICATED,
        /** 凭证无效或过期，不允许匿名降级。 */
        INVALID
    }

    private static final CredentialAuthentication ABSENT = new CredentialAuthentication(Status.ABSENT, null, null, null);
    private static final CredentialAuthentication INVALID = new CredentialAuthentication(Status.INVALID, null, null, null);

    private final Status status;
    private final AccessSubject subject;
    private final String identityCode;
    private final String identityId;

    private CredentialAuthentication(Status status, AccessSubject subject, String identityCode, String identityId) {
        this.status = status;
        this.subject = subject;
        this.identityCode = identityCode;
        this.identityId = identityId;
    }

    public static CredentialAuthentication absent() {
        return ABSENT;
    }

    public static CredentialAuthentication invalid() {
        return INVALID;
    }

    public static CredentialAuthentication authenticated(AccessSubject subject) {
        return authenticated(subject, null, null);
    }

    public static CredentialAuthentication authenticated(AccessSubject subject, String identityCode, String identityId) {
        if (subject == null) {
            throw new ValidationException("凭证认证成功必须提供实际主体");
        }
        if (identityCode != null || identityId != null) {
            requireCoordinate(identityCode, "code");
            requireCoordinate(identityId, "ID");
        }
        return new CredentialAuthentication(Status.AUTHENTICATED, subject, identityCode, identityId);
    }

    public Status getStatus() {
        return status;
    }

    public AccessSubject getSubject() {
        return subject;
    }

    public String getIdentityCode() {
        return identityCode;
    }

    public String getIdentityId() {
        return identityId;
    }

    private static void requireCoordinate(String value, String name) {
        if (value == null || value.trim().isEmpty() || !value.equals(value.trim())) {
            throw new ValidationException("凭证认证结果的身份选择 " + name + " 不能为空或包含首尾空格");
        }
    }
}
