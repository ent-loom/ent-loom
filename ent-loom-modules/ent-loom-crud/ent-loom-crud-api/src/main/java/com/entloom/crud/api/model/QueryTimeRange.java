package com.entloom.crud.api.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 通用查询时间范围。
 */
@Getter
@Setter
public class QueryTimeRange {
    /** 时间字段。 */
    private String field;
    /** 起始时间。 */
    private String start;
    /** 结束时间。 */
    private String end;
    /** 时区。 */
    private String timezone;
}
