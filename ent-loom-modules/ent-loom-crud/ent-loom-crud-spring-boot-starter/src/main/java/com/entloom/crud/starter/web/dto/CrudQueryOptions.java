package com.entloom.crud.starter.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * 查询公共可选参数。
 */
@Getter
@Setter
public class CrudQueryOptions {
    /** 请求标识。 */
    private String requestId;
    /** 页码。 */
    private Integer page;
    /** 分页大小。 */
    private Integer limit;
    /** 过滤条件列表（read/stats 共用）。 */
    private List<QueryFilter> filters = new ArrayList<QueryFilter>();
    /** 过滤条件列表（前端友好字段）。 */
    private List<QueryFilter> filterList = new ArrayList<QueryFilter>();
    /** 最简过滤对象（字段到值映射）。 */
    private Map<String, Object> filter = new HashMap<String, Object>();
    /** 字段过滤映射。 */
    private Map<String, QueryFilter> filterMap = new HashMap<String, QueryFilter>();
    /** 时间过滤条件。 */
    private CrudTimeFilter time;
    /** 排序条件列表。 */
    private List<QuerySort> sorts = new ArrayList<QuerySort>();
    /** 显式投影字段列表。 */
    private List<String> selectFields = new ArrayList<String>();
    /** 单一排序条件（兼容字段）。 */
    private QuerySort sort;
    /** 展开关联列表。 */
    private List<String> expandRelations = new ArrayList<String>();
    /** 未显式建模的扩展参数。 */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Map<String, Object> extraOptions = new LinkedHashMap<String, Object>();

    public void setFilters(List<QueryFilter> filters) {
        this.filters = filters == null ? new ArrayList<QueryFilter>() : filters;
    }

    public void setFilterList(List<QueryFilter> filterList) {
        this.filterList = filterList == null ? new ArrayList<QueryFilter>() : filterList;
    }

    public void setFilter(Map<String, Object> filter) {
        this.filter = filter == null ? new HashMap<String, Object>() : filter;
    }

    public void setFilterMap(Map<String, QueryFilter> filterMap) {
        this.filterMap = filterMap == null ? new HashMap<String, QueryFilter>() : filterMap;
    }

    public void setSorts(List<QuerySort> sorts) {
        this.sorts = sorts == null ? new ArrayList<QuerySort>() : sorts;
    }

    public void setSelectFields(List<String> selectFields) {
        this.selectFields = selectFields == null ? new ArrayList<String>() : selectFields;
    }

    public void setExpandRelations(List<String> expandRelations) {
        this.expandRelations = expandRelations == null ? new ArrayList<String>() : expandRelations;
    }

    @JsonAnySetter
    public void addExtraOption(String name, Object value) {
        this.extraOptions.put(name, value);
    }

    @JsonIgnore
    public Map<String, Object> getExtraOptions() {
        return extraOptions;
    }
}
