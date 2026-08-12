package com.entloom.crud.core.governance.audit;

/**
 * 治理审计结果。
 */
public enum CrudGovernanceAuditOutcome {
    /** 治理通过且执行成功。 */
    SUCCESS,
    /** 治理阶段拒绝。 */
    GOVERNANCE_DENIED,
    /** 治理通过但执行阶段失败。 */
    EXECUTION_FAILED
}
