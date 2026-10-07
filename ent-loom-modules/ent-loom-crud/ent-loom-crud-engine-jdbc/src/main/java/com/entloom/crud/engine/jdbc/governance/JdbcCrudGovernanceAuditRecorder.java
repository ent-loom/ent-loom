package com.entloom.crud.engine.jdbc.governance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.authorization.AuthorizationResult;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC 审计落库实现。
 */
public class JdbcCrudGovernanceAuditRecorder implements CrudGovernanceAuditRecorder {
    /** JDBC 模板。 */
    private final JdbcTemplate jdbcTemplate;
    /** 表名。 */
    private final String tableName;
    /** 对象映射器。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JdbcCrudGovernanceAuditRecorder(JdbcTemplate jdbcTemplate, String tableName, boolean autoInitializeSchema) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName == null || tableName.trim().isEmpty()
            ? "entloom_crud_governance_audit"
            : tableName;
        if (autoInitializeSchema) {
            initializeSchema();
        }
    }

    @Override
    public void record(CrudGovernanceAuditEvent event) {
        jdbcTemplate.update(
            "insert into " + tableName + " (subject_id, tenant_id, org_id, resource, action, scene, access_entry, portal, capability, policy_matched, policy_rejection_reason, access_decision, allowed, outcome, reason_code, granted_scope_json, governance_scope_json, authorization_json, cost_ms) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            event.getSubject() == null ? null : event.getSubject().getSubjectId(),
            event.getSubject() == null ? null : event.getSubject().getTenantId(),
            event.getSubject() == null ? null : event.getSubject().getOrgId(),
            event.getAction() == null ? null : event.getAction().getResource(),
            event.getAction() == null ? null : event.getAction().getAction(),
            event.getAction() == null ? null : event.getAction().getScene(),
            event.getAction() == null ? null : event.getAction().getAccessEntry(),
            event.getAction() == null ? null : event.getAction().getPortal(),
            event.getAction() == null ? null : event.getAction().getCapability(),
            event.getAction() != null && event.getAction().isPolicyMatched() ? 1 : 0,
            event.getAction() == null ? null : event.getAction().getPolicyRejectionReason(),
            event.getAccessDecision().name(),
            event.isAllowed() ? 1 : 0,
            event.getOutcome() == null ? null : event.getOutcome().name(),
            event.getReason() == null ? null : event.getReason().name(),
            toJson(event.getGrantedScope()),
            toJson(event.getGovernanceScope()),
            toAuthorizationJson(event),
            event.getCostMs()
        );
    }

    /**
     * 初始化所需的数据库表结构。
     */
    public void initializeSchema() {
        jdbcTemplate.execute(
            "create table if not exists " + tableName + " ("
                + "id bigint auto_increment primary key, "
                + "subject_id varchar(128), "
                + "tenant_id varchar(128), "
                + "org_id varchar(128), "
                + "resource varchar(128), "
                + "action varchar(128), "
                + "scene varchar(255), "
                + "access_entry varchar(64), "
                + "portal varchar(64), "
                + "capability varchar(128), "
                + "policy_matched int, "
                + "policy_rejection_reason varchar(1024), "
                + "access_decision varchar(32), "
                + "allowed int, "
                + "outcome varchar(32), "
                + "reason_code varchar(64), "
                + "granted_scope_json clob, "
                + "governance_scope_json clob, "
                + "authorization_json clob, "
                + "cost_ms bigint, "
                + "created_at timestamp default current_timestamp)"
        );
    }

    /**
     * 将数据范围转换为 JSON 字符串。
     */
    private String toJson(CrudDataScope scope) {
        if (scope == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("explicitAll", scope.isExplicitAll());
        payload.put("dimensions", scope.getDimensions());
        payload.put("readScopes", scope.getReadScopes());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("治理范围序列化失败", ex);
        }
    }

    private String toAuthorizationJson(CrudGovernanceAuditEvent event) {
        AuthorizationResult result = event.getAuthorizationResult();
        VisitorContext visitor = event.getVisitor();
        if (result == null && visitor == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        if (visitor != null) {
            Map<String, Object> visitorPayload = new LinkedHashMap<String, Object>();
            visitorPayload.put("accessEntry", visitor.getAccessEntry());
            visitorPayload.put("requestId", visitor.getRequestId());
            visitorPayload.put("traceId", visitor.getTraceId());
            visitor.getIdentity().ifPresent(identity -> {
                visitorPayload.put("identityCode", identity.getIdentityCode());
                visitorPayload.put("identityId", identity.getIdentityId());
            });
            payload.put("visitor", visitorPayload);
        }
        if (result != null) {
            Map<String, Object> authorization = new LinkedHashMap<String, Object>();
            authorization.put("decision", result.getDecision() == null ? null : result.getDecision().name());
            authorization.put("invocationId", result.getRequest().getInvocationId());
            authorization.put("resource", result.getRequest().getOperation().getResourceCode());
            authorization.put("operation", result.getRequest().getOperation().getOperationCode());
            authorization.put("targetId", result.getRequest().getTargetId());
            authorization.put("scope", toAuthorizationScope(result.getScope()));
            authorization.put("fields", result.getFields().snapshot());
            payload.put("authorization", authorization);
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("业务授权审计序列化失败", ex);
        }
    }

    private Map<String, Object> toAuthorizationScope(AuthorizationScope scope) {
        if (scope == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("kind", scope.getKind().name());
        if (scope.getField() != null) {
            payload.put("field", scope.getField());
        }
        if (!scope.getValues().isEmpty()) {
            payload.put("values", scope.getValues());
        }
        if (!scope.getChildren().isEmpty()) {
            List<Map<String, Object>> children = new ArrayList<Map<String, Object>>();
            for (AuthorizationScope child : scope.getChildren()) {
                children.add(toAuthorizationScope(child));
            }
            payload.put("children", children);
        }
        return payload;
    }
}
