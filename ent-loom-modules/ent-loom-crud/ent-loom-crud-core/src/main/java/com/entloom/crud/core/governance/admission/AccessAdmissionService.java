package com.entloom.crud.core.governance.admission;

import com.entloom.crud.api.access.AuthenticationMode;
import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;

/**
 * 不依赖 HTTP 的认证准入编排，不承担资源授权或凭证算法。
 *
 * <p>先认证再选择公共/主体分支；是否声明上下文参数不应决定接入层是否调用本服务。</p>
 */
public final class AccessAdmissionService {
    private final BusinessIdentityRegistry identityRegistry;

    public AccessAdmissionService(BusinessIdentityRegistry identityRegistry) {
        if (identityRegistry == null) {
            throw new ValidationException("认证准入需要身份注册表");
        }
        this.identityRegistry = identityRegistry;
    }

    /** 业务接口默认要求有效凭证，公开接口必须显式指定其他模式。 */
    public AccessAdmission admit(AccessAuthenticationSource source, String accessEntry, String requestId, String traceId) {
        return admit(AuthenticationMode.REQUIRED, source, accessEntry, requestId, traceId);
    }

    public AccessAdmission admit(
        AuthenticationMode mode,
        AccessAuthenticationSource source,
        String accessEntry,
        String requestId,
        String traceId
    ) {
        if (mode == null || source == null) {
            throw new ValidationException("认证模式与可信来源不能为空");
        }
        CredentialAuthentication authentication = source.authenticate();
        if (authentication == null) {
            throw new PermissionDeniedException("可信来源未返回凭证认证结果");
        }
        if (authentication.getStatus() == CredentialAuthentication.Status.INVALID) {
            throw new PermissionDeniedException("凭证无效或已过期");
        }
        if (authentication.getStatus() == CredentialAuthentication.Status.ABSENT) {
            if (mode == AuthenticationMode.REQUIRED) {
                throw new PermissionDeniedException("当前接口要求有效凭证");
            }
            return new AccessAdmission(mode, VisitorContext.anonymous(accessEntry, requestId, traceId), null);
        }
        if (mode == AuthenticationMode.ANONYMOUS) {
            return new AccessAdmission(mode, VisitorContext.anonymous(accessEntry, requestId, traceId), authentication.getSubject());
        }
        BusinessIdentity identity = authentication.getIdentityCode() == null ? null
            : identityRegistry.resolve(authentication.getSubject(), authentication.getIdentityCode(), authentication.getIdentityId());
        VisitorContext context = VisitorContext.authenticated(authentication.getSubject(), identity, accessEntry, requestId, traceId);
        return new AccessAdmission(mode, context, authentication.getSubject());
    }
}
