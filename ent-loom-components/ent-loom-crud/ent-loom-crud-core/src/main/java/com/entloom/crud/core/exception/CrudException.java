package com.entloom.crud.core.exception;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.CrudErrorStage;
import lombok.Getter;
import lombok.Setter;

/**
 * 框架统一异常基类。
 */
public class CrudException extends RuntimeException {
    @Getter
    /** 错误码。 */
    private final CrudErrorCode errorCode;
    /** 失败阶段。 */
    @Getter
    @Setter
    private CrudErrorStage stage;
    /** 路由 key。 */
    @Getter
    @Setter
    private String routeKey;
    /** 机器可读失败原因。 */
    @Getter
    @Setter
    private String reason;

    public CrudException(CrudErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CrudException(CrudErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public CrudException withStage(CrudErrorStage stage) {
        this.stage = stage;
        return this;
    }

    public CrudException withRouteKey(String routeKey) {
        this.routeKey = routeKey;
        return this;
    }

    public CrudException withReason(String reason) {
        this.reason = reason;
        return this;
    }

    public CrudException fillMissing(CrudErrorStage stage, String routeKey, String reason) {
        if (this.stage == null) {
            this.stage = stage;
        }
        if (this.routeKey == null || this.routeKey.trim().isEmpty()) {
            this.routeKey = routeKey;
        }
        if (this.reason == null || this.reason.trim().isEmpty()) {
            this.reason = reason;
        }
        return this;
    }
}
