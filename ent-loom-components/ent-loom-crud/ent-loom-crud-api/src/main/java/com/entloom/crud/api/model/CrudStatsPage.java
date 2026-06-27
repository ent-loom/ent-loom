package com.entloom.crud.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

/**
 * 统计分页信息。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CrudStatsPage {
    private int page;
    private int limit;
    private int returned;
    private String nextCursor;
    private Long totalGroups;
}
