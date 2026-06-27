package com.entloom.crud.api.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * 导出 HTTP 请求。
 */
@Getter
@Setter
public class CrudExportHttpRequest {
    private List<String> entityCodes = new ArrayList<String>();
    private String requestId;
    private String format = "excel-xlsx";
    private String fileName;
    private String taskId;
    private boolean async;
    private List<String> fields = new ArrayList<String>();
    private List<QueryFilter> filters = new ArrayList<QueryFilter>();
    private List<QuerySort> sorts = new ArrayList<QuerySort>();
    private QueryTimeRange time;
    private CrudExportRenderOptions renderOptions;
    private PageRequest page;
    private Integer limit;
    private boolean includeExecutionMeta;
    private Map<String, Object> attributes = new LinkedHashMap<String, Object>();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Map<String, Object> extraFields = new LinkedHashMap<String, Object>();

    public void setEntityCodes(List<String> entityCodes) {
        this.entityCodes = entityCodes == null ? new ArrayList<String>() : entityCodes;
    }

    public void setFields(List<String> fields) {
        this.fields = fields == null ? new ArrayList<String>() : fields;
    }

    public void setFilters(List<QueryFilter> filters) {
        this.filters = filters == null ? new ArrayList<QueryFilter>() : filters;
    }

    public void setSorts(List<QuerySort> sorts) {
        this.sorts = sorts == null ? new ArrayList<QuerySort>() : sorts;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes == null ? new LinkedHashMap<String, Object>() : attributes;
    }

    @JsonAnySetter
    public void addExtraField(String name, Object value) {
        this.extraFields.put(name, value);
    }

    @JsonIgnore
    public Map<String, Object> getExtraFields() {
        return extraFields;
    }
}
