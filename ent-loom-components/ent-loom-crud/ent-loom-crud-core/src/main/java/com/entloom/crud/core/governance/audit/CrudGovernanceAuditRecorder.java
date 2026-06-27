package com.entloom.crud.core.governance.audit;

/**
 * 治理审计记录器。
 */
public interface CrudGovernanceAuditRecorder {
    /**
     * 记录治理事件。
     *
     * @param event 审计事件
     */
    void record(CrudGovernanceAuditEvent event);
}
