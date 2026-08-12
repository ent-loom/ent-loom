package com.entloom.crud.core.capability.stats;

/**
 * 聚合查询结果形态。
 */
public enum StatsQueryMode {
    /** 标量聚合结果。 */
    SCALAR,
    /** 分组列表聚合结果。 */
    LIST,
    /** 分组分页聚合结果。 */
    PAGE
}
