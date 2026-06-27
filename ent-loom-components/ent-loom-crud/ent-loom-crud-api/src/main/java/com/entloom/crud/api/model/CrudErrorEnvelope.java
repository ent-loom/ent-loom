package com.entloom.crud.api.model;

import com.entloom.crud.api.enums.CrudErrorStage;
import lombok.Getter;
import lombok.Setter;

/**
 * CRUD 错误响应信封。
 */
@Getter
@Setter
public class CrudErrorEnvelope {
    /** 错误码。 */
    private String code;
    /** 错误消息。 */
    private String message;
    /** 失败阶段。 */
    private CrudErrorStage stage;
    /** 路由 key。 */
    private String routeKey;
    /** 请求 ID。 */
    private String requestId;
    /** 链路 ID。 */
    private String traceId;
    /** 机器可读失败原因。 */
    private String reason;

    public CrudErrorEnvelope() {
    }

    public CrudErrorEnvelope(String code, String message, CrudErrorStage stage, String routeKey, String reason) {
        this.code = code;
        this.message = message;
        this.stage = stage;
        this.routeKey = routeKey;
        this.reason = reason;
    }

    public CrudErrorEnvelope copy() {
        CrudErrorEnvelope copy = new CrudErrorEnvelope();
        copy.setCode(code);
        copy.setMessage(message);
        copy.setStage(stage);
        copy.setRouteKey(routeKey);
        copy.setRequestId(requestId);
        copy.setTraceId(traceId);
        copy.setReason(reason);
        return copy;
    }
}
