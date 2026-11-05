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
import com.entloom.crud.core.capability.command.engine.AuthorizedBatchUpdateExecutor;
import com.entloom.crud.core.runtime.contract.CrudInputContract;
import com.entloom.crud.engine.jdbc.dao.JdbcEntityDaoFactory;
import com.entloom.crud.engine.jdbc.dao.JdbcPaginationPolicy;
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
    private final java.util.Set<String> deniedTargets = new java.util.HashSet<>();
    private final java.util.Set<String> inaccessibleTargets = new java.util.HashSet<>();
    private final List<String> targets = new ArrayList<String>();
    private AuthorizationScope createScope = AuthorizationScope.equal("schoolId", 1L);

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
        BusinessOperation batchUpdate = new BusinessOperation(meta.getResourceDescriptor().getResourceCode(), "batch-update");
        BusinessOperation delete = new BusinessOperation(meta.getResourceDescriptor().getResourceCode(), "delete");
        BusinessOperation create = new BusinessOperation(meta.getResourceDescriptor().getResourceCode(), "create");
        BusinessOperationCatalog operations = new BusinessOperationCatalog(
            Collections.singletonList(meta.getResourceDescriptor()),
            Arrays.asList(new BusinessOperationDeclaration(update, "Test#update"),
                new BusinessOperationDeclaration(batchUpdate, "Test#batch-update"),
                new BusinessOperationDeclaration(delete, "Test#delete"), new BusinessOperationDeclaration(create, "Test#create")));
        EntityPolicyTemplate template = new EntityPolicyTemplate("public", "Test#public",
            Arrays.asList(new EntityPolicyTemplate.Rule("update", PolicyIdentityBranch.publicBranch(), Collections.emptyList()),
                new EntityPolicyTemplate.Rule("batch-update", PolicyIdentityBranch.publicBranch(), Collections.emptyList()),
                new EntityPolicyTemplate.Rule("delete", PolicyIdentityBranch.publicBranch(), Collections.emptyList()),
                new EntityPolicyTemplate.Rule("create", PolicyIdentityBranch.publicBranch(), Collections.emptyList())));
        EntityPolicyRegistry policies = new EntityPolicyRegistry(Collections.singletonList(meta),
            Collections.singletonList(template), Collections.singletonList(new EntityPolicyBinding(
                OrderTestEntity.class, "public", "Test#binding", Collections.emptyMap())), operations,
            new BusinessIdentityRegistry(Collections.emptyList()));
        CrudOperationMappingRegistry mappings = new CrudOperationMappingRegistry(Collections.singletonList(meta), operations,
            Arrays.asList(new CrudOperationMapping(CrudOperationKey.of(CommandOperation.UPDATE), null, update, "Test#update-map"),
                new CrudOperationMapping(CrudOperationKey.of(CommandOperation.UPDATE_BATCH), null, batchUpdate, "Test#batch-update-map"),
                new CrudOperationMapping(CrudOperationKey.of(CommandOperation.DELETE), null, delete, "Test#delete-map"),
                new CrudOperationMapping(CrudOperationKey.of(CommandOperation.CREATE), null, create, "Test#create-map")));
        BusinessAuthorizationService service = new BusinessAuthorizationService(operations, policies,
            (current, operation, rule, target) -> {
                targets.add(target);
                return inaccessibleTargets.contains(target) ? BusinessPermissionDecision.targetInaccessible()
                    : allowed.get() && !deniedTargets.contains(target) ? BusinessPermissionDecision.allow(
                    operation.equals(create) ? createScope : AuthorizationScope.in("id", Arrays.asList(1L, 4L)),
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
        JdbcEntityDaoFactory daos = new JdbcEntityDaoFactory(entities, guarded, StandardJdbcDialect.GENERIC,
            JdbcEntityDaoFactory.DEFAULT_MAX_PARAMETERS, null, transactions, JdbcEntityDaoFactory.DEFAULT_MAX_BATCH_SIZE,
            new JdbcPaginationPolicy(), audit::add);
        AuthorizedBatchUpdateExecutor batches = new AuthorizedBatchUpdateExecutor(entities, adapter, daos, guard,
            audit::add, CrudInputContract.empty(), (entity, values) -> {});
        executor = new JdbcAuthorizedCommandExecutor(entities, adapter, guarded, guard,
            StandardJdbcDialect.GENERIC, audit::add, transactions, batches, (entity, values) -> {}, CrudInputContract.empty());
    }

    @Test
    void 创建显式主键并落实授权归属和逻辑删除初始值() {
        CommandResult<?> result = executor.action(visitor, create(map("id", 10L, "orderNo", "created")));
        assertEquals(10L, ((Map<?, ?>) result.getData()).get("id"));
        assertEquals(1L, jdbc.queryForObject("select school_id from t_order where id=10", Long.class));
        assertEquals(0, jdbc.queryForObject("select is_deleted from t_order where id=10", Integer.class));
        assertEquals(Collections.singletonList(null), targets);
        assertEquals(CrudGovernanceAuditOutcome.SUCCESS, audit.get(0).getOutcome());
    }

    @Test
    void 创建拒绝未授权字段越权归属和不可确定范围且不写入() {
        assertThrows(PermissionDeniedException.class, () -> executor.action(visitor,
            create(map("id", 10L, "orderNo", "bad", "unknown", "x"))));
        assertThrows(ValidationException.class, () -> executor.action(visitor,
            create(map("id", 10L, "orderNo", "bad", "schoolId", 2L))));
        assertThrows(PermissionDeniedException.class, () -> executor.action(visitor,
            create(map("id", 10L, "orderNo", "bad", "isDeleted", 1))));
        createScope = AuthorizationScope.or(List.of(AuthorizationScope.equal("schoolId", 1L),
            AuthorizationScope.equal("schoolId", 2L)));
        assertThrows(PermissionDeniedException.class, () -> executor.action(visitor, create(map("id", 10L, "orderNo", "bad"))));
        createScope = AuthorizationScope.none();
        assertThrows(PermissionDeniedException.class, () -> executor.action(visitor, create(map("id", 10L, "orderNo", "bad"))));
        assertEquals(4, jdbc.queryForObject("select count(*) from t_order", Integer.class));
    }

    @Test
    void 创建拒绝无可信访问者拒绝决策和目标条件() {
        assertThrows(PermissionDeniedException.class, () -> executor.action(null, create(map("id", 10L, "orderNo", "bad"))));
        allowed.set(false);
        assertThrows(PermissionDeniedException.class, () -> executor.action(visitor, create(map("id", 10L, "orderNo", "bad"))));
        allowed.set(true);
        assertThrows(PermissionDeniedException.class, () -> executor.action(visitor,
            create(map("id", 10L, "orderNo", "bad")).toBuilder().expectedVersion(0L).build()));
        assertEquals(4, jdbc.queryForObject("select count(*) from t_order", Integer.class));
    }

    @Test
    void 创建唯一键冲突回滚并记录执行失败() {
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            () -> executor.action(visitor, create(map("id", 1L, "orderNo", "duplicate"))));
        assertEquals("one", jdbc.queryForObject("select order_no from t_order where id=1", String.class));
        assertEquals(CrudGovernanceAuditOutcome.EXECUTION_FAILED, audit.get(0).getOutcome());
    }

    private CommandSpec<Map<String, Object>> create(Map<String, Object> values) {
        return CommandSpec.<Map<String, Object>>builder().rootType(OrderTestEntity.class).op(CommandOperation.CREATE)
            .payload(values).resultType(CommandResult.class).build();
    }

    @Test
    void 目标拒绝按写入未命中处理并记录授权拒绝() {
        inaccessibleTargets.add("1");
        var denied = assertThrows(EntityDaoWriteMissException.class, () -> executor.action(visitor, update(1L, map("orderNo", "bad"))));
        assertEquals(com.entloom.crud.api.enums.CrudErrorStage.EXECUTE, denied.getStage());
        assertEquals("one", jdbc.queryForObject("select order_no from t_order where id=1", String.class));
        assertEquals(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED, audit.get(0).getOutcome());
        assertEquals("DATA_SCOPE_DENIED", audit.get(0).getReasonCode());
        assertEquals(BusinessPermissionDecision.Kind.TARGET_INACCESSIBLE, audit.get(0).getAuthorizationResult().getKind());
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
    void unsupported_batch_and_relation_or_primary_key_fields_should_be_rejected_before_sql() {
        CommandSpec<BatchCommand<Map<String, Object>>> batch = CommandSpec.<BatchCommand<Map<String, Object>>>builder()
            .rootType(OrderTestEntity.class).op(CommandOperation.DELETE_BATCH).payload(
                BatchCommand.of(Collections.singletonList(new WriteCommand<Map<String, Object>>(
                    CommandOperation.UPDATE, 1L, map("orderNo", "x"))))).build();
        assertThrows(PermissionDeniedException.class, () -> executor.action(visitor, batch));
        assertThrows(PermissionDeniedException.class,
            () -> executor.action(visitor, update(1L, map("id", 1L))));
    }

    @Test
    void 批量命令逐目标授权并在提交后按输入顺序返回结果和审计() {
        CommandResult<?> result = executor.action(visitor, batch(4L, 1L));
        assertEquals(Arrays.asList("4", "1"), targets);
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals(2, data.get("rows"));
        assertEquals(4L, ((Map<?, ?>) ((List<?>) data.get("items")).get(0)).get("id"));
        assertEquals("updated", jdbc.queryForObject("select order_no from t_order where id=1", String.class));
        assertEquals(2, audit.size());
        assertTrue(audit.stream().allMatch(event -> event.getOutcome() == CrudGovernanceAuditOutcome.SUCCESS));
    }

    @Test
    void 第二项目标不可写则第一项回滚且整批没有成功审计() {
        assertThrows(EntityDaoWriteMissException.class, () -> executor.action(visitor, batch(1L, 2L)));
        assertEquals("one", jdbc.queryForObject("select order_no from t_order where id=1", String.class));
        assertEquals("two", jdbc.queryForObject("select order_no from t_order where id=2", String.class));
        assertEquals(2, audit.size());
        assertTrue(audit.stream().allMatch(event -> event.getOutcome() == CrudGovernanceAuditOutcome.EXECUTION_FAILED));
    }

    @Test
    void 显式空值清空可空字段且未提交字段保持原值() {
        CommandSpec<BatchCommand<Map<String, Object>>> spec = CommandSpec.<BatchCommand<Map<String, Object>>>builder()
            .rootType(OrderTestEntity.class).op(CommandOperation.UPDATE_BATCH).resultType(CommandResult.class)
            .payload(BatchCommand.of(Collections.singletonList(new WriteCommand<>(CommandOperation.UPDATE, 1L,
                Collections.singletonMap("orderNo", null))))).build();
        executor.action(visitor, spec);
        assertEquals(null, jdbc.queryForObject("select order_no from t_order where id=1", String.class));
        assertEquals(1L, jdbc.queryForObject("select school_id from t_order where id=1", Long.class));
    }

    @Test
    void 空批次重复目标和试运行在授权及写入前拒绝() {
        assertThrows(ValidationException.class, () -> executor.action(visitor, batch()));
        assertThrows(ValidationException.class, () -> executor.action(visitor, batch(1L, 1L)));
        assertThrows(ValidationException.class, () -> executor.action(visitor, batch(1L).toBuilder().dryRun(true).build()));
        assertThrows(ValidationException.class, () -> executor.action(visitor,
            batch(1L).toBuilder().idempotencyKey("unsupported-key").build()));
        assertThrows(ValidationException.class, () -> executor.action(visitor,
            batch(1L).toBuilder().expectedVersion(0L).build()));
        Long[] tooMany = new Long[501];
        Arrays.fill(tooMany, 1L);
        assertThrows(ValidationException.class, () -> executor.action(visitor, batch(tooMany)));
        assertTrue(targets.isEmpty());
        assertEquals("one", jdbc.queryForObject("select order_no from t_order where id=1", String.class));
    }

    @Test
    void 第二项目标动态拒绝时整批不写入且保留拒绝授权审计() {
        deniedTargets.add("4");
        assertThrows(PermissionDeniedException.class, () -> executor.action(visitor, batch(1L, 4L)));
        assertEquals("one", jdbc.queryForObject("select order_no from t_order where id=1", String.class));
        assertEquals("four", jdbc.queryForObject("select order_no from t_order where id=4", String.class));
        assertEquals(Arrays.asList("1", "4"), targets);
        assertEquals(2, audit.size());
        assertTrue(audit.stream().allMatch(event -> event.getOutcome() == CrudGovernanceAuditOutcome.GOVERNANCE_DENIED));
        assertEquals(com.entloom.crud.api.enums.AccessDecision.DENY, audit.get(1).getAuthorizationResult().getDecision());
    }

    private CommandSpec<BatchCommand<Map<String, Object>>> batch(Long... ids) {
        List<WriteCommand<Map<String, Object>>> items = new ArrayList<>();
        for (Long id : ids) items.add(new WriteCommand<>(CommandOperation.UPDATE, id, map("orderNo", "updated")));
        return CommandSpec.<BatchCommand<Map<String, Object>>>builder().rootType(OrderTestEntity.class)
            .op(CommandOperation.UPDATE_BATCH).resultType(CommandResult.class).payload(BatchCommand.of(items)).build();
    }

    private CommandSpec<WriteCommand<Map<String, Object>>> update(Object id, Map<String, Object> values) {
        return CommandSpec.<WriteCommand<Map<String, Object>>>builder().rootType(OrderTestEntity.class)
            .op(CommandOperation.UPDATE).resultType(CommandResult.class)
            .payload(new WriteCommand<Map<String, Object>>(CommandOperation.UPDATE, id, values)).build();
    }

    private Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
}
