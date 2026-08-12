package com.entloom.crud.core.governance.audit;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import lombok.Getter;

/**
 * 治理审计事件。
 */
@Getter
public class CrudGovernanceAuditEvent {
    /** 请求主体。 */
    private final SubjectContext subject;
    /** 命令动作实现。 */
    private final CrudResourceAction action;
    /** 访问决策。 */
    private final AccessDecision accessDecision;
    /** 已授予的数据范围。 */
    private final CrudDataScope grantedScope;
    /** 治理计算出的数据范围。 */
    private final CrudDataScope governanceScope;
    /** 审计结果。 */
    private final CrudGovernanceAuditOutcome outcome;
    /** 原因编码。 */
    private final CrudGovernanceAuditReasonCode reason;
    /** 耗时（毫秒）。 */
    private final long costMs;
    /** 失败阶段。 */
    private final CrudErrorStage stage;
    /** 路由 key。 */
    private final String routeKey;
    /** 请求 ID。 */
    private final String requestId;
    /** 链路 ID。 */
    private final String traceId;

    public CrudGovernanceAuditEvent(
        SubjectContext subject,
        CrudResourceAction action,
        AccessDecision accessDecision,
        CrudDataScope grantedScope,
        CrudDataScope governanceScope,
        CrudGovernanceAuditOutcome outcome,
        CrudGovernanceAuditReasonCode reason,
        long costMs
    ) {
        this(subject, action, accessDecision, grantedScope, governanceScope, outcome, reason, costMs, null, null, null, null);
    }

    public CrudGovernanceAuditEvent(
        SubjectContext subject,
        CrudResourceAction action,
        AccessDecision accessDecision,
        CrudDataScope grantedScope,
        CrudDataScope governanceScope,
        CrudGovernanceAuditOutcome outcome,
        CrudGovernanceAuditReasonCode reason,
        long costMs,
        CrudErrorStage stage,
        String routeKey,
        String requestId,
        String traceId
    ) {
        this.subject = subject;
        this.action = action;
        this.accessDecision = accessDecision == null ? AccessDecision.ALLOW : accessDecision;
        this.grantedScope = grantedScope;
        this.governanceScope = governanceScope;
        this.outcome = outcome == null ? CrudGovernanceAuditOutcome.SUCCESS : outcome;
        this.reason = reason == null ? CrudGovernanceAuditReasonCode.NONE : reason;
        this.costMs = costMs;
        this.stage = stage;
        this.routeKey = routeKey;
        this.requestId = requestId;
        this.traceId = traceId;
    }

    public static CrudGovernanceAuditEvent of(
        SubjectContext subject,
        CrudResourceAction action,
        AccessDecision accessDecision,
        CrudDataScope grantedScope,
        CrudDataScope governanceScope,
        CrudGovernanceAuditOutcome outcome,
        CrudGovernanceAuditReasonCode reason,
        long costMs
    ) {
        return of(subject, action, accessDecision, grantedScope, governanceScope, outcome, reason, costMs, null, null, null, null);
    }

    public static CrudGovernanceAuditEvent of(
        SubjectContext subject,
        CrudResourceAction action,
        AccessDecision accessDecision,
        CrudDataScope grantedScope,
        CrudDataScope governanceScope,
        CrudGovernanceAuditOutcome outcome,
        CrudGovernanceAuditReasonCode reason,
        long costMs,
        CrudErrorStage stage,
        String routeKey,
        String requestId,
        String traceId
    ) {
        return new CrudGovernanceAuditEvent(
            subject,
            action,
            accessDecision,
            grantedScope,
            governanceScope,
            outcome,
            reason,
            costMs,
            stage,
            routeKey,
            requestId,
            traceId
        );
    }

    public static CrudGovernanceAuditEvent of(
        SubjectContext subject,
        CrudResourceAction action,
        AccessDecision accessDecision,
        CrudDataScope grantedScope,
        CrudDataScope governanceScope,
        String reasonCode,
        long costMs
    ) {
        CrudGovernanceAuditReasonCode reason = CrudGovernanceAuditReasonCode.fromName(reasonCode);
        CrudGovernanceAuditOutcome outcome = deriveOutcome(accessDecision, reason);
        return of(subject, action, accessDecision, grantedScope, governanceScope, outcome, reason, costMs);
    }

    public String getReasonCode() {
        return reason == null ? null : reason.name();
    }

    public boolean isAllowed() {
        return accessDecision != AccessDecision.DENY;
    }

    public boolean isSuccess() {
        return outcome == CrudGovernanceAuditOutcome.SUCCESS;
    }

    private static CrudGovernanceAuditOutcome deriveOutcome(
        AccessDecision accessDecision,
        CrudGovernanceAuditReasonCode reason
    ) {
        if (accessDecision == AccessDecision.DENY) {
            return CrudGovernanceAuditOutcome.GOVERNANCE_DENIED;
        }
        if (reason == null || reason == CrudGovernanceAuditReasonCode.NONE) {
            return CrudGovernanceAuditOutcome.SUCCESS;
        }
        return CrudGovernanceAuditOutcome.EXECUTION_FAILED;
    }
}
