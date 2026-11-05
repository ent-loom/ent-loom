package com.entloom.crud.starter.web.support;

import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.model.CrudResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.MDC;

/** 单次 HTTP 请求共享的关联标识与响应信息。 */
public final class CrudHttpRequestContext {
    private static final String ATTRIBUTE = CrudHttpRequestContext.class.getName();
    private static final ThreadLocal<CrudHttpRequestContext> CURRENT = new ThreadLocal<>();
    private final String requestId;
    private final String traceId;
    private volatile CrudResponseContext responseContext;

    private CrudHttpRequestContext(String requestId, String traceId) {
        this.requestId = requestId;
        this.traceId = traceId;
    }

    public static CrudHttpRequestContext current() {
        return CURRENT.get();
    }

    public static void bindOperation(CrudOperationKey operationKey) {
        bindOperation(operationKey, null);
    }

    public static void bindOperation(CrudOperationKey operationKey, String capability) {
        CrudHttpRequestContext context = current();
        if (context != null) {
            context.responseContext = new CrudResponseContext(operationKey, capability, context.responseContext);
        }
    }

    public static <T> CrudResponse<T> completeOperation(CrudOperationKey operationKey, CrudResponse<T> response) {
        CrudResponseContext operation = removeOperation(operationKey);
        response.setOperationDomain(operationKey.getDomain().name());
        response.setOperation(operationKey.getOperation());
        if (operation != null) {
            response.setCapability(operation.getCapability());
        }
        return response;
    }

    public static void completeOperation(CrudOperationKey operationKey) {
        removeOperation(operationKey);
    }

    public static <T> T trackOperationFailure(Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException | Error failure) {
            CrudHttpRequestContext request = current();
            CrudResponseContext operation = request == null ? null : request.responseContext;
            if (operation != null) {
                operation.markFailed();
            }
            throw failure;
        }
    }

    private static CrudResponseContext removeOperation(CrudOperationKey operationKey) {
        CrudHttpRequestContext request = current();
        CrudResponseContext operation = request == null ? null : request.responseContext;
        while (operation != null && !operationKey.equals(operation.getOperationKey())) {
            operation = operation.getPrevious();
        }
        if (operation != null) {
            CrudResponseContext previous = operation.getPrevious();
            while (previous != null && previous.isFailed()) {
                previous = previous.getPrevious();
            }
            request.responseContext = previous;
        }
        return operation;
    }

    static CrudHttpRequestContext forRequest(HttpServletRequest request) {
        Object existing = request.getAttribute(ATTRIBUTE);
        if (existing instanceof CrudHttpRequestContext) return (CrudHttpRequestContext) existing;
        CrudHttpRequestContext created = new CrudHttpRequestContext(UUID.randomUUID().toString(), MDC.get("traceId"));
        request.setAttribute(ATTRIBUTE, created);
        return created;
    }

    public String getRequestId() { return requestId; }
    public String getTraceId() { return traceId; }

    CrudResponseContext getResponseContext() { return responseContext; }

    Scope open() {
        CrudHttpRequestContext previous = CURRENT.get();
        String previousRequestId = MDC.get("requestId");
        String previousTraceId = MDC.get("traceId");
        CURRENT.set(this);
        setMdc("requestId", requestId);
        setMdc("traceId", traceId);
        return () -> {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
            setMdc("requestId", previousRequestId);
            setMdc("traceId", previousTraceId);
        };
    }

    private static void setMdc(String key, String value) {
        if (value == null) MDC.remove(key); else MDC.put(key, value);
    }

    interface Scope extends AutoCloseable {
        @Override void close();
    }
}
