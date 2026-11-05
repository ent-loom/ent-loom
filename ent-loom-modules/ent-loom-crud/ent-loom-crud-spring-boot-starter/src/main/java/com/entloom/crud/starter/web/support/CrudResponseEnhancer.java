package com.entloom.crud.starter.web.support;

import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.model.CrudErrorEnvelope;
import com.entloom.crud.api.model.CrudResponse;

/** 根据当前 HTTP 请求补充响应上下文。 */
public class CrudResponseEnhancer {
    public <T> CrudResponse<T> enhance(CrudResponse<T> response) {
        CrudHttpRequestContext request = CrudHttpRequestContext.current();
        if (request != null) {
            response.setRequestId(request.getRequestId());
            response.setTraceId(request.getTraceId());
            CrudResponseContext context = request.getResponseContext();
            while (context != null && context.isFailed() && response.isSuccess()) {
                context = context.getPrevious();
            }
            if (context != null && response.getOperation() == null) {
                CrudOperationKey operation = context.getOperationKey();
                response.setOperationDomain(operation == null ? null : operation.getDomain().name());
                response.setOperation(operation == null ? null : operation.getOperation());
                response.setCapability(context.getCapability());
            }
        }
        CrudErrorEnvelope error = response.getError();
        if (error != null) {
            if (request != null || error.getRequestId() == null) {
                error.setRequestId(response.getRequestId());
            }
            if (request != null || error.getTraceId() == null) {
                error.setTraceId(response.getTraceId());
            }
        }
        return response;
    }
}
