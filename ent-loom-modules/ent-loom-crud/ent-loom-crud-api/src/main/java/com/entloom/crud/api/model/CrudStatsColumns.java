package com.entloom.crud.api.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 统计结果列定义。
 */
@Getter
@Setter
public class CrudStatsColumns {
    private List<String> dimensions = new ArrayList<String>();
    private List<String> metrics = new ArrayList<String>();
}
