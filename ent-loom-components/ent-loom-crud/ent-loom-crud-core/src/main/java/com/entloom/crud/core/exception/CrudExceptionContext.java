package com.entloom.crud.core.exception;

import com.entloom.crud.api.enums.CrudErrorStage;

/**
 * CRUD 异常上下文补齐工具。
 */
public final class CrudExceptionContext {
    private CrudExceptionContext() {
    }

    public static RuntimeException enrich(RuntimeException ex, CrudErrorStage stage, String routeKey) {
        return enrich(ex, stage, routeKey, null);
    }

    public static RuntimeException enrich(RuntimeException ex, CrudErrorStage stage, String routeKey, String reason) {
        if (ex instanceof CrudException) {
            CrudException crudException = (CrudException) ex;
            return crudException.fillMissing(stage, routeKey, resolveReason(crudException, reason));
        }
        return ex;
    }

    private static String resolveReason(CrudException ex, String reason) {
        if (reason != null && !reason.trim().isEmpty()) {
            return reason;
        }
        return ex.getErrorCode() == null ? null : ex.getErrorCode().name();
    }
}
