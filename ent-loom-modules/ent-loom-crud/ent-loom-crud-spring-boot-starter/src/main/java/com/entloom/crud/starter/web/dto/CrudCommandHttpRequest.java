package com.entloom.crud.starter.web.dto;

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
 * HTTP 命令请求 DTO。
 */
@Getter
@Setter
public class CrudCommandHttpRequest {
    /** 实体编码列表。 */
    private List<String> entityCodes = new ArrayList<String>();
    /** 命令选项。 */
    private CrudCommandOptions options = new CrudCommandOptions();
    /** 业务载荷。 */
    private Object payload;
    /** 未显式建模的顶层扩展字段。 */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Map<String, Object> extraFields = new LinkedHashMap<String, Object>();

    public void setEntityCodes(List<String> entityCodes) {
        this.entityCodes = entityCodes == null ? new ArrayList<String>() : entityCodes;
    }

    public void setOptions(CrudCommandOptions options) {
        this.options = options == null ? new CrudCommandOptions() : options;
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
