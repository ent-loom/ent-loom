package com.entloom.crud.starter.web.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.entloom.crud.core.capability.stats.StatsGroupBy;
import com.entloom.crud.core.capability.stats.StatsHaving;
import com.entloom.crud.core.capability.stats.StatsMetric;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * stats 查询 DSL。
 */
@Getter
@Setter
public class CrudQueryStats {
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
    /** 未显式建模的扩展字段。 */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Map<String, Object> extraFields = new LinkedHashMap<String, Object>();

    public void setGroupBy(List<StatsGroupBy> groupBy) {
        this.groupBy = groupBy == null ? new ArrayList<StatsGroupBy>() : groupBy;
    }

    public void setMetrics(List<StatsMetric> metrics) {
        this.metrics = metrics == null ? new ArrayList<StatsMetric>() : metrics;
    }

    public void setHaving(List<StatsHaving> having) {
        this.having = having == null ? new ArrayList<StatsHaving>() : having;
    }

    @JsonAnySetter
    public void addExtraField(String name, Object value) {
        this.extraFields.put(name, value);
    }

    @JsonIgnore
    public Map<String, Object> getExtraFields() {
        return extraFields;
    }
}
