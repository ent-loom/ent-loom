package com.entloom.crud.core.capability.stats;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 统计指标定义。
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StatsMetric {
    /** 聚合函数。 */
    private String agg;
    /** 指标字段。 */
    private String field;
    /** 指标别名。 */
    private String alias;
}
