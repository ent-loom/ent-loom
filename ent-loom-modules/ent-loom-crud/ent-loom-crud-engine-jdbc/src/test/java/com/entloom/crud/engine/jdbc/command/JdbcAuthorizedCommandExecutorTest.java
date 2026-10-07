package com.entloom.crud.engine.jdbc.command;

import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.capability.command.spec.BatchCommand;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.DeleteTarget;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.exception.EntityDaoWriteMissException;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.governance.adapter.CrudBusinessAuthorizationAdapter;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.authorization.BusinessAuthorizationService;
import com.entloom.crud.core.governance.authorization.BusinessPermissionDecision;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.core.governance.policy.BusinessOperationCatalog;
import com.entloom.crud.core.governance.policy.BusinessOperationDeclaration;
import com.entloom.crud.core.governance.policy.CrudOperationMapping;
import com.entloom.crud.core.governance.policy.CrudOperationMappingRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyBinding;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyTemplate;
import com.entloom.crud.core.governance.policy.PolicyIdentityBranch;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.security.SqlSecurityGuard;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;
import com.entloom.crud.engine.jdbc.log.SqlExecutionLogger;
import com.entloom.crud.engine.jdbc.security.JdbcGuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.security.SqlIdentifierAllowlistValidator;
import com.entloom.crud.engine.jdbc.security.SqlParameterLimiter;
import com.entloom.crud.engine.jdbc.security.SqlSafetyGuard;
import com.entloom.crud.engine.jdbc.test.entity.OrderItemTestEntity;
import com.entloom.crud.engine.jdbc.test.entity.OrderTestEntity;
import com.entloom.crud.engine.jdbc.transaction.JdbcCrudWriteTransactionExecutor;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcAuthorizedCommandExecutorTest {
    private final VisitorContext visitor = VisitorContext.anonymous("trusted-entry", "request-1", null);
    private JdbcTemplate jdbc;
    private JdbcAuthorizedCommandExecutor executor;
    private final List<CrudGovernanceAuditEvent> audit = new ArrayList<CrudGovernanceAuditEvent>();
    private final AtomicBoolean allowed = new AtomicBoolean(true);
    private final List<String> targets = new ArrayList<String>();

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:authorized_command_" + UUID.randomUUID() + ";MODE=MYSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table t_order(id bigint primary key, order_no varchar(64), school_id bigint, tenant_id varchar(64), is_deleted int)");
        jdbc.update("insert into t_order values (1,'one',1,'A',0),(2,'two',1,'B',0),(3,'three',2,'A',0),(4,'four',2,'B',0)");

        EntityMetaRegistry entities = new CrudRuntimeModelBackedEntityMetaRegistry(new CrudNativeRuntimeModelParser()
            .parse(Arrays.<Class<?>>asList(OrderTestEntity.class, OrderItemTestEntity.class)));
        EntityMeta meta = entities.getEntityMeta(OrderTestEntity.class);
        BusinessOperation update = new BusinessOperation(meta.getResourceDescriptor().getResourceCode(), "update");
        BusinessOperation delete = new BusinessOperation(meta.getResourceDescriptor().getResourceCode(), "delete");
        BusinessOperationCatalog operations = new BusinessOperationCatalog(
            Collections.singletonList(meta.getResourceDescriptor()),
            Arrays.asList(new BusinessOperationDeclaration(update, "Test#update"),
                new BusinessOperationDeclaration(delete, "Test#delete")));
        EntityPolicyTemplate template = new EntityPolicyTemplate("public", "Test#public",
            Arrays.asList(new EntityPolicyTemplate.Rule("update", PolicyIdentityBranch.publicBranch(), Collections.emptyList()),
                new EntityPolicyTemplate.Rule("delete", PolicyIdentityBranch.publicBranch(), Collections.emptyList())));
        EntityPolicyRegistry policies = new EntityPolicyRegistry(Collections.singletonList(meta),
            Collections.singletonList(template), Collections.singletonList(new EntityPolicyBinding(
                OrderTestEntity.class, "public", "Test#binding", Collections.emptyMap())), operations,
            new BusinessIdentityRegistry(Collections.emptyList()));
        CrudOperationMappingRegistry mappings = new CrudOperationMappingRegistry(Collections.singletonList(meta), operations,
            Arrays.asList(new CrudOperationMapping(CrudOperationKey.of(CommandOperation.UPDATE), null, update, "Test#update-map"),
                new CrudOperationMapping(CrudOperationKey.of(CommandOperation.DELETE), null, delete, "Test#delete-map")));
        BusinessAuthorizationService service = new BusinessAuthorizationService(operations, policies,
            (current, operation, rule, target) -> {
                targets.add(target);
                return allowed.get() ? BusinessPermissionDecision.allow(
                    AuthorizationScope.in("id", Arrays.asList(1L, 4L)),
                    AuthorizationFields.of(Collections.singletonMap(AuthorizationFields.Use.WRITE,
                        Collections.singletonList("orderNo")))) : BusinessPermissionDecision.deny();
            });
        CrudBusinessAuthorizationAdapter adapter = new CrudBusinessAuthorizationAdapter(entities, mappings, service);
        SqlSafetyGuard safety = new SqlSafetyGuard(new SqlIdentifierAllowlistValidator(entities), new SqlParameterLimiter());
        SqlSecurityGuard guard = new SqlSecurityGuard() {
            @Override public void beforeCompile(BaseSpec spec) {
                assertTrue(spec.getSubject() == null);
                safety.beforeCompile(spec);
            }
            @Override public void beforeExecute(String sql, List<Object> args, com.entloom.crud.core.runtime.context.CrudExecutionContext context) {
                safety.beforeExecute(sql, args, context);
            }
        };
        JdbcGuardedSqlExecutor guarded = new JdbcGuardedSqlExecutor(jdbc, guard, new SqlExecutionLogger());
        CrudWriteTransactionExecutor transactions = new JdbcCrudWriteTransactionExecutor(dataSource);
        executor = new JdbcAuthorizedCommandExecutor(entities, adapter, guarded, guard,
            StandardJdbcDialect.GENERIC, audit::add, transactions);
    }

    @Test
    void update_should_apply_authorized_scope_and_write_field_allowlist() {
        CommandResult<?> result = executor.action(visitor, update(1L, map("orderNo", "one-updated")));
        assertEquals(1, ((Number) ((Map<?, ?>) result.getData()).get("rows")).intValue());
        assertEquals("one-updated", jdbc.queryForObject("select order_no from t_order where id=1", String.class));
        assertThrows(EntityDaoWriteMissException.class,
            () -> executor.action(visitor, update(2L, map("orderNo", "must-not-write"))));
        assertEquals("two", jdbc.queryForObject("select order_no from t_order where id=2", String.class));
        assertThrows(PermissionDeniedException.class,
            () -> executor.action(visitor, update(1L, map("schoolId", 9L))));
        assertEquals(CrudGovernanceAuditOutcome.SUCCESS, audit.get(0).getOutcome());
        assertEquals(CrudGovernanceAuditOutcome.EXECUTION_FAILED, audit.get(1).getOutcome());
        assertEquals(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED, audit.get(2).getOutcome());
    }

    @Test
    void delete_should_bind_target_and_logic_delete_inside_transaction() {
        CommandSpec<DeleteTarget> spec = CommandSpec.<DeleteTarget>builder()
            .rootType(OrderTestEntity.class).op(CommandOperation.DELETE).resultType(CommandResult.class)
            .payload(new DeleteTarget(4L, Collections.emptyList(), null)).build();
        executor.action(visitor, spec);
        assertEquals(1, jdbc.queryForObject("select is_deleted from t_order where id=4", Integer.class));
        assertTrue(targets.contains("4"));
        allowed.set(false);
        assertThrows(PermissionDeniedException.class, () -> executor.action(visitor, spec));
        assertEquals(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED, audit.get(1).getOutcome());
    }

    @Test
    void batch_and_relation_or_primary_key_fields_should_be_rejected_before_sql() {
        CommandSpec<BatchCommand<Map<String, Object>>> batch = CommandSpec.<BatchCommand<Map<String, Object>>>builder()
            .rootType(OrderTestEntity.class).op(CommandOperation.UPDATE_BATCH).payload(
                BatchCommand.of(Collections.singletonList(new WriteCommand<Map<String, Object>>(
                    CommandOperation.UPDATE, 1L, map("orderNo", "x"))))).build();
        assertThrows(PermissionDeniedException.class, () -> executor.action(visitor, batch));
        assertThrows(PermissionDeniedException.class,
            () -> executor.action(visitor, update(1L, map("id", 1L))));
    }

    private CommandSpec<WriteCommand<Map<String, Object>>> update(Object id, Map<String, Object> values) {
        return CommandSpec.<WriteCommand<Map<String, Object>>>builder().rootType(OrderTestEntity.class)
            .op(CommandOperation.UPDATE).resultType(CommandResult.class)
            .payload(new WriteCommand<Map<String, Object>>(CommandOperation.UPDATE, id, values)).build();
    }

    private Map<String, Object> map(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put(key, value);
        return result;
    }
}
