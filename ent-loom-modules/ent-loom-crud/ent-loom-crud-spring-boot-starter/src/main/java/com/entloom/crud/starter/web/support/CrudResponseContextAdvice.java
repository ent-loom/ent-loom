package com.entloom.crud.starter.web.support;

import com.entloom.crud.api.model.CrudResponse;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** 在格式化和序列化之前补充 CRUD 响应上下文。 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CrudResponseContextAdvice implements ResponseBodyAdvice<Object> {
    private final CrudResponseEnhancer enhancer;

    public CrudResponseContextAdvice(CrudResponseEnhancer enhancer) {
        this.enhancer = enhancer;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
        Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof MappingJacksonValue) {
            MappingJacksonValue wrapper = (MappingJacksonValue) body;
            if (wrapper.getValue() instanceof CrudResponse<?>) {
                wrapper.setValue(enhancer.enhance((CrudResponse<?>) wrapper.getValue()));
            }
            return wrapper;
        }
        return body instanceof CrudResponse<?> ? enhancer.enhance((CrudResponse<?>) body) : body;
    }
}
