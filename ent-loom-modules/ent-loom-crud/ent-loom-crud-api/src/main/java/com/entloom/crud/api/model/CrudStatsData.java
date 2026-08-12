package com.entloom.crud.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 统计结果数据载荷。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CrudStatsData {
    private String mode;
    private CrudRecord metrics;
    private CrudStatsColumns columns;
    private List<CrudStatsRow> rows;
    private CrudRecord summary;
    private CrudStatsPage page;
    private CrudRecord meta;
}
