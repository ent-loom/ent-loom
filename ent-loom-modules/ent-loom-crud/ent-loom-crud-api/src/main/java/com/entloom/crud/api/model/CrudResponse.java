package com.entloom.crud.api.model;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 通用 CRUD HTTP 响应。
 */
@Getter
@Setter
public class CrudResponse<T> {
    private boolean success;
    private String code;
    private String message;
    private CrudErrorEnvelope error;
    private String requestId;
    private String traceId;
    private String operationDomain;
    private String operation;
    private String capability;
    private T data;
    private Map<String, Object> meta = new LinkedHashMap<String, Object>();
}
