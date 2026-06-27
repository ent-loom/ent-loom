package com.entloom.crud.core.capability.stats;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 统计结果行。
 */
@Getter
@Setter
public class StatsResultRow {
    private Map<String, Object> dimensions = new LinkedHashMap<String, Object>();
    private Map<String, Object> metrics = new LinkedHashMap<String, Object>();
}
