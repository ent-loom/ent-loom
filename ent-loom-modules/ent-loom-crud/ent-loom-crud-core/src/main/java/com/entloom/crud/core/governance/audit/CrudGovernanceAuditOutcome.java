package com.entloom.crud.core.governance.audit;

/**
 * 治理审计结果。
 */
public enum CrudGovernanceAuditOutcome {
    /** 治理通过且执行成功。 */
    SUCCESS,
    /** 治理阶段拒绝。 */
    GOVERNANCE_DENIED,
    /** 治理或查询准备出现内部错误，未进入业务执行。 */
    GOVERNANCE_FAILED,
    /** 治理通过但执行阶段失败。 */
    EXECUTION_FAILED
}
