package com.entloom.crud.starter.web.support;

import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.api.model.CrudErrorEnvelope;
import com.entloom.crud.api.model.CrudResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;

/**
 * CRUD HTTP 响应构建器。
 */
public class CrudResponseBuilder {
    public void bind(String requestId, CrudOperationKey operationKey) {
        bind(requestId, operationKey, null);
    }

    public void bind(String requestId, CrudOperationKey operationKey, String capability) {
        CrudResponseContextHolder.bind(requestId, operationKey, capability);
    }

    public void clear() {
        CrudResponseContextHolder.clear();
    }

    public <T> CrudResponse<T> success(T data) {
        return respond(true, "OK", "OK", data, null);
    }

    public <T> CrudResponse<T> success(String code, String message, T data, Map<String, Object> meta) {
        return respond(true, code, message, data, meta);
    }

    public CrudResponse<Void> failure(String code, String message) {
        return failure(new CrudErrorEnvelope(code, message, CrudErrorStage.UNKNOWN, null, code));
    }

    public CrudResponse<Void> failure(CrudErrorEnvelope error) {
        CrudErrorEnvelope actualError = error == null
            ? new CrudErrorEnvelope("INTERNAL_ERROR", "内部错误", CrudErrorStage.UNKNOWN, null, "INTERNAL_ERROR")
            : error.copy();
        CrudResponse<Void> response = build(false, actualError.getCode(), actualError.getMessage(), null, null);
        if (actualError.getStage() == null) {
            actualError.setStage(CrudErrorStage.UNKNOWN);
        }
        if (actualError.getRequestId() == null) {
            actualError.setRequestId(response.getRequestId());
        }
        if (actualError.getTraceId() == null) {
            actualError.setTraceId(response.getTraceId());
        }
        response.setError(actualError);
        return response;
    }

    public <T> CrudResponse<T> respond(
        boolean success,
        String code,
        String message,
        T data,
        Map<String, Object> meta
    ) {
        return build(success, code, message, data, meta);
    }

    private <T> CrudResponse<T> build(
        boolean success,
        String code,
        String message,
        T data,
        Map<String, Object> meta
    ) {
        CrudResponseContext context = CrudResponseContextHolder.clear();
        CrudResponse<T> response = new CrudResponse<T>();
        response.setSuccess(success);
        response.setCode(code);
        response.setMessage(message);
        response.setData(data);
        response.setRequestId(context == null ? null : context.getRequestId());
        CrudOperationKey operationKey = context == null ? null : context.getOperationKey();
        response.setOperationDomain(operationKey == null ? null : operationKey.getDomain().name());
        response.setOperation(operationKey == null ? null : operationKey.getOperation());
        response.setCapability(context == null ? null : context.getCapability());
        response.setTraceId(resolveTraceId());
        response.setMeta(meta == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(meta));
        return response;
    }

    private String resolveTraceId() {
        return MDC.get("traceId");
    }
}
