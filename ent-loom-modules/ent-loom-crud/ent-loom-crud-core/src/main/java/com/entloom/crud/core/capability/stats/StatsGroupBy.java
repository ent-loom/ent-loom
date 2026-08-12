package com.entloom.crud.core.capability.stats;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 统计分组维度定义。
 */
@Getter
@Setter
@NoArgsConstructor
public class StatsGroupBy {
    /** 维度字段。 */
    private String field;
    /** 分组别名。 */
    private String alias;
    /** 时间分桶粒度。 */
    private String granularity;

    public StatsGroupBy(String field) {
        this.field = field;
    }
}
