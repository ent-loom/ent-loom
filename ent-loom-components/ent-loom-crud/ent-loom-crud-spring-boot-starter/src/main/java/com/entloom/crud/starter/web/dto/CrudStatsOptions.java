package com.entloom.crud.starter.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

/**
 * stats 专属查询选项。
 */
@Getter
@Setter
public class CrudStatsOptions extends CrudQueryOptions {
    /** 调试开关。 */
    private Boolean debug;
    /** 追踪开关。 */
    private Boolean trace;

    @JsonIgnore
    public boolean includeExecutionMeta() {
        return Boolean.TRUE.equals(debug) || Boolean.TRUE.equals(trace);
    }
}
