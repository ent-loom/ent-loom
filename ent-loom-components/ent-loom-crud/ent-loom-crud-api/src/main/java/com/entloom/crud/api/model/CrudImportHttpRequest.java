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
 * 导入 HTTP 请求。
 */
@Getter
@Setter
public class CrudImportHttpRequest {
    private List<String> entityCodes = new ArrayList<String>();
    private String requestId;
    private String format = "excel-xlsx";
    private String mode = "UPSERT";
    private String sourceFileId;
    private String taskId;
    private Integer batchSize;
    private boolean async;
    private boolean includeExecutionMeta;
    private Map<String, Object> attributes = new LinkedHashMap<String, Object>();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Map<String, Object> extraFields = new LinkedHashMap<String, Object>();

    public void setEntityCodes(List<String> entityCodes) {
        this.entityCodes = entityCodes == null ? new ArrayList<String>() : entityCodes;
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
