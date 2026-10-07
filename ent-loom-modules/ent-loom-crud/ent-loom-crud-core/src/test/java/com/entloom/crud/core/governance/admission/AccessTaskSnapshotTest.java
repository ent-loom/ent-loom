package com.entloom.crud.core.governance.admission;

import com.entloom.crud.api.access.AccessBranch;
import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.AuthenticationMode;
import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistration;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccessTaskSnapshotTest {
    private final AccessSubject actor = new AccessSubject("actor-1", AccessSubjectType.HUMAN);
    private final AtomicBoolean membershipActive = new AtomicBoolean(true);
    private final AccessAdmissionService service = new AccessAdmissionService(new BusinessIdentityRegistry(
        Collections.singletonList(new BusinessIdentityRegistration<MerchantIdentity>("merchant", MerchantIdentity.class,
            (subject, identityId) -> membershipActive.get() && actor.equals(subject) && "merchant-1".equals(identityId)
                ? new MerchantIdentity(identityId) : null))));

    @Test
    void restore_reauthenticates_actor_and_resolves_current_identity() {
        AccessTaskSnapshot snapshot = AccessTaskSnapshot.capture(service.admit(AuthenticationMode.REQUIRED,
            () -> selected(actor), "internal", "request-1", "trace-1"));
        assertEquals(actor, snapshot.getInitiator());
        assertEquals("merchant", snapshot.getIdentityCode());
        assertEquals("merchant-1", snapshot.getIdentityId());
        AccessAdmission restored = snapshot.restore(service, () -> selected(actor));
        assertEquals("merchant-1", restored.getVisitorContext().requireIdentity(MerchantIdentity.class).getIdentityId());
        assertEquals("request-1", restored.getVisitorContext().getRequestId());
        assertThrows(PermissionDeniedException.class, () -> snapshot.restore(service, CredentialAuthentication::absent));
        assertThrows(PermissionDeniedException.class, () -> snapshot.restore(service, CredentialAuthentication::invalid));
        assertThrows(PermissionDeniedException.class, () -> snapshot.restore(service,
            () -> CredentialAuthentication.authenticated(new AccessSubject("actor-2", AccessSubjectType.HUMAN))));
        assertThrows(PermissionDeniedException.class, () -> snapshot.restore(service,
            () -> CredentialAuthentication.authenticated(actor)));
        membershipActive.set(false);
        assertThrows(PermissionDeniedException.class, () -> snapshot.restore(service, () -> selected(actor)));
    }

    @Test
    void public_snapshot_never_promotes_valid_credential_to_identity() {
        AccessTaskSnapshot snapshot = AccessTaskSnapshot.capture(service.admit(AuthenticationMode.ANONYMOUS,
            () -> selected(actor), "public", null, null));
        AccessAdmission restored = snapshot.restore(service, () -> selected(actor));
        assertEquals(AccessBranch.PUBLIC, restored.getBranch());
        assertFalse(restored.getVisitorContext().getSubject().isPresent());
        assertFalse(restored.getVisitorContext().getIdentity().isPresent());
        assertEquals(actor, restored.getAuthenticatedSubject().get());
        assertThrows(PermissionDeniedException.class, () -> snapshot.restore(service, CredentialAuthentication::absent));
    }

    @Test
    void absent_public_snapshot_rejects_new_authenticated_subject() {
        AccessTaskSnapshot snapshot = AccessTaskSnapshot.capture(service.admit(AuthenticationMode.OPTIONAL,
            CredentialAuthentication::absent, "public", null, null));
        assertEquals(AccessBranch.PUBLIC, snapshot.restore(service, CredentialAuthentication::absent).getBranch());
        assertThrows(PermissionDeniedException.class, () -> snapshot.restore(service, () -> selected(actor)));
    }

    private CredentialAuthentication selected(AccessSubject subject) {
        return CredentialAuthentication.authenticated(subject, "merchant", "merchant-1");
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
