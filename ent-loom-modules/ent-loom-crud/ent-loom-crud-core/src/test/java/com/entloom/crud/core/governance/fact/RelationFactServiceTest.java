package com.entloom.crud.core.governance.fact;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistration;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.core.governance.policy.BusinessOperationCatalog;
import com.entloom.crud.core.governance.policy.BusinessOperationDeclaration;
import com.entloom.crud.core.governance.policy.EntityPolicyBinding;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyTemplate;
import com.entloom.crud.core.governance.policy.PolicyIdentityBranch;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationFactServiceTest {
    @Test
    void preserves_paired_facts_and_freezes_the_result() {
        List<Map<String, String>> source = new ArrayList<Map<String, String>>(Arrays.asList(
            pair("school-a", "class-1"), pair("school-b", "class-2")));
        RelationFactService service = new RelationFactService((visitor, rule, target, limit) -> new RelationFactSet(source), 10);
        RelationFactSet facts = service.resolve(teacher("teacher-1"), rule(), null);
        source.clear();
        assertEquals(Arrays.asList(pair("school-a", "class-1"), pair("school-b", "class-2")), facts.getTuples());
        assertThrows(UnsupportedOperationException.class, () -> facts.getTuples().clear());
        assertThrows(UnsupportedOperationException.class, () -> facts.getTuples().get(0).put("school", "other"));
    }

    @Test
    void rechecks_facts_on_each_call_after_revocation() {
        AtomicReference<RelationFactSet> current = new AtomicReference<RelationFactSet>(
            new RelationFactSet(Collections.singletonList(pair("school-a", "class-1"))));
        AtomicInteger calls = new AtomicInteger();
        RelationFactService service = new RelationFactService((visitor, rule, target, limit) -> {
            calls.incrementAndGet();
            assertEquals("leave-1", target);
            assertEquals(10, limit);
            return current.get();
        }, 10);
        service.resolve(teacher("teacher-1"), rule(), "leave-1");
        current.set(new RelationFactSet(Collections.emptyList()));
        assertThrows(PermissionDeniedException.class, () -> service.resolve(teacher("teacher-1"), rule(), "leave-1"));
        assertEquals(2, calls.get());
    }

    @Test
    void denies_wrong_identity_missing_or_malformed_facts_and_oversized_results() {
        EntityPolicyRegistry.EffectiveRule rule = rule();
        AtomicInteger calls = new AtomicInteger();
        RelationFactService service = new RelationFactService((visitor, selected, target, limit) -> {
            calls.incrementAndGet();
            return new RelationFactSet(Collections.singletonList(pair("school-a", "class-1")));
        }, 1);
        assertThrows(PermissionDeniedException.class, () -> service.resolve(VisitorContext.anonymous("base", null, null), rule, null));
        assertThrows(PermissionDeniedException.class, () -> service.resolve(
            VisitorContext.authenticated(new AccessSubject("actor", AccessSubjectType.HUMAN),
                new TeacherIdentity("other", "parent"), "base", null, null), rule, null));
        assertEquals(0, calls.get());

        assertThrows(PermissionDeniedException.class, () -> new RelationFactService((v, r, t, limit) -> null, 1)
            .resolve(teacher("teacher-1"), rule, null));
        assertThrows(PermissionDeniedException.class, () -> new RelationFactService((v, r, t, limit) ->
            new RelationFactSet(Collections.singletonList(Collections.singletonMap("school", "school-a"))), 1)
            .resolve(teacher("teacher-1"), rule, null));
        assertThrows(PermissionDeniedException.class, () -> new RelationFactService((v, r, t, limit) ->
            new RelationFactSet(Arrays.asList(pair("school-a", "class-1"), pair("school-b", "class-2"))), 1)
            .resolve(teacher("teacher-1"), rule, null));
    }

    @Test
    void public_rule_does_not_pass_authenticated_identity_to_fact_resolver() {
        RelationFactService service = new RelationFactService((visitor, selected, target, limit) -> {
            assertTrue(visitor.isAnonymous());
            assertFalse(visitor.getIdentity().isPresent());
            assertEquals("base", visitor.getAccessEntry());
            return new RelationFactSet(Collections.singletonList(pair("school-a", "class-1")));
        }, 10);
        service.resolve(teacher("teacher-1"), rule(PolicyIdentityBranch.publicBranch()), null);
    }

    private static EntityPolicyRegistry.EffectiveRule rule() {
        return rule(PolicyIdentityBranch.identity("teacher"));
    }

    private static EntityPolicyRegistry.EffectiveRule rule(PolicyIdentityBranch branch) {
        ResourceDescriptor resource = new ResourceDescriptor(Leave.class, "leave", null, Collections.emptyList());
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        for (String field : Arrays.asList("id", "schoolId", "classId")) {
            fields.put(field, new EntityFieldMeta(field, String.class, field, false, false, true, true));
        }
        EntityMeta meta = new EntityMeta(Leave.class, resource, "leave", "id", null, fields);
        BusinessOperation operation = new BusinessOperation("leave", "view");
        BusinessOperationCatalog operations = new BusinessOperationCatalog(Collections.singletonList(resource),
            Collections.singletonList(new BusinessOperationDeclaration(operation, "Project#view")));
        EntityPolicyTemplate template = new EntityPolicyTemplate("record", "Project#record", Collections.singletonList(
            new EntityPolicyTemplate.Rule("view", branch, Arrays.asList("school", "class"))));
        Map<String, String> relations = new LinkedHashMap<String, String>();
        relations.put("school", "schoolId");
        relations.put("class", "classId");
        EntityPolicyBinding binding = new EntityPolicyBinding(Leave.class, "record", "Leave#binding", relations);
        BusinessIdentityRegistry identities = new BusinessIdentityRegistry(Collections.singletonList(
            new BusinessIdentityRegistration<TeacherIdentity>("teacher", TeacherIdentity.class,
                (subject, identityId) -> new TeacherIdentity(identityId, "teacher"))));
        return new EntityPolicyRegistry(Collections.singletonList(meta), Collections.singletonList(template),
            Collections.singletonList(binding), operations, identities)
            .requireDeclaration(Leave.class, operation, branch);
    }

    private static VisitorContext teacher(String id) {
        return VisitorContext.authenticated(new AccessSubject("actor", AccessSubjectType.HUMAN),
            new TeacherIdentity(id, "teacher"), "base", null, null);
    }

    private static Map<String, String> pair(String school, String clazz) {
        Map<String, String> tuple = new LinkedHashMap<String, String>();
        tuple.put("school", school);
        tuple.put("class", clazz);
        return tuple;
    }

    private static final class Leave {
    }

    private static final class TeacherIdentity implements BusinessIdentity {
        private final String id;
        private final String code;

        private TeacherIdentity(String id, String code) {
            this.id = id;
            this.code = code;
        }

        @Override
        public String getIdentityCode() { return code; }

        @Override
        public String getIdentityId() { return id; }
    }
}
