package com.entloom.crud.core.capability.stats;

import lombok.Getter;
import lombok.Setter;

/**
 * 统计时间范围参数。
 */
@Getter
@Setter
public class StatsTimeRange {
    /** 时间字段。 */
    private String field;
    /** 起始时间（包含）。 */
    private String from;
    /** 结束时间（不包含）。 */
    private String to;
    /** 时区。 */
    private String timezone;
}
