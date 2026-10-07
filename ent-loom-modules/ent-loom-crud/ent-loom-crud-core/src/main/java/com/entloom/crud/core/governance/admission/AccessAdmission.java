package com.entloom.crud.core.governance.admission;

import com.entloom.crud.api.access.AccessBranch;
import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AuthenticationMode;
import com.entloom.crud.api.access.VisitorContext;
import java.util.Optional;

/** 不可变准入结果；调用方仍须执行业务操作授权，不能将其当作授权凭证。 */
public final class AccessAdmission {
    private final AuthenticationMode authenticationMode;
    private final VisitorContext visitorContext;
    private final AccessSubject authenticatedSubject;

    AccessAdmission(AuthenticationMode authenticationMode, VisitorContext visitorContext, AccessSubject authenticatedSubject) {
        this.authenticationMode = authenticationMode;
        this.visitorContext = visitorContext;
        this.authenticatedSubject = authenticatedSubject;
    }

    public AuthenticationMode getAuthenticationMode() {
        return authenticationMode;
    }

    public AccessBranch getBranch() {
        return visitorContext.isAnonymous() ? AccessBranch.PUBLIC : AccessBranch.AUTHENTICATED;
    }

    /** 有效授权上下文；公共分支不含已认证主体或个性化身份。 */
    public VisitorContext getVisitorContext() {
        return visitorContext;
    }

    /** 实际认证主体仅供审计；不得将其重新合入公共分支的授权上下文。 */
    public Optional<AccessSubject> getAuthenticatedSubject() {
        return Optional.ofNullable(authenticatedSubject);
    }
}
