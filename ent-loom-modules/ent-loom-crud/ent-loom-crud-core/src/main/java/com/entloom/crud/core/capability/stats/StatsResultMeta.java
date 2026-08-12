package com.entloom.crud.core.capability.stats;

import lombok.Getter;
import lombok.Setter;

/**
 * 统计执行元信息。
 */
@Getter
@Setter
public class StatsResultMeta {
    private String timezone;
    private Long costMs;
}
