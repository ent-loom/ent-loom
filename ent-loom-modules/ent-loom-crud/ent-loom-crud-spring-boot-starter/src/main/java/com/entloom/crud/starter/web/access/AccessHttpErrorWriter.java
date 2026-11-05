package com.entloom.crud.starter.web.access;

import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.starter.web.error.CrudHttpErrorDescriptor;
import com.entloom.crud.starter.web.error.CrudHttpErrorMapper;
import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.starter.web.support.CrudResponseEnhancer;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** 准入层复用现有错误包络和状态映射，不依赖 Controller 异常通知范围。 */
public final class AccessHttpErrorWriter {
    private final ObjectMapper objectMapper;
    private final CrudResponseEnhancer responseEnhancer;

    public AccessHttpErrorWriter(ObjectMapper objectMapper, CrudResponseEnhancer responseEnhancer) {
        this.objectMapper = objectMapper;
        this.responseEnhancer = responseEnhancer;
    }

    public void write(HttpServletResponse response, CrudException exception) throws IOException {
        CrudHttpErrorDescriptor descriptor = CrudHttpErrorMapper.map(exception);
        response.setStatus(descriptor.getStatus().value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), responseEnhancer.enhance(CrudResponse.failure(descriptor.getError())));
    }
}
