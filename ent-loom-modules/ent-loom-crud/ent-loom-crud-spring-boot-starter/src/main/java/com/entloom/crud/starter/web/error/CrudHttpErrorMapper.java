package com.entloom.crud.starter.web.error;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.api.model.CrudErrorEnvelope;
import com.entloom.crud.core.exception.CrudException;
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
            return new CrudHttpErrorDescriptor(
                mapStatus(crudException.getErrorCode()),
                new CrudErrorEnvelope(
                    crudException.getErrorCode().name(),
                    crudException.getMessage(),
                    crudException.getStage() == null ? CrudErrorStage.HTTP_CONTRACT : crudException.getStage(),
                    crudException.getRouteKey(),
                    crudException.getReason() == null ? crudException.getErrorCode().name() : crudException.getReason()
                )
            );
        }
        return new CrudHttpErrorDescriptor(
            HttpStatus.INTERNAL_SERVER_ERROR,
            new CrudErrorEnvelope(
                CrudErrorCode.INTERNAL_ERROR.name(),
                ex.getMessage(),
                CrudErrorStage.UNKNOWN,
                null,
                CrudErrorCode.INTERNAL_ERROR.name()
            )
        );
    }

    private static HttpStatus mapStatus(CrudErrorCode errorCode) {
        switch (errorCode) {
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
