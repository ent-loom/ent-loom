package com.entloom.crud.core.capability.stats;

import lombok.Getter;
import lombok.Setter;

/**
 * 统计分页信息。
 */
@Getter
@Setter
public class StatsResultPage {
    private int page;
    private int limit;
    private int returned;
    private String nextCursor;
    private Long totalGroups;
}
