package com.entloom.crud.core.capability.stats;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 单表统计查询 DSL 载荷。
 */
@Getter
@Setter
public class StatsQueryPayload {
    /** 分组维度。 */
    private List<StatsGroupBy> groupBy = new ArrayList<StatsGroupBy>();
    /** 指标定义。 */
    private List<StatsMetric> metrics = new ArrayList<StatsMetric>();
    /** having 条件。 */
    private List<StatsHaving> having = new ArrayList<StatsHaving>();
    /** 是否包含汇总。 */
    private Boolean includeSummary;
    /** 是否返回分组总数。 */
    private Boolean includeTotalGroups;

    public void setGroupBy(List<StatsGroupBy> groupBy) {
        this.groupBy = groupBy == null ? new ArrayList<StatsGroupBy>() : groupBy;
    }

    public void setMetrics(List<StatsMetric> metrics) {
        this.metrics = metrics == null ? new ArrayList<StatsMetric>() : metrics;
    }

    public void setHaving(List<StatsHaving> having) {
        this.having = having == null ? new ArrayList<StatsHaving>() : having;
    }
}
