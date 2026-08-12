package com.entloom.crud.starter.web.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * stats 查询请求。
 */
@Getter
@Setter
public class CrudStatsHttpRequest extends CrudQueryHttpRequest {
    /** stats 查询选项。 */
    private CrudStatsOptions options = new CrudStatsOptions();
    /** stats 查询载荷。 */
    private CrudQueryStats stats;

    public void setOptions(CrudStatsOptions options) {
        this.options = options == null ? new CrudStatsOptions() : options;
    }
}
