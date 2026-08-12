package com.entloom.crud.core.governance.audit;

import com.entloom.crud.core.governance.scope.CrudDataScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认结构化日志审计实现。
 */
public class LoggingCrudGovernanceAuditRecorder implements CrudGovernanceAuditRecorder {
    /** 日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(LoggingCrudGovernanceAuditRecorder.class);

    @Override
    public void record(CrudGovernanceAuditEvent event) {
        if (event == null) {
            return;
        }
        log.info(
            "crud_governance_audit outcome={} allowed={} success={} decision={} reason={} stage={} routeKey={} requestId={} traceId={} resource={} operationDomain={} operation={} action={} scene={} capability={} subjectId={} tenantId={} orgId={} grantedScope={} governanceScope={} costMs={}",
            event.getOutcome(),
            event.isAllowed(),
            event.isSuccess(),
            event.getAccessDecision(),
            event.getReason(),
            event.getStage(),
            event.getRouteKey(),
            event.getRequestId(),
            event.getTraceId(),
            event.getAction() == null ? null : event.getAction().getResource(),
            event.getAction() == null ? null : event.getAction().getOperationDomain(),
            event.getAction() == null ? null : event.getAction().getOperation(),
            event.getAction() == null ? null : event.getAction().getAction(),
            event.getAction() == null ? null : event.getAction().getScene(),
            event.getAction() == null ? null : event.getAction().getCapability(),
            event.getSubject() == null ? null : event.getSubject().getSubjectId(),
            event.getSubject() == null ? null : event.getSubject().getTenantId(),
            event.getSubject() == null ? null : event.getSubject().getOrgId(),
            summarize(event.getGrantedScope()),
            summarize(event.getGovernanceScope()),
            event.getCostMs()
        );
    }

    private String summarize(CrudDataScope scope) {
        if (scope == null) {
            return "null";
        }
        return "{explicitAll=" + scope.isExplicitAll() + ",dimensions=" + scope.getDimensions() + "}";
    }
}
