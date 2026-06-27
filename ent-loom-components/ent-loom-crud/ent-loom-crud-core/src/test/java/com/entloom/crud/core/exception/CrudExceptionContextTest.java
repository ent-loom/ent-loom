package com.entloom.crud.core.exception;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.CrudErrorStage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CrudExceptionContextTest {
    @Test
    void should_fill_missing_stage_route_key_and_reason_for_crud_exception() {
        CrudException ex = new CrudException(CrudErrorCode.ROUTE_NOT_FOUND, "missing");

        RuntimeException enriched = CrudExceptionContext.enrich(ex, CrudErrorStage.ROUTE, "Order|PAGE", "ROUTE_MISS");

        Assertions.assertSame(ex, enriched);
        Assertions.assertEquals(CrudErrorStage.ROUTE, ex.getStage());
        Assertions.assertEquals("Order|PAGE", ex.getRouteKey());
        Assertions.assertEquals("ROUTE_MISS", ex.getReason());
    }

    @Test
    void should_not_override_existing_context_values() {
        CrudException ex = new CrudException(CrudErrorCode.VALIDATION_ERROR, "invalid")
            .withStage(CrudErrorStage.NORMALIZE)
            .withRouteKey("Existing|KEY")
            .withReason("EXISTING_REASON");

        CrudExceptionContext.enrich(ex, CrudErrorStage.ROUTE, "New|KEY", "NEW_REASON");

        Assertions.assertEquals(CrudErrorStage.NORMALIZE, ex.getStage());
        Assertions.assertEquals("Existing|KEY", ex.getRouteKey());
        Assertions.assertEquals("EXISTING_REASON", ex.getReason());
    }

    @Test
    void should_use_error_code_as_default_reason_when_reason_missing() {
        CrudException ex = new CrudException(CrudErrorCode.PERMISSION_DENIED, "denied");

        CrudExceptionContext.enrich(ex, CrudErrorStage.GOVERNANCE, "Order|PAGE");

        Assertions.assertEquals("PERMISSION_DENIED", ex.getReason());
    }

    @Test
    void should_return_non_crud_exception_unchanged() {
        RuntimeException ex = new IllegalStateException("boom");

        RuntimeException enriched = CrudExceptionContext.enrich(ex, CrudErrorStage.EXECUTE, "Order|PAGE");

        Assertions.assertSame(ex, enriched);
    }
}
