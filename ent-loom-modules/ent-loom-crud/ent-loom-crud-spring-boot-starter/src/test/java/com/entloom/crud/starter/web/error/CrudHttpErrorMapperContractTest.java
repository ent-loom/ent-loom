package com.entloom.crud.starter.web.error;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.core.exception.CrudException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * CRUD 错误 code、stage、reason 与 HTTP 状态映射合同。
 */
class CrudHttpErrorMapperContractTest {
    @Test
    void every_error_code_should_have_a_stable_http_status_mapping() {
        Map<CrudErrorCode, HttpStatus> expectedStatuses = expectedStatuses();
        Assertions.assertEquals(
            EnumSet.allOf(CrudErrorCode.class),
            EnumSet.copyOf(expectedStatuses.keySet()),
            "HTTP 错误矩阵必须覆盖全部 CrudErrorCode"
        );

        for (CrudErrorCode code : CrudErrorCode.values()) {
            CrudException exception = new CrudException(code, "测试错误")
                .withStage(CrudErrorStage.EXECUTE)
                .withReason("TEST_" + code.name())
                .withRouteKey("TestOrderEntity|TEST");
            CrudHttpErrorDescriptor descriptor = CrudHttpErrorMapper.map(exception);

            Assertions.assertEquals(expectedStatuses.get(code), descriptor.getStatus(), code.name());
            Assertions.assertEquals(code.name(), descriptor.getError().getCode(), code.name() + ".code");
            Assertions.assertEquals(CrudErrorStage.EXECUTE, descriptor.getError().getStage(), code.name() + ".stage");
            Assertions.assertEquals("TEST_" + code.name(), descriptor.getError().getReason(), code.name() + ".reason");
            Assertions.assertEquals("TestOrderEntity|TEST", descriptor.getError().getRouteKey(), code.name() + ".routeKey");
        }
    }

    @Test
    void representative_stage_and_reason_should_match_http_contract() {
        assertMapping(
            CrudErrorCode.VALIDATION_ERROR,
            CrudErrorStage.HTTP_CONTRACT,
            "VALIDATION_ERROR",
            HttpStatus.BAD_REQUEST
        );
        assertMapping(
            CrudErrorCode.ROUTE_NOT_FOUND,
            CrudErrorStage.ROUTE,
            "ROUTE_NOT_FOUND",
            HttpStatus.NOT_FOUND
        );
        assertMapping(
            CrudErrorCode.PERMISSION_DENIED,
            CrudErrorStage.GOVERNANCE,
            "PERMISSION_DENIED",
            HttpStatus.FORBIDDEN
        );
        assertMapping(
            CrudErrorCode.INTERNAL_ERROR,
            CrudErrorStage.UNKNOWN,
            "INTERNAL_ERROR",
            HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @Test
    void missing_stage_or_reason_should_use_contract_defaults() {
        CrudHttpErrorDescriptor frameworkError = CrudHttpErrorMapper.map(
            new CrudException(CrudErrorCode.VALIDATION_ERROR, "测试错误")
        );
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, frameworkError.getStatus());
        Assertions.assertEquals(CrudErrorStage.HTTP_CONTRACT, frameworkError.getError().getStage());
        Assertions.assertEquals("VALIDATION_ERROR", frameworkError.getError().getReason());

        CrudHttpErrorDescriptor unexpectedError = CrudHttpErrorMapper.map(new IllegalStateException("测试异常"));
        Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, unexpectedError.getStatus());
        Assertions.assertEquals(CrudErrorCode.INTERNAL_ERROR.name(), unexpectedError.getError().getCode());
        Assertions.assertEquals(CrudErrorStage.UNKNOWN, unexpectedError.getError().getStage());
        Assertions.assertEquals(CrudErrorCode.INTERNAL_ERROR.name(), unexpectedError.getError().getReason());
    }

    private void assertMapping(
        CrudErrorCode code,
        CrudErrorStage stage,
        String reason,
        HttpStatus status
    ) {
        CrudException exception = new CrudException(code, "测试错误")
            .withStage(stage)
            .withReason(reason);
        CrudHttpErrorDescriptor descriptor = CrudHttpErrorMapper.map(exception);

        Assertions.assertEquals(status, descriptor.getStatus(), code.name() + ".status");
        Assertions.assertEquals(code.name(), descriptor.getError().getCode(), code.name() + ".code");
        Assertions.assertEquals(stage, descriptor.getError().getStage(), code.name() + ".stage");
        Assertions.assertEquals(reason, descriptor.getError().getReason(), code.name() + ".reason");
    }

    private Map<CrudErrorCode, HttpStatus> expectedStatuses() {
        EnumMap<CrudErrorCode, HttpStatus> statuses = new EnumMap<>(CrudErrorCode.class);
        put(statuses, HttpStatus.BAD_REQUEST,
            CrudErrorCode.VALIDATION_ERROR,
            CrudErrorCode.TYPE_RESOLUTION_FAILED,
            CrudErrorCode.ENTITY_SCOPE_ILLEGAL,
            CrudErrorCode.UNSUPPORTED_QUERY_STRATEGY,
            CrudErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        put(statuses, HttpStatus.FORBIDDEN,
            CrudErrorCode.PERMISSION_DENIED,
            CrudErrorCode.DATA_SCOPE_DENIED);
        put(statuses, HttpStatus.NOT_FOUND,
            CrudErrorCode.ENTITY_NOT_EXPOSED,
            CrudErrorCode.ROUTE_NOT_FOUND);
        put(statuses, HttpStatus.METHOD_NOT_ALLOWED, CrudErrorCode.METHOD_NOT_ALLOWED);
        put(statuses, HttpStatus.CONFLICT,
            CrudErrorCode.ROUTE_AMBIGUOUS,
            CrudErrorCode.QUERY_NOT_UNIQUE,
            CrudErrorCode.IDEMPOTENCY_IN_PROGRESS,
            CrudErrorCode.IDEMPOTENCY_PAYLOAD_CONFLICT);
        put(statuses, HttpStatus.INTERNAL_SERVER_ERROR,
            CrudErrorCode.ATTRIBUTE_CONTRIBUTION_FAILED,
            CrudErrorCode.UNSUPPORTED_FORMAT,
            CrudErrorCode.UNSUPPORTED_OPERATION,
            CrudErrorCode.SCENE_NOT_FOUND,
            CrudErrorCode.FILE_SERVICE_UNAVAILABLE,
            CrudErrorCode.FILE_NOT_FOUND,
            CrudErrorCode.FILE_EXPIRED,
            CrudErrorCode.FILE_METADATA_INVALID,
            CrudErrorCode.TASK_NOT_FOUND,
            CrudErrorCode.DOWNLOAD_NOT_READY,
            CrudErrorCode.SYNC_LIMIT_EXCEEDED,
            CrudErrorCode.ROW_VALIDATION_FAILED,
            CrudErrorCode.INTERNAL_ERROR);
        return statuses;
    }

    private void put(Map<CrudErrorCode, HttpStatus> statuses, HttpStatus status, CrudErrorCode... codes) {
        for (CrudErrorCode code : codes) {
            statuses.put(code, status);
        }
    }
}
