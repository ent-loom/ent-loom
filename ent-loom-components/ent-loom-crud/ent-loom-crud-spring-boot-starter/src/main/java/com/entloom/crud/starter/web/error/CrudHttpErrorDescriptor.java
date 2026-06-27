package com.entloom.crud.starter.web.error;

import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.api.model.CrudErrorEnvelope;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * HTTP 错误描述。
 */
@Getter
public class CrudHttpErrorDescriptor {
    private final HttpStatus status;
    private final CrudErrorEnvelope error;

    public CrudHttpErrorDescriptor(HttpStatus status, String code, String message) {
        this(status, new CrudErrorEnvelope(code, message, CrudErrorStage.UNKNOWN, null, code));
    }

    public CrudHttpErrorDescriptor(HttpStatus status, CrudErrorEnvelope error) {
        this.status = status;
        this.error = error;
    }

    public String getCode() {
        return error == null ? null : error.getCode();
    }

    public String getMessage() {
        return error == null ? null : error.getMessage();
    }
}
