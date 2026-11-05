package com.entloom.crud.engine.jdbc.governance;

import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditReasonCode;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Map;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcCrudGovernanceAuditRecorderTest {
    @Test
    void 授权提前拒绝或故障时仍持久化目标与调用标识() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:crud_early_authorization_audit;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        JdbcCrudGovernanceAuditRecorder recorder = new JdbcCrudGovernanceAuditRecorder(jdbc, null, true);
        AuthorizationRequest request = new AuthorizationRequest(VisitorContext.anonymous("public", "request-1", "trace-1"),
            new BusinessOperation("product", "view"), "101");
        for (CrudGovernanceAuditOutcome outcome : new CrudGovernanceAuditOutcome[] {
                CrudGovernanceAuditOutcome.GOVERNANCE_DENIED, CrudGovernanceAuditOutcome.GOVERNANCE_FAILED}) {
            recorder.record(CrudGovernanceAuditEvent.forBusinessQuery(request.getVisitor(), null, request, null,
                new CrudResourceAction("product", "view", null), outcome,
                outcome == CrudGovernanceAuditOutcome.GOVERNANCE_DENIED ? CrudGovernanceAuditReasonCode.PERMISSION_DENIED
                    : CrudGovernanceAuditReasonCode.INTERNAL_ERROR,
                1L, CrudErrorStage.GOVERNANCE, "product.view"));
            var payload = new ObjectMapper().readTree(jdbc.queryForObject(
                "select authorization_json from entloom_crud_governance_audit where outcome = ?", String.class, outcome.name()));
            Assertions.assertEquals(request.getInvocationId(), payload.path("request").path("invocationId").asText());
            Assertions.assertEquals("101", payload.path("request").path("targetId").asText());
            Assertions.assertEquals("product", payload.path("request").path("resource").asText());
            Assertions.assertEquals("view", payload.path("request").path("operation").asText());
            Assertions.assertEquals("request-1", payload.path("visitor").path("requestId").asText());
            Assertions.assertFalse(payload.has("authorization"));
        }
    }

    @Test
    void should_persist_scene_policy_audit_fields() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:crud_policy_audit;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        JdbcCrudGovernanceAuditRecorder recorder = new JdbcCrudGovernanceAuditRecorder(jdbcTemplate, null, true);
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("tester");
        CrudResourceAction action = new CrudResourceAction(
            new ResourceDescriptor(TestEntity.class, "test_entity", null, Collections.<String>emptyList()),
            CrudOperationKey.of(CommandOperation.ACTION),
            "activate",
            null,
            "base",
            "http",
            false,
            "高风险场景未配置 Scene Policy"
        );

        recorder.record(CrudGovernanceAuditEvent.of(
            subject, action, AccessDecision.DENY, null, null,
            CrudGovernanceAuditOutcome.GOVERNANCE_DENIED,
            CrudGovernanceAuditReasonCode.PERMISSION_DENIED,
            3L
        ));

        Map<String, Object> row = jdbcTemplate.queryForMap(
            "select access_entry, portal, capability, policy_matched, policy_rejection_reason from entloom_crud_governance_audit"
        );
        Assertions.assertEquals("base", row.get("ACCESS_ENTRY"));
        Assertions.assertEquals("http", row.get("PORTAL"));
        Assertions.assertEquals(0, ((Number) row.get("POLICY_MATCHED")).intValue());
        Assertions.assertEquals("高风险场景未配置 Scene Policy", row.get("POLICY_REJECTION_REASON"));
    }

    private static final class TestEntity {
    }
}
