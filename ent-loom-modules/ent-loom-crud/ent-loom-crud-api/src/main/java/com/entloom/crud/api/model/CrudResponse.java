package com.entloom.crud.api.model;

import com.entloom.crud.api.enums.CrudErrorStage;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 通用 CRUD HTTP 响应。静态工厂创建基础响应，请求上下文由 HTTP 层补充。
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

    /** HTTP 适配保留命令结果语义，不将业务失败重新包装为成功。 */
    public static <T> CrudResponse<T> from(CommandResult<T> result) {
        java.util.Objects.requireNonNull(result, "命令结果不能为空");
        Map<String, Object> meta = new LinkedHashMap<String, Object>();
        if (result.isIdempotentReplay()) meta.put("idempotentReplay", Boolean.TRUE);
        return of(result.isSuccess(), result.getCode() == null ? "OK" : result.getCode(),
            result.getMessage() == null ? "OK" : result.getMessage(), result.getData(), meta);
    }

    public static <T> CrudResponse<T> success(T data) {
        return success("OK", "OK", data, null);
    }

    public static <T> CrudResponse<T> success(String code, String message, T data, Map<String, Object> meta) {
        return of(true, code, message, data, meta);
    }

    public static CrudResponse<Void> failure(String code, String message) {
        return failure(new CrudErrorEnvelope(code, message, CrudErrorStage.UNKNOWN, null, code));
    }

    public static CrudResponse<Void> failure(CrudErrorEnvelope error) {
        CrudErrorEnvelope actual = error == null
            ? new CrudErrorEnvelope("INTERNAL_ERROR", "内部错误", CrudErrorStage.UNKNOWN, null, "INTERNAL_ERROR")
            : error.copy();
        if (actual.getStage() == null) {
            actual.setStage(CrudErrorStage.UNKNOWN);
        }
        CrudResponse<Void> response = of(false, actual.getCode(), actual.getMessage(), null, null);
        response.setError(actual);
        return response;
    }

    public static <T> CrudResponse<T> of(
        boolean success, String code, String message, T data, Map<String, Object> meta
    ) {
        CrudResponse<T> response = new CrudResponse<T>();
        response.setSuccess(success);
        response.setCode(code);
        response.setMessage(message);
        response.setData(data);
        response.setMeta(meta == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(meta));
        return response;
    }
}
