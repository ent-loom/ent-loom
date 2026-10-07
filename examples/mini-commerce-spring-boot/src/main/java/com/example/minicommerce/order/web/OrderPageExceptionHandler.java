package com.example.minicommerce.order.web;

import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.starter.web.error.CrudHttpErrorMapper;
import com.entloom.crud.starter.web.support.CrudResponseBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 两端分页共用框架错误映射与分页输入校验响应。 */
@RestControllerAdvice(assignableTypes = {ConsumerOrderController.class, MerchantOrderController.class})
@RequiredArgsConstructor
public class OrderPageExceptionHandler {
    private final CrudResponseBuilder responses;

    @ExceptionHandler(CrudException.class)
    ResponseEntity<CrudResponse<Void>> frameworkError(CrudException error) {
        var mapped = CrudHttpErrorMapper.map(error);
        return ResponseEntity.status(mapped.getStatus()).body(responses.failure(mapped.getError()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<CrudResponse<Void>> invalidRequest(Exception error) {
        return ResponseEntity.badRequest().body(responses.failure("VALIDATION_ERROR", "页码须从 1 开始，单页 1～100 条，筛选 ID 须为正整数"));
    }
}
