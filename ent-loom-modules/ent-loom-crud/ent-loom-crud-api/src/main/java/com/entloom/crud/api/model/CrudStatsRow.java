package com.entloom.crud.api.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 统计结果行。
 */
@Getter
@Setter
public class CrudStatsRow {
    private CrudRecord dimensions = new CrudRecord();
    private CrudRecord metrics = new CrudRecord();
}
