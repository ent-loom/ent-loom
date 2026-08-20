package com.entloom.meta.contract.contribution;

/**
 * 同级 Contribution 冲突处理策略。
 */
public enum MetaConflictPolicy {
    /** 产生错误诊断，由 fail-fast 策略决定是否中止。 */
    FAIL,
    /** 产生警告诊断，保留稳定裁决结果。 */
    WARN,
    /** 忽略冲突诊断，但仍使用稳定裁决结果。 */
    IGNORE
}
