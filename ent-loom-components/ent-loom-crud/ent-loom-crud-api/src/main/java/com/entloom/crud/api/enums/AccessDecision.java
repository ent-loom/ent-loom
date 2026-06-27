package com.entloom.crud.api.enums;

/**
 * 治理判定结果。
 */
public enum AccessDecision {
    /** 允许。 */
    ALLOW,
    /** 拒绝。 */
    DENY,
    /** 脱敏后允许（预留）。 */
    MASK,
    /** 过滤后允许（预留）。 */
    FILTER
}
