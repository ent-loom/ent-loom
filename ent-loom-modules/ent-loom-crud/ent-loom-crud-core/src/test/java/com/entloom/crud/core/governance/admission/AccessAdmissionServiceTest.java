package com.entloom.crud.core.governance.admission;

import com.entloom.crud.api.access.AccessBranch;
import com.entloom.crud.api.access.AccessContext;
import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.AuthenticationMode;
import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistration;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.core.governance.identity.BusinessIdentityResolver;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessAdmissionServiceTest {
    @Test
    void credential_status_should_not_fabricate_subject_or_identity() {
        for (CredentialAuthentication authentication : Arrays.asList(CredentialAuthentication.absent(), CredentialAuthentication.invalid())) {
            assertNull(authentication.getSubject());
            assertNull(authentication.getIdentityCode());
            assertNull(authentication.getIdentityId());
        }
        assertSame(CredentialAuthentication.Status.ABSENT, CredentialAuthentication.absent().getStatus());
        assertSame(CredentialAuthentication.Status.INVALID, CredentialAuthentication.invalid().getStatus());
    }

    @Test
    void authenticated_factory_should_require_actual_subject_and_complete_identity_selection() {
        assertThrows(ValidationException.class, () -> CredentialAuthentication.authenticated(null));
        for (String invalid : Arrays.asList(null, "", " ", " merchant", "merchant ")) {
            assertThrows(ValidationException.class, () -> CredentialAuthentication.authenticated(subject(), invalid, "merchant-1"));
            assertThrows(ValidationException.class, () -> CredentialAuthentication.authenticated(subject(), "merchant", invalid));
        }
        CredentialAuthentication authentication = CredentialAuthentication.authenticated(subject());
        assertEquals(subject(), authentication.getSubject());
        assertNull(authentication.getIdentityCode());
        assertNull(authentication.getIdentityId());
    }

    @Test
    void absent_credential_matrix_should_reject_required_and_allow_only_public_branches() {
        AccessAdmissionService service = service();
        assertThrows(PermissionDeniedException.class, () -> admit(service, AuthenticationMode.REQUIRED, CredentialAuthentication.absent()));
        for (AuthenticationMode mode : Arrays.asList(AuthenticationMode.OPTIONAL, AuthenticationMode.ANONYMOUS)) {
            AccessAdmission admission = admit(service, mode, CredentialAuthentication.absent());
            assertSame(mode, admission.getAuthenticationMode());
            assertPublic(admission);
            assertFalse(admission.getAuthenticatedSubject().isPresent());
        }
    }

    @Test
    void valid_credential_matrix_should_select_authenticated_or_public_branch_explicitly() {
        AccessAdmissionService service = service();
        for (AuthenticationMode mode : AuthenticationMode.values()) {
            AccessAdmission admission = admit(service, mode, authenticated());
            assertEquals(subject(), admission.getAuthenticatedSubject().get());
            if (mode == AuthenticationMode.ANONYMOUS) {
                assertPublic(admission);
            } else {
                assertSame(AccessBranch.AUTHENTICATED, admission.getBranch());
                assertFalse(admission.getVisitorContext().isAnonymous());
                assertEquals(subject(), admission.getVisitorContext().getSubject().get());
                assertEquals("merchant-1", AccessContext.from(admission.getVisitorContext(), "merchant")
                    .getIdentity().getIdentityId());
            }
        }
    }

    @Test
    void invalid_credential_matrix_should_deny_including_anonymous_mode() {
        for (AuthenticationMode mode : AuthenticationMode.values()) {
            assertThrows(PermissionDeniedException.class, () -> admit(service(), mode, CredentialAuthentication.invalid()));
        }
    }

    @Test
    void default_mode_should_require_authentication() {
        AccessAdmissionService service = service();
        assertThrows(PermissionDeniedException.class, () ->
            service.admit(CredentialAuthentication::absent, "common", null, null));
        AccessAdmission admission = service.admit(AccessAdmissionServiceTest::authenticated, "common", null, null);
        assertSame(AuthenticationMode.REQUIRED, admission.getAuthenticationMode());
        assertSame(AccessBranch.AUTHENTICATED, admission.getBranch());
    }

    @Test
    void public_mode_should_authenticate_once_but_never_resolve_or_merge_selected_identity() {
        AtomicInteger authenticationCalls = new AtomicInteger();
        AtomicInteger identityCalls = new AtomicInteger();
        AccessAdmissionService service = service((subject, identityId) -> {
            identityCalls.incrementAndGet();
            throw new IllegalStateException("公共分支不应查询个性化身份");
        });
        AccessSubject actualSubject = subject();
        AccessAdmission admission = service.admit(AuthenticationMode.ANONYMOUS, () -> {
            authenticationCalls.incrementAndGet();
            return CredentialAuthentication.authenticated(actualSubject, "merchant", "merchant-1");
        }, "common", "request-1", "trace-1");
        assertEquals(1, authenticationCalls.get());
        assertEquals(0, identityCalls.get());
        assertSame(actualSubject, admission.getAuthenticatedSubject().get());
        assertPublic(admission);
    }

    @Test
    void public_mode_should_not_turn_unverified_identity_selection_into_authority() {
        AccessAdmission admission = admit(service(), AuthenticationMode.ANONYMOUS,
            CredentialAuthentication.authenticated(subject(), "unregistered", "forged-id"));
        assertPublic(admission);
        assertEquals(subject(), admission.getAuthenticatedSubject().get());
    }

    @Test
    void authenticated_modes_should_verify_selected_identity_ownership() {
        for (AuthenticationMode mode : Arrays.asList(AuthenticationMode.REQUIRED, AuthenticationMode.OPTIONAL)) {
            assertThrows(PermissionDeniedException.class, () -> admit(service(), mode,
                CredentialAuthentication.authenticated(new AccessSubject("actor-2", AccessSubjectType.HUMAN), "merchant", "merchant-1")));
            assertThrows(PermissionDeniedException.class, () -> admit(service(), mode,
                CredentialAuthentication.authenticated(subject(), "merchant", "merchant-2")));
            assertThrows(PermissionDeniedException.class, () -> admit(service(), mode,
                CredentialAuthentication.authenticated(subject(), "unknown", "merchant-1")));
        }
    }

    @Test
    void authenticated_subject_without_identity_should_not_receive_default_business_identity() {
        for (AuthenticationMode mode : Arrays.asList(AuthenticationMode.REQUIRED, AuthenticationMode.OPTIONAL)) {
            AccessAdmission admission = admit(service(), mode, CredentialAuthentication.authenticated(subject()));
            assertSame(AccessBranch.AUTHENTICATED, admission.getBranch());
            assertFalse(admission.getVisitorContext().getIdentity().isPresent());
            assertThrows(IllegalStateException.class, () -> admission.getVisitorContext().requireIdentity("merchant"));
        }
    }

    @Test
    void null_authentication_result_should_fail_closed_for_all_modes() {
        for (AuthenticationMode mode : AuthenticationMode.values()) {
            assertThrows(PermissionDeniedException.class, () -> admit(service(), mode, null));
        }
    }

    @Test
    void authentication_failure_should_propagate_without_anonymous_fallback() {
        IllegalStateException failure = new IllegalStateException("认证来源不可用");
        for (AuthenticationMode mode : AuthenticationMode.values()) {
            assertSame(failure, assertThrows(IllegalStateException.class, () -> service().admit(mode, () -> {
                throw failure;
            }, "common", null, null)));
        }
    }

    @Test
    void rejected_credentials_should_not_invoke_identity_resolver() {
        AtomicInteger identityCalls = new AtomicInteger();
        AccessAdmissionService service = service((subject, identityId) -> {
            identityCalls.incrementAndGet();
            return new MerchantIdentity(identityId);
        });
        for (AuthenticationMode mode : AuthenticationMode.values()) {
            assertThrows(PermissionDeniedException.class, () -> admit(service, mode, CredentialAuthentication.invalid()));
        }
        assertEquals(0, identityCalls.get());
    }

    @Test
    void service_should_authenticate_each_call_without_retaining_previous_identity() {
        AccessAdmissionService service = service();
        AtomicInteger calls = new AtomicInteger();
        AccessAuthenticationSource source = () -> calls.incrementAndGet() == 1 ? authenticated() : CredentialAuthentication.absent();
        AccessAdmission first = service.admit(AuthenticationMode.OPTIONAL, source, "common", null, null);
        AccessAdmission second = service.admit(AuthenticationMode.OPTIONAL, source, "common", null, null);
        assertSame(AccessBranch.AUTHENTICATED, first.getBranch());
        assertEquals("merchant-1", first.getVisitorContext().requireIdentity("merchant").getIdentityId());
        assertPublic(second);
        assertFalse(second.getAuthenticatedSubject().isPresent());
        assertEquals(2, calls.get());
    }

    @Test
    void service_should_preserve_trusted_entry_and_audit_ids_on_every_branch() {
        for (AuthenticationMode mode : AuthenticationMode.values()) {
            VisitorContext visitor = admit(service(), mode, authenticated()).getVisitorContext();
            assertEquals("common", visitor.getAccessEntry());
            assertEquals("request-1", visitor.getRequestId());
            assertEquals("trace-1", visitor.getTraceId());
        }
    }

    @Test
    void service_should_not_assign_default_identity_to_service_or_device_subjects() {
        for (AccessSubjectType type : Arrays.asList(AccessSubjectType.SERVICE, AccessSubjectType.DEVICE)) {
            AccessSubject subject = new AccessSubject("source-1", type);
            AccessAdmission admission = admit(service(), AuthenticationMode.REQUIRED, CredentialAuthentication.authenticated(subject));
            assertSame(subject, admission.getAuthenticatedSubject().get());
            assertSame(subject, admission.getVisitorContext().getSubject().get());
            assertFalse(admission.getVisitorContext().getIdentity().isPresent());
        }
    }

    @Test
    void service_should_reject_invalid_configuration() {
        assertThrows(ValidationException.class, () -> new AccessAdmissionService(null));
        assertThrows(ValidationException.class, () -> service().admit(null, CredentialAuthentication::absent, "common", null, null));
        assertThrows(ValidationException.class, () -> service().admit(AuthenticationMode.OPTIONAL, null, "common", null, null));
        assertThrows(IllegalArgumentException.class, () -> service().admit(AuthenticationMode.OPTIONAL, CredentialAuthentication::absent, " ", null, null));
    }

    private static AccessAdmission admit(AccessAdmissionService service, AuthenticationMode mode, CredentialAuthentication authentication) {
        return service.admit(mode, () -> authentication, "common", "request-1", "trace-1");
    }

    private static void assertPublic(AccessAdmission admission) {
        assertSame(AccessBranch.PUBLIC, admission.getBranch());
        assertTrue(admission.getVisitorContext().isAnonymous());
        assertFalse(admission.getVisitorContext().getSubject().isPresent());
        assertFalse(admission.getVisitorContext().getIdentity().isPresent());
        assertThrows(IllegalStateException.class, () -> AccessContext.from(admission.getVisitorContext(), "merchant"));
    }

    private static AccessSubject subject() {
        return new AccessSubject("actor-1", AccessSubjectType.HUMAN);
    }

    private static CredentialAuthentication authenticated() {
        return CredentialAuthentication.authenticated(subject(), "merchant", "merchant-1");
    }

    private static AccessAdmissionService service() {
        return service((subject, identityId) -> subject.equals(subject()) && "merchant-1".equals(identityId)
            ? new MerchantIdentity(identityId) : null);
    }

    private static AccessAdmissionService service(BusinessIdentityResolver<MerchantIdentity> resolver) {
        BusinessIdentityRegistration<?> registration = new BusinessIdentityRegistration<MerchantIdentity>(
            "merchant", MerchantIdentity.class, resolver);
        return new AccessAdmissionService(new BusinessIdentityRegistry(Collections.singletonList(registration)));
    }

    private static final class MerchantIdentity implements BusinessIdentity {
        private final String identityId;

        private MerchantIdentity(String identityId) {
            this.identityId = identityId;
        }

        @Override
        public String getIdentityCode() {
            return "merchant";
        }

        @Override
        public String getIdentityId() {
            return identityId;
        }
    }
}
