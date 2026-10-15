package com.example.minicommerce.common.web;

import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.starter.web.error.CrudHttpErrorMapper;
import com.entloom.crud.starter.web.support.CrudResponseBuilder;
import com.example.minicommerce.MiniCommerceApplication;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** 应用接口统一错误响应；业务异常优先交给框架契约，接口特例由局部处理器覆盖。 */
@RestControllerAdvice(basePackageClasses = MiniCommerceApplication.class)
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private final CrudResponseBuilder responses;

    @ExceptionHandler(CrudException.class)
    ResponseEntity<CrudResponse<Void>> frameworkError(CrudException error) {
        var mapped = CrudHttpErrorMapper.map(error);
        return ResponseEntity.status(mapped.getStatus()).body(responses.failure(mapped.getError()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
        HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = error.getBindingResult().getAllErrors().stream()
            .map(DefaultMessageSourceResolvable::getDefaultMessage).filter(StringUtils::hasText)
            .distinct().sorted().collect(Collectors.joining("；"));
        return handleExceptionInternal(error, responses.failure("VALIDATION_ERROR",
            StringUtils.hasText(message) ? message : "请求参数不合法"), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException error,
        HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(error, responses.failure("VALIDATION_ERROR",
            "请求体不能为空，且须为符合接口约定的 JSON"), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception error, Object body,
        HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            log.error("业务接口发生 HTTP 服务端异常", error);
        }
        if (!(body instanceof CrudResponse<?>)) {
            String code = status.is5xxServerError() ? "INTERNAL_ERROR"
                : status.value() == 400 ? "VALIDATION_ERROR"
                : status.value() == 405 ? "METHOD_NOT_ALLOWED" : "HTTP_ERROR";
            String message = switch (status.value()) {
                case 400 -> "请求参数不合法";
                case 404 -> "请求资源不存在";
                case 405 -> "请求方法不支持";
                case 415 -> "请求内容类型不支持";
                default -> status.is5xxServerError() ? "服务暂时不可用，请稍后重试" : "请求未能完成";
            };
            body = responses.failure(code, message);
        }
        return super.handleExceptionInternal(error, body, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpectedError(Exception error, WebRequest request) {
        var responseStatus = AnnotatedElementUtils.findMergedAnnotation(error.getClass(), ResponseStatus.class);
        if (responseStatus != null) {
            return handleExceptionInternal(error, null, new HttpHeaders(), responseStatus.code(), request);
        }
        log.error("业务接口发生未预期异常", error);
        return ResponseEntity.internalServerError()
            .body(responses.failure("INTERNAL_ERROR", "服务暂时不可用，请稍后重试"));
    }
}
