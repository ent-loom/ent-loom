package com.entloom.crud.starter.web.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.entloom.crud.api.model.QueryFilter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * 命令公共可选参数。
 */
@Getter
@Setter
public class CrudCommandOptions {
    /** 请求标识。 */
    private String requestId;
    /** 幂等键。 */
    private String idempotencyKey;
    /** 期望版本。 */
    private Long expectedVersion;
    /** 是否 dry-run。 */
    private Boolean dryRun;
    /** 高级目标选择器；默认写入优先使用 payload.id / payload.items[].id。 */
    private List<QueryFilter> targetFilters = new ArrayList<QueryFilter>();
    /** 未显式建模的扩展参数。 */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Map<String, Object> extraOptions = new LinkedHashMap<String, Object>();

    @JsonAnySetter
    public void addExtraOption(String name, Object value) {
        this.extraOptions.put(name, value);
    }

    public void setTargetFilters(List<QueryFilter> targetFilters) {
        this.targetFilters = targetFilters == null ? new ArrayList<QueryFilter>() : targetFilters;
    }

    @JsonIgnore
    public Map<String, Object> getExtraOptions() {
        return extraOptions;
    }

    @JsonIgnore
    public boolean isDryRunEnabled() {
        return Boolean.TRUE.equals(dryRun);
    }
}
