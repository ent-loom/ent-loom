package com.entloom.crud.core.capability.stats;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 统计列定义。
 */
@Getter
@Setter
public class StatsResultColumns {
    private List<String> dimensions = new ArrayList<String>();
    private List<String> metrics = new ArrayList<String>();
}
