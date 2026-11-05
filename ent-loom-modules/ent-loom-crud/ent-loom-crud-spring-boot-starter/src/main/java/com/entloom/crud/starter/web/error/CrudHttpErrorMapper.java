package com.entloom.crud.starter.web.error;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.api.model.CrudErrorEnvelope;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.CommandActionRejectedException;
import org.springframework.http.HttpStatus;

/**
 * CRUD 异常到 HTTP 错误描述的映射器。
 */
public final class CrudHttpErrorMapper {
    private CrudHttpErrorMapper() {
    }

    public static CrudHttpErrorDescriptor map(Exception ex) {
        if (ex instanceof CrudException) {
            CrudException crudException = (CrudException) ex;
            HttpStatus status = mapStatus(crudException.getErrorCode());
            return new CrudHttpErrorDescriptor(
                status,
                new CrudErrorEnvelope(
                    (crudException instanceof CommandActionRejectedException)
                        ? ((CommandActionRejectedException) crudException).getCode() : crudException.getErrorCode().name(),
                    status.is5xxServerError() ? "服务暂时不可用，请稍后重试" : crudException.getMessage(),
                    crudException.getStage() == null ? CrudErrorStage.HTTP_CONTRACT : crudException.getStage(),
                    status.is5xxServerError() ? null : crudException.getRouteKey(),
                    status.is5xxServerError() || crudException.getReason() == null
                        ? crudException.getErrorCode().name() : crudException.getReason()
                )
            );
        }
        return new CrudHttpErrorDescriptor(
            HttpStatus.INTERNAL_SERVER_ERROR,
            new CrudErrorEnvelope(
                CrudErrorCode.INTERNAL_ERROR.name(),
                "服务暂时不可用，请稍后重试",
                CrudErrorStage.UNKNOWN,
                null,
                CrudErrorCode.INTERNAL_ERROR.name()
            )
        );
    }

    private static HttpStatus mapStatus(CrudErrorCode errorCode) {
        switch (errorCode) {
            case BUSINESS_REJECTED:
            case VALIDATION_ERROR:
            case TYPE_RESOLUTION_FAILED:
            case ENTITY_SCOPE_ILLEGAL:
            case UNSUPPORTED_QUERY_STRATEGY:
            case IDEMPOTENCY_KEY_REQUIRED:
                return HttpStatus.BAD_REQUEST;
            case METHOD_NOT_ALLOWED:
                return HttpStatus.METHOD_NOT_ALLOWED;
            case ENTITY_NOT_EXPOSED:
            case ROUTE_NOT_FOUND:
                return HttpStatus.NOT_FOUND;
            case ROUTE_AMBIGUOUS:
            case WRITE_CONFLICT:
            case QUERY_NOT_UNIQUE:
            case IDEMPOTENCY_IN_PROGRESS:
            case IDEMPOTENCY_PAYLOAD_CONFLICT:
                return HttpStatus.CONFLICT;
            case PERMISSION_DENIED:
            case DATA_SCOPE_DENIED:
                return HttpStatus.FORBIDDEN;
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
