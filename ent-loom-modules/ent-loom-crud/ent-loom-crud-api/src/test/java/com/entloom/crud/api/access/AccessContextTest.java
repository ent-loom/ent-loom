package com.entloom.crud.api.access;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessContextTest {
    @Test
    void subject_should_distinguish_sources_and_normalize_identifier() {
        for (AccessSubjectType type : AccessSubjectType.values()) {
            AccessSubject subject = new AccessSubject(" actor-1 ", type);
            assertEquals("actor-1", subject.getSubjectId());
            assertSame(type, subject.getType());
            assertEquals(subject, new AccessSubject("actor-1", type));
            assertEquals(subject.hashCode(), new AccessSubject("actor-1", type).hashCode());
        }
        assertNotEquals(new AccessSubject("actor-1", AccessSubjectType.HUMAN),
            new AccessSubject("actor-1", AccessSubjectType.SERVICE));
    }

    @Test
    void subject_should_reject_missing_identifier_or_source() {
        assertThrows(IllegalArgumentException.class, () -> new AccessSubject(null, AccessSubjectType.HUMAN));
        assertThrows(IllegalArgumentException.class, () -> new AccessSubject("  ", AccessSubjectType.HUMAN));
        assertThrows(NullPointerException.class, () -> new AccessSubject("actor-1", null));
    }

    @Test
    void anonymous_should_not_fabricate_subject_or_identity() {
        VisitorContext context = VisitorContext.anonymous(" common ", " request-1 ", " trace-1 ");
        assertTrue(context.isAnonymous());
        assertFalse(context.getSubject().isPresent());
        assertFalse(context.getIdentity().isPresent());
        assertEquals("common", context.getAccessEntry());
        assertEquals("request-1", context.getRequestId());
        assertEquals("trace-1", context.getTraceId());
        assertFalse(context.findIdentity(MerchantIdentity.class).isPresent());
        assertThrows(IllegalStateException.class, () -> context.requireIdentity(MerchantIdentity.class));
        assertThrows(IllegalStateException.class, () -> AccessContext.from(context, MerchantIdentity.class));
    }

    @Test
    void authenticated_should_allow_no_current_business_identity_but_not_no_subject() {
        VisitorContext context = VisitorContext.authenticated(subject(), null, "common", null, " ");
        assertFalse(context.isAnonymous());
        assertEquals(subject(), context.getSubject().get());
        assertFalse(context.getIdentity().isPresent());
        assertEquals(null, context.getRequestId());
        assertEquals(null, context.getTraceId());
        assertThrows(IllegalStateException.class, () -> AccessContext.from(context, MerchantIdentity.class));
        assertThrows(NullPointerException.class, () ->
            VisitorContext.authenticated(null, new MerchantIdentity("merchant-1"), "common", null, null));
    }

    @Test
    void fixed_context_should_retain_actual_subject_and_project_identity() {
        MerchantIdentity identity = new MerchantIdentity("merchant-1");
        VisitorContext visitor = VisitorContext.authenticated(subject(), identity, "merchant", "request-1", "trace-1");
        AccessContext<MerchantIdentity> context = AccessContext.from(visitor, MerchantIdentity.class);
        assertSame(visitor, context.getVisitorContext());
        assertSame(identity, visitor.requireIdentity(MerchantIdentity.class));
        assertSame(identity, context.getIdentity());
        assertEquals("actor-1", context.getSubject().getSubjectId());
        assertEquals("merchant-1", context.getIdentity().getIdentityId());
        assertEquals("merchant", context.getIdentity().getIdentityCode());
    }

    @Test
    void identity_lookup_should_never_switch_or_merge_current_identity() {
        VisitorContext context = VisitorContext.authenticated(subject(), new MerchantIdentity("merchant-1"),
            "common", null, null);
        assertFalse(context.findIdentity(TeacherIdentity.class).isPresent());
        assertThrows(IllegalStateException.class, () -> context.requireIdentity(TeacherIdentity.class));
        assertThrows(IllegalStateException.class, () -> AccessContext.from(context, TeacherIdentity.class));
        assertEquals("merchant-1", context.requireIdentity(MerchantIdentity.class).getIdentityId());
    }

    @Test
    void context_should_reject_empty_entry_or_identity_coordinates() {
        assertThrows(IllegalArgumentException.class, () -> VisitorContext.anonymous(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> VisitorContext.anonymous(" ", null, null));
        assertThrows(IllegalArgumentException.class, () ->
            VisitorContext.authenticated(subject(), new MerchantIdentity(" "), "common", null, null));
        assertThrows(IllegalArgumentException.class, () ->
            VisitorContext.authenticated(subject(), new MerchantIdentity(null), "common", null, null));
        BusinessIdentity invalidCode = new BusinessIdentity() {
            @Override
            public String getIdentityCode() {
                return " ";
            }

            @Override
            public String getIdentityId() {
                return "identity-1";
            }
        };
        assertThrows(IllegalArgumentException.class, () ->
            VisitorContext.authenticated(subject(), invalidCode, "common", null, null));
    }

    @Test
    void contexts_should_not_share_current_identity_between_calls() {
        VisitorContext first = VisitorContext.authenticated(subject(), new MerchantIdentity("merchant-1"),
            "common", null, null);
        VisitorContext second = VisitorContext.authenticated(new AccessSubject("actor-2", AccessSubjectType.HUMAN),
            new TeacherIdentity(), "common", null, null);
        assertFalse(first.findIdentity(TeacherIdentity.class).isPresent());
        assertFalse(second.findIdentity(MerchantIdentity.class).isPresent());
        assertEquals("actor-1", first.getSubject().get().getSubjectId());
        assertEquals("actor-2", second.getSubject().get().getSubjectId());
    }

    @Test
    void context_should_reject_missing_type_or_visitor() {
        VisitorContext context = VisitorContext.anonymous("common", null, null);
        assertThrows(NullPointerException.class, () -> context.findIdentity(null));
        assertThrows(NullPointerException.class, () -> context.requireIdentity(null));
        assertThrows(NullPointerException.class, () -> AccessContext.from(context, null));
        assertThrows(NullPointerException.class, () -> AccessContext.from(null, MerchantIdentity.class));
    }

    private static AccessSubject subject() {
        return new AccessSubject("actor-1", AccessSubjectType.HUMAN);
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

    private static final class TeacherIdentity implements BusinessIdentity {
        @Override
        public String getIdentityCode() {
            return "teacher";
        }

        @Override
        public String getIdentityId() {
            return "teacher-1";
        }
    }
}
