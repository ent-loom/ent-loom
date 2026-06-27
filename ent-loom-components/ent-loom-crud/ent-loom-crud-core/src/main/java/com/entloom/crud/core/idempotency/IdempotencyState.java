package com.entloom.crud.core.idempotency;

/**
 * 幂等状态。
 */
public enum IdempotencyState {
    /** 处理中。 */
    PROCESSING,
    /** 成功。 */
    SUCCEEDED,
    /** 最终失败。 */
    FAILED_FINAL
}
