package com.entloom.crud.core.governance.admission;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.AuthenticationMode;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessContextScopeTest {
    private final AccessAdmissionService service = new AccessAdmissionService(new BusinessIdentityRegistry(Collections.emptyList()));

    @Test
    void nested_scope_restores_previous_context_even_after_failure() {
        AccessAdmission outer = admit("actor-1");
        AccessAdmission inner = admit("actor-2");
        assertThrows(IllegalStateException.class, AccessContextScope::requireVisitor);
        AccessContextScope.withAdmission(outer, () -> {
            assertSame(outer, AccessContextScope.currentAdmission().get());
            assertThrows(IllegalStateException.class, () -> AccessContextScope.withAdmission(inner, () -> {
                assertSame(inner, AccessContextScope.currentAdmission().get());
                throw new IllegalStateException("失败");
            }));
            assertSame(outer.getVisitorContext(), AccessContextScope.requireVisitor());
        });
        assertFalse(AccessContextScope.currentAdmission().isPresent());
    }

    @Test
    void context_does_not_flow_into_new_or_reused_worker_thread() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AccessAdmission first = admit("actor-1");
            AccessAdmission second = admit("actor-2");
            AccessContextScope.withAdmission(first, () -> {
                try {
                    assertFalse(executor.submit(() -> AccessContextScope.currentVisitor().isPresent()).get());
                    assertEquals("actor-1", AccessContextScope.requireVisitor().getSubject().get().getSubjectId());
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });
            executor.submit(() -> AccessContextScope.withAdmission(second, () ->
                assertEquals("actor-2", AccessContextScope.requireVisitor().getSubject().get().getSubjectId()))).get();
            assertFalse(executor.submit(() -> AccessContextScope.currentVisitor().isPresent()).get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void explicit_scope_restores_outer_context_and_rejects_cross_thread_close() throws Exception {
        AccessAdmission outer = admit("actor-1");
        AccessAdmission inner = admit("actor-2");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (AccessContextScope.Scope first = AccessContextScope.enter(outer)) {
            AccessContextScope.Scope second = AccessContextScope.enter(inner);
            assertThrows(IllegalStateException.class, first::close);
            ExecutionException failure = assertThrows(ExecutionException.class, () -> executor.submit(second::close).get());
            assertSame(IllegalStateException.class, failure.getCause().getClass());
            assertSame(inner, AccessContextScope.currentAdmission().get());
            second.close();
            second.close();
            assertSame(outer, AccessContextScope.currentAdmission().get());
        } finally {
            executor.shutdownNow();
        }
        assertFalse(AccessContextScope.currentAdmission().isPresent());
    }

    @Test
    void suspended_scope_should_hide_outer_identity_and_restore_it_after_nested_failure() {
        AccessAdmission outer = admit("actor-1");
        AccessContextScope.withAdmission(outer, () -> {
            try (AccessContextScope.Scope ignored = AccessContextScope.suspend()) {
                assertFalse(AccessContextScope.currentVisitor().isPresent());
                assertFalse(AccessContextScope.isAuthorizationRequired());
                assertThrows(IllegalStateException.class, () -> AccessContextScope.withAdmission(admit("actor-2"), () -> {
                    throw new IllegalStateException("内层失败");
                }));
                assertFalse(AccessContextScope.currentVisitor().isPresent());
            }
            assertSame(outer, AccessContextScope.currentAdmission().get());
            assertTrue(AccessContextScope.isAuthorizationRequired());
        });
        assertFalse(AccessContextScope.currentVisitor().isPresent());
        assertFalse(AccessContextScope.isAuthorizationRequired());
    }

    private AccessAdmission admit(String subjectId) {
        return service.admit(AuthenticationMode.REQUIRED,
            () -> CredentialAuthentication.authenticated(new AccessSubject(subjectId, AccessSubjectType.HUMAN)),
            "internal", "request-1", "trace-1");
    }
}
