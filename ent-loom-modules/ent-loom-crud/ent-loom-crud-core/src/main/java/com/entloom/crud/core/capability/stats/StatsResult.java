package com.entloom.crud.core.capability.stats;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 统计查询结果。
 */
@Getter
@Setter
public class StatsResult {
    private StatsQueryMode mode;
    private Map<String, Object> metrics;
    private StatsResultColumns columns;
    private List<StatsResultRow> rows;
    private Map<String, Object> summary;
    private StatsResultPage page;
    private StatsResultMeta meta;
}
