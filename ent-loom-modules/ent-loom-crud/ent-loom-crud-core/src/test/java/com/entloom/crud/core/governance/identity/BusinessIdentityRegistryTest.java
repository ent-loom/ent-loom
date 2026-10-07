package com.entloom.crud.core.governance.identity;

import com.entloom.crud.api.access.AccessContext;
import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessIdentityRegistryTest {
    @Test
    void registration_should_reject_invalid_code_type_or_resolver() {
        for (String code : Arrays.asList(null, "", " ", " merchant", "merchant ")) {
            assertThrows(ValidationException.class, () ->
                new BusinessIdentityRegistration<MerchantIdentity>(code, MerchantIdentity.class, (subject, identityId) -> null));
        }
        assertThrows(ValidationException.class, () ->
            new BusinessIdentityRegistration<MerchantIdentity>("merchant", null, (subject, identityId) -> null));
        assertThrows(ValidationException.class, () ->
            new BusinessIdentityRegistration<BusinessIdentity>("merchant", BusinessIdentity.class, (subject, identityId) -> null));
        assertThrows(ValidationException.class, () ->
            new BusinessIdentityRegistration<AbstractIdentity>("merchant", AbstractIdentity.class, (subject, identityId) -> null));
        assertThrows(ValidationException.class, () ->
            new BusinessIdentityRegistration<MerchantIdentity>("merchant", MerchantIdentity.class, null));
    }

    @Test
    void registry_should_reject_null_declarations() {
        assertThrows(ValidationException.class, () -> new BusinessIdentityRegistry(null));
        assertThrows(ValidationException.class, () ->
            new BusinessIdentityRegistry(Collections.<BusinessIdentityRegistration<?>>singletonList(null)));
    }

    @Test
    void registry_should_reject_duplicate_code_with_both_types_in_diagnostic() {
        BusinessIdentityRegistration<TeacherIdentity> teacher = new BusinessIdentityRegistration<TeacherIdentity>(
            "merchant", TeacherIdentity.class, (subject, identityId) -> new TeacherIdentity(identityId));
        ValidationException failure = assertThrows(ValidationException.class, () -> registry(merchant(), teacher));
        assertTrue(failure.getMessage().contains("merchant"));
        assertTrue(failure.getMessage().contains(MerchantIdentity.class.getName()));
        assertTrue(failure.getMessage().contains(TeacherIdentity.class.getName()));
        assertThrows(ValidationException.class, () -> registry(merchant(), merchant()));
    }

    @Test
    void registry_should_reject_duplicate_type_with_both_codes_in_diagnostic() {
        BusinessIdentityRegistration<MerchantIdentity> other = new BusinessIdentityRegistration<MerchantIdentity>(
            "another", MerchantIdentity.class, (subject, identityId) -> null);
        ValidationException failure = assertThrows(ValidationException.class, () -> registry(merchant(), other));
        assertTrue(failure.getMessage().contains(MerchantIdentity.class.getName()));
        assertTrue(failure.getMessage().contains("merchant"));
        assertTrue(failure.getMessage().contains("another"));
    }

    @Test
    void registry_should_freeze_declarations_without_retaining_source_collection() {
        List<BusinessIdentityRegistration<?>> source = new ArrayList<BusinessIdentityRegistration<?>>();
        source.add(merchant());
        BusinessIdentityRegistry registry = new BusinessIdentityRegistry(source);
        source.clear();
        assertEquals(1, registry.snapshot().size());
        assertThrows(UnsupportedOperationException.class, () -> registry.snapshot().clear());
        assertEquals("merchant-1", registry.resolve(subject("actor-1"), "merchant", "merchant-1").getIdentityId());
    }

    @Test
    void empty_registry_should_not_allow_unregistered_identity() {
        BusinessIdentityRegistry registry = registry();
        assertTrue(registry.snapshot().isEmpty());
        assertThrows(PermissionDeniedException.class, () -> registry.resolve(subject("actor-1"), "merchant", "merchant-1"));
    }

    @Test
    void invalid_selection_should_fail_before_invoking_resolver() {
        AtomicInteger calls = new AtomicInteger();
        BusinessIdentityRegistry registry = registry(new BusinessIdentityRegistration<MerchantIdentity>(
            "merchant", MerchantIdentity.class, (subject, identityId) -> {
                calls.incrementAndGet();
                return new MerchantIdentity("merchant", identityId);
            }));
        assertThrows(PermissionDeniedException.class, () -> registry.resolve(null, "merchant", "merchant-1"));
        assertThrows(PermissionDeniedException.class, () -> registry.resolve(subject("actor-1"), "unknown", "merchant-1"));
        for (String coordinate : Arrays.asList(null, "", " ", " merchant", "merchant ")) {
            assertThrows(PermissionDeniedException.class, () -> registry.resolve(subject("actor-1"), coordinate, "merchant-1"));
            assertThrows(PermissionDeniedException.class, () -> registry.resolve(subject("actor-1"), "merchant", coordinate));
        }
        assertEquals(0, calls.get());
    }

    @Test
    void resolved_identity_should_preserve_project_fields_and_actual_subject() {
        MerchantIdentity identity = new MerchantIdentity("merchant", "merchant-1");
        AccessSubject actualSubject = subject("actor-1");
        AtomicReference<AccessSubject> seenSubject = new AtomicReference<AccessSubject>();
        BusinessIdentityRegistry registry = registry(new BusinessIdentityRegistration<MerchantIdentity>(
            "merchant", MerchantIdentity.class, (subject, identityId) -> {
                seenSubject.set(subject);
                assertEquals("merchant-1", identityId);
                return identity;
            }));
        VisitorContext visitor = VisitorContext.authenticated(actualSubject,
            registry.resolve(actualSubject, "merchant", "merchant-1"), "common", "request-1", "trace-1");
        AccessContext<MerchantIdentity> context = AccessContext.from(visitor, MerchantIdentity.class);
        assertSame(actualSubject, seenSubject.get());
        assertSame(actualSubject, context.getSubject());
        assertSame(identity, context.getIdentity());
        assertEquals("项目商家", context.getIdentity().getDisplayName());
        assertFalse(visitor.findIdentity(TeacherIdentity.class).isPresent());
    }

    @Test
    void identity_selection_should_verify_ownership_instead_of_trusting_identifier() {
        BusinessIdentityRegistry registry = registry(merchant());
        assertThrows(PermissionDeniedException.class, () -> registry.resolve(subject("actor-2"), "merchant", "merchant-1"));
        assertThrows(PermissionDeniedException.class, () -> registry.resolve(subject("actor-1"), "merchant", "merchant-2"));
        assertEquals("merchant-1", registry.resolve(subject("actor-1"), "merchant", "merchant-1").getIdentityId());
    }

    @Test
    void identity_resolution_should_not_cache_revoked_membership() {
        AtomicInteger calls = new AtomicInteger();
        BusinessIdentityRegistry registry = registry(new BusinessIdentityRegistration<MerchantIdentity>(
            "merchant", MerchantIdentity.class, (subject, identityId) ->
                calls.incrementAndGet() == 1 ? new MerchantIdentity("merchant", identityId) : null));
        registry.resolve(subject("actor-1"), "merchant", "merchant-1");
        assertThrows(PermissionDeniedException.class, () -> registry.resolve(subject("actor-1"), "merchant", "merchant-1"));
        assertEquals(2, calls.get());
    }

    @Test
    void resolver_returning_wrong_code_or_id_should_fail_closed() {
        for (MerchantIdentity invalid : Arrays.asList(
            new MerchantIdentity("teacher", "merchant-1"),
            new MerchantIdentity(null, "merchant-1"),
            new MerchantIdentity("merchant", "merchant-2"),
            new MerchantIdentity("merchant", null)
        )) {
            BusinessIdentityRegistry registry = registry(new BusinessIdentityRegistration<MerchantIdentity>(
                "merchant", MerchantIdentity.class, (subject, identityId) -> invalid));
            assertThrows(PermissionDeniedException.class, () -> registry.resolve(subject("actor-1"), "merchant", "merchant-1"));
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void resolver_returning_wrong_runtime_type_should_fail_closed() {
        BusinessIdentityResolver wrongResolver = (subject, identityId) -> new TeacherIdentity(identityId);
        BusinessIdentityRegistry registry = registry(new BusinessIdentityRegistration(
            "merchant", MerchantIdentity.class, wrongResolver));
        PermissionDeniedException failure = assertThrows(PermissionDeniedException.class, () ->
            registry.resolve(subject("actor-1"), "merchant", "merchant-1"));
        assertTrue(failure.getMessage().contains(TeacherIdentity.class.getName()));
    }

    @Test
    void resolver_failure_should_propagate_without_anonymous_fallback() {
        IllegalStateException failure = new IllegalStateException("身份事实查询失败");
        BusinessIdentityRegistry registry = registry(new BusinessIdentityRegistration<MerchantIdentity>(
            "merchant", MerchantIdentity.class, (subject, identityId) -> {
                throw failure;
            }));
        assertSame(failure, assertThrows(IllegalStateException.class, () ->
            registry.resolve(subject("actor-1"), "merchant", "merchant-1")));
    }

    @Test
    void resolver_should_receive_service_and_device_sources_without_granting_default_identity() {
        BusinessIdentityRegistry registry = registry(merchant());
        for (AccessSubjectType type : Arrays.asList(AccessSubjectType.SERVICE, AccessSubjectType.DEVICE)) {
            assertThrows(PermissionDeniedException.class, () ->
                registry.resolve(new AccessSubject("actor-1", type), "merchant", "merchant-1"));
        }
    }

    private static BusinessIdentityRegistry registry(BusinessIdentityRegistration<?>... registrations) {
        return new BusinessIdentityRegistry(Arrays.asList(registrations));
    }

    private static AccessSubject subject(String subjectId) {
        return new AccessSubject(subjectId, AccessSubjectType.HUMAN);
    }

    private static BusinessIdentityRegistration<MerchantIdentity> merchant() {
        return new BusinessIdentityRegistration<MerchantIdentity>("merchant", MerchantIdentity.class,
            (subject, identityId) -> subject.equals(subject("actor-1")) && "merchant-1".equals(identityId)
                ? new MerchantIdentity("merchant", identityId) : null);
    }

    private abstract static class AbstractIdentity implements BusinessIdentity {
    }

    private static final class MerchantIdentity implements BusinessIdentity {
        private final String code;
        private final String identityId;

        private MerchantIdentity(String code, String identityId) {
            this.code = code;
            this.identityId = identityId;
        }

        public String getDisplayName() {
            return "项目商家";
        }

        @Override
        public String getIdentityCode() {
            return code;
        }

        @Override
        public String getIdentityId() {
            return identityId;
        }
    }

    private static final class TeacherIdentity implements BusinessIdentity {
        private final String identityId;

        private TeacherIdentity(String identityId) {
            this.identityId = identityId;
        }

        @Override
        public String getIdentityCode() {
            return "teacher";
        }

        @Override
        public String getIdentityId() {
            return identityId;
        }
    }
}
