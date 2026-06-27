package com.entloom.crud.engine.jdbc.governance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.sql.SQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC 审计落库实现。
 */
public class JdbcCrudGovernanceAuditRecorder implements CrudGovernanceAuditRecorder {
    /** 审计结果列名。 */
    private static final String OUTCOME_COLUMN = "outcome";
    /** 审计结果列定义。 */
    private static final String OUTCOME_COLUMN_DEFINITION = "varchar(32)";
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
            "insert into " + tableName + " (subject_id, tenant_id, org_id, resource, action, scene, access_decision, allowed, outcome, reason_code, granted_scope_json, governance_scope_json, cost_ms) values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            event.getSubject() == null ? null : event.getSubject().getSubjectId(),
            event.getSubject() == null ? null : event.getSubject().getTenantId(),
            event.getSubject() == null ? null : event.getSubject().getOrgId(),
            event.getAction() == null ? null : event.getAction().getResource(),
            event.getAction() == null ? null : event.getAction().getAction(),
            event.getAction() == null ? null : event.getAction().getScene(),
            event.getAccessDecision().name(),
            event.isAllowed() ? 1 : 0,
            event.getOutcome() == null ? null : event.getOutcome().name(),
            event.getReason() == null ? null : event.getReason().name(),
            toJson(event.getGrantedScope()),
            toJson(event.getGovernanceScope()),
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
                + "access_decision varchar(32), "
                + "allowed int, "
                + "outcome varchar(32), "
                + "reason_code varchar(64), "
                + "granted_scope_json clob, "
                + "governance_scope_json clob, "
                + "cost_ms bigint, "
                + "created_at timestamp default current_timestamp)"
        );
        ensureColumnExists(OUTCOME_COLUMN, OUTCOME_COLUMN_DEFINITION);
    }

    /**
     * 补齐历史版本缺失列，便于在线迁移。
     */
    private void ensureColumnExists(String columnName, String definition) {
        try {
            jdbcTemplate.execute("alter table " + tableName + " add column " + columnName + " " + definition);
        } catch (DataAccessException ex) {
            if (!isColumnAlreadyExists(ex)) {
                throw ex;
            }
        }
    }

    private boolean isColumnAlreadyExists(DataAccessException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof SQLException) {
            String sqlState = ((SQLException) cause).getSQLState();
            if ("42S21".equals(sqlState)) {
                return true;
            }
        }
        String message = ex.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("duplicate column")
            || normalized.contains("column already exists")
            || normalized.contains("already exists")
            || normalized.contains("column exists");
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
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("治理范围序列化失败", ex);
        }
    }
}
