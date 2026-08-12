package com.entloom.crud.core.governance.permission;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RuleBasedCrudPermissionServiceTest {
    @Test
    void should_return_first_matching_rule_decision() {
        RuleBasedCrudPermissionService service = new RuleBasedCrudPermissionService(Arrays.asList(
            new CrudPermissionRule(
                "order",
                "PAGE",
                "admin",
                AccessDecision.DENY,
                Collections.<String>emptySet(),
                Collections.<String>emptySet(),
                Collections.<String>emptySet()
            ),
            new CrudPermissionRule(
                "order",
                "PAGE",
                "admin",
                AccessDecision.ALLOW,
                Collections.<String>emptySet(),
                Collections.<String>emptySet(),
                Collections.<String>emptySet()
            )
        ));

        AccessDecision decision = service.decide(new CrudResourceAction("order", "PAGE", "admin"), subject("u1", "t1", "o1"), null);

        Assertions.assertEquals(AccessDecision.DENY, decision);
    }

    @Test
    void should_match_subject_tenant_and_org_constraints() {
        RuleBasedCrudPermissionService service = new RuleBasedCrudPermissionService(Collections.singletonList(
            new CrudPermissionRule(
                "order",
                "CREATE",
                "*",
                AccessDecision.ALLOW,
                setOf("u1"),
                setOf("t1"),
                setOf("o1")
            )
        ));

        Assertions.assertEquals(
            AccessDecision.ALLOW,
            service.decide(new CrudResourceAction("order", "CREATE", "bulk"), subject("u1", "t1", "o1"), null)
        );
        Assertions.assertThrows(
            PermissionDeniedException.class,
            () -> service.decide(new CrudResourceAction("order", "CREATE", "bulk"), subject("u2", "t1", "o1"), null)
        );
    }

    @Test
    void should_fail_closed_when_no_rule_matches() {
        RuleBasedCrudPermissionService service = new RuleBasedCrudPermissionService(Collections.singletonList(
            new CrudPermissionRule("order", "PAGE", null, AccessDecision.ALLOW, null, null, null)
        ));

        Assertions.assertThrows(
            PermissionDeniedException.class,
            () -> service.decide(new CrudResourceAction("student", "PAGE", null), subject("u1", "t1", "o1"), null)
        );
    }

    private SubjectContext subject(String subjectId, String tenantId, String orgId) {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId(subjectId);
        subject.setTenantId(tenantId);
        subject.setOrgId(orgId);
        return subject;
    }

    private LinkedHashSet<String> setOf(String value) {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        values.add(value);
        return values;
    }
}
