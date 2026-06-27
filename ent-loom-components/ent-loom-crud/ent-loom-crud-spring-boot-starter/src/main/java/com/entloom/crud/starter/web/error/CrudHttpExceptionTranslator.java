package com.entloom.crud.starter.web.error;

import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.api.model.CrudErrorEnvelope;
import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.starter.web.controller.EntCrudCommandController;
import com.entloom.crud.starter.web.controller.EntCrudQueryController;
import com.entloom.crud.starter.web.controller.EntCrudStatsController;
import com.entloom.crud.starter.web.support.CrudResponseBuilder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;

/**
 * 控制器异常映射器。
 */
@RestControllerAdvice(
    assignableTypes = {EntCrudQueryController.class, EntCrudCommandController.class, EntCrudStatsController.class}
)
@RequiredArgsConstructor
public class CrudHttpExceptionTranslator {
    /** 日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(CrudHttpExceptionTranslator.class);
    private final CrudResponseBuilder crudResponseBuilder;

    /**
     * 统一异常映射。
     *
     * @param ex 框架异常
     * @return HTTP 响应
     */
    @ExceptionHandler(CrudException.class)
    public ResponseEntity<CrudResponse<Void>> handleCrudException(CrudException ex) {
        log.warn("CRUD request failed: code={}, message={}", ex.getErrorCode(), ex.getMessage(), ex);
        return toResponse(CrudHttpErrorMapper.map(ex));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CrudResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("CRUD request failed: code={}, message={}", CrudErrorCode.METHOD_NOT_ALLOWED, ex.getMessage(), ex);
        return toResponse(new CrudHttpErrorDescriptor(
            org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED,
            new CrudErrorEnvelope(
                CrudErrorCode.METHOD_NOT_ALLOWED.name(),
                ex.getMessage(),
                CrudErrorStage.HTTP_CONTRACT,
                null,
                CrudErrorCode.METHOD_NOT_ALLOWED.name()
            )
        ));
    }

    /**
     * 兜底异常映射。
     *
     * @param ex 未分类异常
     * @return HTTP 响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CrudResponse<Void>> handleException(Exception ex) {
        log.error("Unhandled exception in CRUD controller", ex);
        return toResponse(CrudHttpErrorMapper.map(ex));
    }

    private ResponseEntity<CrudResponse<Void>> toResponse(CrudHttpErrorDescriptor descriptor) {
        CrudResponse<Void> body = crudResponseBuilder.failure(descriptor.getError());
        return ResponseEntity.status(descriptor.getStatus()).body(body);
    }
}
