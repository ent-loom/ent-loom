package com.entloom.crud.starter.web.support;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 仅格式化 ent-crud 响应，避免修改 Spring 全局 JSON 行为。
 */
@ControllerAdvice
public class CrudResponseDateFormatAdvice implements ResponseBodyAdvice<Object> {

    private static final String CRUD_PATH_MARKER = "/ent-crud";

    private final CrudResponseDateFormatter dateFormatter;

    public CrudResponseDateFormatAdvice(CrudResponseDateFormatter dateFormatter) {
        this.dateFormatter = dateFormatter;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return converterType == null || !ByteArrayHttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(
        Object body,
        MethodParameter returnType,
        MediaType selectedContentType,
        Class<? extends HttpMessageConverter<?>> selectedConverterType,
        ServerHttpRequest request,
        ServerHttpResponse response
    ) {
        if (!isCrudRequest(request) || !isJsonResponse(selectedContentType)) {
            return body;
        }
        return dateFormatter.format(body);
    }

    private boolean isCrudRequest(ServerHttpRequest request) {
        String path = request == null || request.getURI() == null ? null : request.getURI().getPath();
        return path != null && path.contains(CRUD_PATH_MARKER);
    }

    private boolean isJsonResponse(MediaType mediaType) {
        if (mediaType == null) {
            return true;
        }
        if (MediaType.APPLICATION_JSON.includes(mediaType) || mediaType.includes(MediaType.APPLICATION_JSON)) {
            return true;
        }
        String subtype = mediaType.getSubtype();
        return subtype != null && subtype.endsWith("+json");
    }
}
