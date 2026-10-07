package com.entloom.crud.api.access;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthorizationRequestTest {
    @Test
    void binds_operation_target_and_invocation_to_trusted_context() {
        VisitorContext visitor = VisitorContext.authenticated(
            new AccessSubject("actor-1", AccessSubjectType.HUMAN), null, "trusted-entry", "request-1", "trace-1");
        BusinessOperation operation = new BusinessOperation("order", "refund");
        AuthorizationRequest first = new AuthorizationRequest(visitor, operation, "order-1");
        AuthorizationRequest second = new AuthorizationRequest(visitor, operation, "order-1");
        assertSame(visitor, first.getVisitor());
        assertEquals(operation, first.getOperation());
        assertEquals("order-1", first.getTargetId());
        assertNotEquals(first.getInvocationId(), second.getInvocationId());
        assertThrows(IllegalArgumentException.class, () -> new AuthorizationRequest(visitor, operation, " order-1"));
    }
}
