package com.entloom.crud.starter.web.error;

import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.starter.web.support.CrudResponseBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 业务异常的默认 HTTP 转换器。
 */
@RestControllerAdvice
public class EntBusinessExceptionHandler {
    private final CrudResponseBuilder responseBuilder;

    public EntBusinessExceptionHandler(CrudResponseBuilder responseBuilder) {
        this.responseBuilder = responseBuilder;
    }

    @ExceptionHandler(EntBusinessException.class)
    public ResponseEntity<CrudResponse<Void>> handle(EntBusinessException businessException) {
        CrudResponse<Void> response = responseBuilder.failure(businessException.getCode(), businessException.getMessage());
        return ResponseEntity.status(businessException.getHttpStatus()).body(response);
    }
}
