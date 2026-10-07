package com.entloom.crud.core.governance.admission;

import com.entloom.crud.api.access.AccessBranch;
import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.access.AuthenticationMode;
import com.entloom.crud.core.exception.PermissionDeniedException;
import java.util.Objects;

/**
 * 进程内任务的身份坐标快照，不保存凭证、身份对象或授权结果。
 * 恢复时必须由任务执行方提供重新认证的可信来源；延后业务操作仍需重新授权。
 */
public final class AccessTaskSnapshot {
    private final AuthenticationMode mode;
    private final AccessBranch branch;
    private final AccessSubject initiator;
    private final String identityCode;
    private final String identityId;
    private final String accessEntry;
    private final String requestId;
    private final String traceId;

    private AccessTaskSnapshot(AccessAdmission admission) {
        VisitorContext visitor = admission.getVisitorContext();
        BusinessIdentity identity = visitor.getIdentity().orElse(null);
        this.mode = admission.getAuthenticationMode();
        this.branch = admission.getBranch();
        this.initiator = admission.getAuthenticatedSubject().orElse(null);
        this.identityCode = identity == null ? null : identity.getIdentityCode();
        this.identityId = identity == null ? null : identity.getIdentityId();
        this.accessEntry = visitor.getAccessEntry();
        this.requestId = visitor.getRequestId();
        this.traceId = visitor.getTraceId();
    }

    public static AccessTaskSnapshot capture(AccessAdmission admission) {
        return new AccessTaskSnapshot(Objects.requireNonNull(admission, "准入结果不能为空"));
    }

    /** 重新认证和解析当前身份，拒绝发起者、分支或身份坐标发生变化的任务。 */
    public AccessAdmission restore(AccessAdmissionService service, AccessAuthenticationSource source) {
        Objects.requireNonNull(service, "准入服务不能为空");
        Objects.requireNonNull(source, "可信来源不能为空");
        AccessAdmission current = service.admit(mode, source, accessEntry, requestId, traceId);
        if (current.getBranch() != branch
            || !Objects.equals(current.getAuthenticatedSubject().orElse(null), initiator)) {
            throw new PermissionDeniedException("任务发起者或准入分支已变化");
        }
        BusinessIdentity identity = current.getVisitorContext().getIdentity().orElse(null);
        if (!Objects.equals(identity == null ? null : identity.getIdentityCode(), identityCode)
            || !Objects.equals(identity == null ? null : identity.getIdentityId(), identityId)) {
            throw new PermissionDeniedException("任务当前身份已变化");
        }
        return current;
    }

    public AccessSubject getInitiator() {
        return initiator;
    }

    public AccessBranch getBranch() {
        return branch;
    }

    public String getIdentityCode() {
        return identityCode;
    }

    public String getIdentityId() {
        return identityId;
    }
}
