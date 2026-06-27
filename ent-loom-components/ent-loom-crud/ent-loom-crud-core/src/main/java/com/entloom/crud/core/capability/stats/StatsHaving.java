package com.entloom.crud.core.capability.stats;

import com.entloom.crud.api.enums.FilterOperator;
import lombok.Getter;
import lombok.Setter;

/**
 * 指标聚合后的 having 条件。
 */
@Getter
@Setter
public class StatsHaving {
    /** 指标别名。 */
    private String metric;
    /** 操作符。 */
    private FilterOperator op;
    /** 条件值。 */
    private Object value;
}
