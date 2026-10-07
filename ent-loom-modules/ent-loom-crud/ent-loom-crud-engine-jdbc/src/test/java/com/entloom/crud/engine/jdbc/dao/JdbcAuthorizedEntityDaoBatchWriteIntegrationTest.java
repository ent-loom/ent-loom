package com.entloom.crud.engine.jdbc.dao;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.api.enums.CrudIdPolicy;
import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.capability.dao.EntityDaoAuthorizationContext;
import com.entloom.crud.core.capability.dao.EntityDaoBatchAtomicity;
import com.entloom.crud.core.capability.dao.EntityDaoBatchAuthorizationContext;
import com.entloom.crud.core.capability.dao.EntityDaoBatchExecutionResult;
import com.entloom.crud.core.capability.dao.EntityDaoBatchWrite;
import com.entloom.crud.core.capability.dao.EntityDaoBatchWriteRequest;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.exception.EntityDaoWriteMissException;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.foundation.write.CrudBatchTransactionCapability;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionCallback;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionPolicy;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditReasonCode;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationResult;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.runtime.context.CrudExecutionContext;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;
import com.entloom.crud.engine.jdbc.log.SqlExecutionLogger;
import com.entloom.crud.engine.jdbc.security.JdbcGuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.security.SqlIdentifierAllowlistValidator;
import com.entloom.crud.engine.jdbc.security.SqlParameterLimiter;
import com.entloom.crud.engine.jdbc.security.SqlSafetyGuard;
import com.entloom.crud.engine.jdbc.transaction.JdbcCrudWriteTransactionExecutor;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 真实 H2、受保护 SQL 和 JDBC 事务验证逐目标授权批量更新的提交语义。 */
class JdbcAuthorizedEntityDaoBatchWriteIntegrationTest {
    private static final EntityType<BatchProduct, Long> TYPE = EntityType.of(BatchProduct.class, Long.class);
    private static final String SELECT_ROWS =
        "select id, name, price, tenant_id, version, is_deleted from t_batch_product order by id";
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private EntityMetaRegistry registry;
    private SqlSafetyGuard safetyGuard;
    private ObservingSqlExecutor sqlExecutor;
    private RecordingAuditRecorder audit;
    private JdbcEntityDaoFactory factory;
    private VisitorContext visitor;
    private List<String> initialRows;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:batch_write_" + UUID.randomUUID()
                + ";MODE=MYSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", ""
        );
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table t_batch_product(id bigint primary key, name varchar(64), "
            + "price decimal(12,2), tenant_id varchar(64) not null, version bigint not null, is_deleted int not null)");
        jdbc.update("insert into t_batch_product values(?,?,?,?,?,?)", 1L, "商品一", new BigDecimal("10.00"), "tenant-a", 0L, 0);
        jdbc.update("insert into t_batch_product values(?,?,?,?,?,?)", 2L, "商品二", new BigDecimal("20.00"), "tenant-b", 7L, 0);
        registry = new CrudRuntimeModelBackedEntityMetaRegistry(
            new CrudNativeRuntimeModelParser().parse(Collections.<Class<?>>singletonList(BatchProduct.class))
        );
        registry.validateOrThrow();
        safetyGuard = spy(new SqlSafetyGuard(new SqlIdentifierAllowlistValidator(registry), new SqlParameterLimiter()));
        audit = new RecordingAuditRecorder();
        sqlExecutor = new ObservingSqlExecutor();
        factory = new JdbcEntityDaoFactory(
            registry, sqlExecutor, StandardJdbcDialect.GENERIC, JdbcEntityDaoFactory.DEFAULT_MAX_PARAMETERS,
            null, new JdbcCrudWriteTransactionExecutor(dataSource), JdbcEntityDaoFactory.DEFAULT_MAX_BATCH_SIZE,
            new JdbcPaginationPolicy(), audit
        );
        visitor = VisitorContext.anonymous("jdbc-batch-test", "request-batch", null);
        initialRows = committedRows();
    }

    @AfterEach
    void closeDatabase() {
        jdbc.execute("shutdown");
    }

    @Test
    void success_consumes_each_targets_scope_fields_and_version_then_audits_after_commit() {
        EntityDaoBatchAuthorizationContext authorization = batch("tenant-b", "price");
        UpdatePatch<BatchProduct> first = patch(1L, 0L, "name", "改名一");
        UpdatePatch<BatchProduct> second = patch(2L, 7L, "price", new BigDecimal("25.50"));

        EntityDaoBatchExecutionResult result = execute(authorization, first, second);

        assertSucceeded(result);
        assertEquals(Arrays.asList(1, 1), sqlExecutor.affectedRows);
        assertEquals(Arrays.asList(1L, 1L), sqlExecutor.firstVersionsDuringWrites);
        assertEquals(Arrays.asList(0, 0), sqlExecutor.auditCountsDuringWrites);
        assertEquals(Arrays.asList(initialRows, initialRows), sqlExecutor.committedRowsDuringWrites);
        List<String> expected = Arrays.asList(
            "1|改名一|10.00|tenant-a|1|0", "2|商品二|25.50|tenant-b|8|0"
        );
        assertEquals(expected, committedRows());
        assertAudit(authorization, CrudGovernanceAuditOutcome.SUCCESS, null);
        assertEquals(Arrays.asList(false, false), audit.transactionActive);
        assertEquals(Arrays.asList(expected, expected), audit.rowsAtRecord);
        // 批量结果以最终提交为准，Patch 输入实体不回填版本。
        assertEquals(Long.valueOf(0L), first.getEntity().version);
        assertEquals(Long.valueOf(7L), second.getEntity().version);
    }

    @Test
    void stale_second_target_rolls_back_the_already_written_first_target() {
        EntityDaoBatchAuthorizationContext authorization = batch("tenant-b", "price");
        UpdatePatch<BatchProduct> first = patch(1L, 0L, "name", "不能保留");
        UpdatePatch<BatchProduct> second = patch(2L, 6L, "price", new BigDecimal("25.50"));

        assertThrows(EntityDaoWriteMissException.class, () -> execute(authorization, first, second));

        assertRolledBack(authorization);
        assertEquals(Long.valueOf(0L), first.getEntity().version);
        assertEquals(Long.valueOf(6L), second.getEntity().version);
    }

    @Test
    void second_target_outside_its_own_scope_rolls_back_first_target() {
        EntityDaoBatchAuthorizationContext authorization = batch("tenant-a", "price");

        assertThrows(EntityDaoWriteMissException.class, () -> execute(authorization,
            patch(1L, 0L, "name", "不能保留"), patch(2L, 7L, "price", new BigDecimal("25.50"))));

        assertRolledBack(authorization);
    }

    @Test
    void logically_deleted_second_target_rolls_back_first_target() {
        jdbc.update("update t_batch_product set is_deleted=1 where id=2");
        initialRows = committedRows();
        EntityDaoBatchAuthorizationContext authorization = batch("tenant-b", "price");

        assertThrows(EntityDaoWriteMissException.class, () -> execute(authorization,
            patch(1L, 0L, "name", "不能保留"), patch(2L, 7L, "price", new BigDecimal("25.50"))));

        assertRolledBack(authorization);
    }

    @Test
    void second_targets_real_sql_constraint_failure_rolls_back_first_target() {
        jdbc.execute("alter table t_batch_product add constraint non_negative_price check(price >= 0)");
        EntityDaoBatchAuthorizationContext authorization = batch("tenant-b", "price");

        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class,
            () -> execute(authorization, patch(1L, 0L, "name", "不能保留"),
                patch(2L, 7L, "price", new BigDecimal("-1.00"))));

        assertTrue(failure.getMostSpecificCause() instanceof SQLException);
        assertEquals("23513", ((SQLException) failure.getMostSpecificCause()).getSQLState());
        assertEquals(2, sqlExecutor.updateAttempts);
        assertEquals(Collections.singletonList(1), sqlExecutor.affectedRows);
        assertEquals(Collections.singletonList(1L), sqlExecutor.firstVersionsDuringWrites);
        assertEquals(Collections.singletonList(0), sqlExecutor.auditCountsDuringWrites);
        assertEquals(initialRows, committedRows());
        assertAudit(authorization, CrudGovernanceAuditOutcome.EXECUTION_FAILED, CrudErrorStage.EXECUTE);
        assertEquals(Arrays.asList(false, false), audit.transactionActive);
        assertEquals(Arrays.asList(initialRows, initialRows), audit.rowsAtRecord);
    }

    @Test
    void missing_transaction_executor_rejects_before_sql_with_validation_exception() {
        // 自定义 SQL 执行器没有可推导的数据源，也未提供事务执行器。
        GuardedSqlExecutor customSqlExecutor = mock(GuardedSqlExecutor.class);
        JdbcEntityDaoFactory unsupported = new JdbcEntityDaoFactory(registry, customSqlExecutor);

        ValidationException failure = assertThrows(ValidationException.class,
            () -> unsupported.scopedAuthorizedBatchWrite(TYPE, batch("tenant-b", "price")));

        assertTrue(failure.getMessage().contains("事务执行器不能为空"), failure.getMessage());
        verifyNoInteractions(customSqlExecutor, safetyGuard);
        assertEquals(initialRows, committedRows());
        assertTrue(audit.events.isEmpty());
    }

    @Test
    void executor_without_batch_capability_rejects_before_sql() {
        CrudWriteTransactionExecutor transactions = mock(CrudWriteTransactionExecutor.class);
        JdbcEntityDaoFactory unsupported = new JdbcEntityDaoFactory(
            registry, sqlExecutor, StandardJdbcDialect.GENERIC, JdbcEntityDaoFactory.DEFAULT_MAX_PARAMETERS,
            null, transactions, JdbcEntityDaoFactory.DEFAULT_MAX_BATCH_SIZE, new JdbcPaginationPolicy(), audit
        );

        ValidationException failure = assertThrows(ValidationException.class,
            () -> unsupported.scopedAuthorizedBatchWrite(TYPE, batch("tenant-b", "price")));

        assertTrue(failure.getMessage().contains("未声明支持批量原子性"), failure.getMessage());
        verifyNoInteractions(transactions, safetyGuard);
        assertEquals(0, sqlExecutor.updateAttempts);
        assertEquals(initialRows, committedRows());
        assertTrue(audit.events.isEmpty());
    }

    @Test
    void declaring_atomicity_without_committed_batch_override_rejects_without_executing_callback() {
        class AtomicityOnlyTransactions implements CrudWriteTransactionExecutor, CrudBatchTransactionCapability {
            private int executeCalls;

            @Override
            public boolean supportsBatchAtomicity(EntityDaoBatchAtomicity atomicity) {
                return atomicity == EntityDaoBatchAtomicity.ALL_OR_NOTHING;
            }

            @Override
            public <T> T execute(CrudWriteTransactionPolicy policy, CrudWriteTransactionCallback<T> callback) {
                executeCalls++;
                return new JdbcCrudWriteTransactionExecutor(dataSource).execute(policy, callback);
            }
        }
        AtomicityOnlyTransactions transactions = new AtomicityOnlyTransactions();
        assertTrue(EntityDaoBatchAtomicity.ALL_OR_NOTHING.isSupportedBy(transactions));
        CrudWriteTransactionCallback<?> callback = mock(CrudWriteTransactionCallback.class);

        ValidationException defaultFailure = assertThrows(ValidationException.class,
            () -> transactions.executeCommittedBatch(callback));

        assertTrue(defaultFailure.getMessage().contains("未实现批量最终提交边界"), defaultFailure.getMessage());
        verifyNoInteractions(callback);
        JdbcEntityDaoFactory unsupported = new JdbcEntityDaoFactory(
            registry, sqlExecutor, StandardJdbcDialect.GENERIC, JdbcEntityDaoFactory.DEFAULT_MAX_PARAMETERS,
            null, transactions, JdbcEntityDaoFactory.DEFAULT_MAX_BATCH_SIZE, new JdbcPaginationPolicy(), audit
        );
        EntityDaoBatchAuthorizationContext authorization = batch("tenant-b", "price");
        EntityDaoBatchWrite<BatchProduct, Long> writer = unsupported.scopedAuthorizedBatchWrite(TYPE, authorization);

        ValidationException batchFailure = assertThrows(ValidationException.class, () -> writer.execute(request(authorization,
            patch(1L, 0L, "name", "不能保留"), patch(2L, 7L, "price", new BigDecimal("25.50")))));

        assertEquals(defaultFailure.getMessage(), batchFailure.getMessage());
        assertEquals(0, transactions.executeCalls);
        verifyNoInteractions(safetyGuard);
        assertEquals(0, sqlExecutor.updateAttempts);
        assertEquals(initialRows, committedRows());
        assertAudit(authorization, CrudGovernanceAuditOutcome.EXECUTION_FAILED, CrudErrorStage.EXECUTE);
        assertEquals(Arrays.asList(initialRows, initialRows), audit.rowsAtRecord);
    }

    @Test
    void request_bound_to_another_authorization_is_rejected_without_sql_or_batch_audit() {
        EntityDaoBatchAuthorizationContext authorization = batch("tenant-b", "price");
        EntityDaoBatchWrite<BatchProduct, Long> writer = factory.scopedAuthorizedBatchWrite(TYPE, authorization);
        // 目标、访问者、操作都相同，仍不能借用另一批次的授权请求。
        EntityDaoBatchWriteRequest<BatchProduct, Long> anotherRequest = request(batch("tenant-b", "price"),
            patch(1L, 0L, "name", "不能保留"), patch(2L, 7L, "price", new BigDecimal("25.50")));

        PermissionDeniedException failure = assertThrows(PermissionDeniedException.class,
            () -> writer.execute(anotherRequest));

        assertTrue(failure.getMessage().contains("不属于本次授权调用"), failure.getMessage());
        verifyNoInteractions(safetyGuard);
        assertEquals(0, sqlExecutor.updateAttempts);
        assertEquals(initialRows, committedRows());
        assertTrue(audit.events.isEmpty());
    }

    @Test
    void second_targets_denied_field_rejects_whole_batch_before_any_guarded_sql() {
        EntityDaoBatchAuthorizationContext authorization = batch("tenant-b", "name");

        PermissionDeniedException failure = assertThrows(PermissionDeniedException.class,
            () -> execute(authorization, patch(1L, 0L, "name", "不能保留"),
                patch(2L, 7L, "price", new BigDecimal("25.50"))));

        assertTrue(failure.getMessage().contains("price"), failure.getMessage());
        assertRejectedBeforeSql(authorization);
    }

    @Test
    void second_targets_missing_version_rejects_whole_batch_before_any_guarded_sql() {
        EntityDaoBatchAuthorizationContext authorization = batch("tenant-b", "price");

        assertThrows(ValidationException.class, () -> execute(authorization,
            patch(1L, 0L, "name", "不能保留"), patch(2L, null, "price", new BigDecimal("25.50"))));

        assertRejectedBeforeSql(authorization);
    }

    @Test
    void active_outer_jdbc_transaction_is_rejected_without_committing_outer_writes() {
        EntityDaoBatchAuthorizationContext authorization = batch("tenant-b", "price");
        EntityDaoBatchWrite<BatchProduct, Long> batch = factory.scopedAuthorizedBatchWrite(TYPE, authorization);
        EntityDaoBatchWriteRequest<BatchProduct, Long> request = request(authorization,
            patch(1L, 0L, "name", "批量更新"), patch(2L, 7L, "price", new BigDecimal("25.50")));
        TransactionTemplate outer = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        outer.execute(status -> {
            jdbc.update("update t_batch_product set name=? where id=1", "外层未提交");
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            ValidationException failure = assertThrows(ValidationException.class, () -> batch.execute(request));
            assertTrue(failure.getMessage().contains("外层事务"), failure.getMessage());
            verifyNoInteractions(safetyGuard);
            assertEquals("外层未提交", jdbc.queryForObject("select name from t_batch_product where id=1", String.class));
            assertEquals(initialRows, committedRows());
            status.setRollbackOnly();
            return null;
        });

        assertEquals(initialRows, committedRows());
        assertTrue(sqlExecutor.affectedRows.isEmpty());
        assertAudit(authorization, CrudGovernanceAuditOutcome.EXECUTION_FAILED, CrudErrorStage.EXECUTE);
    }

    @Test
    void audit_failure_after_commit_preserves_success_and_attempts_remaining_targets() {
        audit.throwOnRecord = true;
        EntityDaoBatchAuthorizationContext authorization = batch("tenant-b", "price");

        EntityDaoBatchExecutionResult result = execute(authorization,
            patch(1L, 0L, "name", "改名一"), patch(2L, 7L, "price", new BigDecimal("25.50")));

        assertSucceeded(result);
        List<String> expected = Arrays.asList(
            "1|改名一|10.00|tenant-a|1|0", "2|商品二|25.50|tenant-b|8|0"
        );
        assertEquals(expected, committedRows());
        assertAudit(authorization, CrudGovernanceAuditOutcome.SUCCESS, null);
        assertEquals(Arrays.asList(false, false), audit.transactionActive);
        assertEquals(Arrays.asList(expected, expected), audit.rowsAtRecord);
    }

    @Test
    void audit_failure_after_rollback_preserves_original_write_miss() {
        audit.throwOnRecord = true;
        EntityDaoBatchAuthorizationContext authorization = batch("tenant-b", "price");

        EntityDaoWriteMissException failure = assertThrows(EntityDaoWriteMissException.class,
            () -> execute(authorization, patch(1L, 0L, "name", "不能保留"),
                patch(2L, 6L, "price", new BigDecimal("25.50"))));

        assertRolledBack(authorization);
        assertEquals(2, failure.getSuppressed().length);
        assertSame(audit.failures.get(0), failure.getSuppressed()[0]);
        assertSame(audit.failures.get(1), failure.getSuppressed()[1]);
    }

    private void assertSucceeded(EntityDaoBatchExecutionResult result) {
        assertEquals(EntityDaoBatchAtomicity.ALL_OR_NOTHING, result.getAtomicity());
        assertFalse(result.hasFailure());
        assertEquals(Arrays.asList("1", "2"), new ArrayList<String>(result.getItemsByTarget().keySet()));
        for (EntityDaoBatchExecutionResult.Item item : result.getItems()) {
            assertEquals(EntityDaoBatchExecutionResult.Status.SUCCEEDED, item.getStatus());
        }
    }

    private void assertRolledBack(EntityDaoBatchAuthorizationContext authorization) {
        // 确认第一项确实已在事务内写入，排除因第一项未执行而误判回滚成功。
        assertEquals(Arrays.asList(1, 0), sqlExecutor.affectedRows);
        assertEquals(Arrays.asList(1L, 1L), sqlExecutor.firstVersionsDuringWrites);
        assertEquals(Arrays.asList(0, 0), sqlExecutor.auditCountsDuringWrites);
        assertEquals(initialRows, committedRows());
        assertAudit(authorization, CrudGovernanceAuditOutcome.EXECUTION_FAILED, CrudErrorStage.EXECUTE);
        assertEquals(Arrays.asList(false, false), audit.transactionActive);
        assertEquals(Arrays.asList(initialRows, initialRows), audit.rowsAtRecord);
    }

    private void assertRejectedBeforeSql(EntityDaoBatchAuthorizationContext authorization) {
        verifyNoInteractions(safetyGuard);
        assertTrue(sqlExecutor.affectedRows.isEmpty());
        assertEquals(initialRows, committedRows());
        assertAudit(authorization, CrudGovernanceAuditOutcome.GOVERNANCE_DENIED, CrudErrorStage.GOVERNANCE);
    }

    private void assertAudit(EntityDaoBatchAuthorizationContext authorization,
        CrudGovernanceAuditOutcome outcome, CrudErrorStage stage) {
        assertEquals(2, audit.events.size());
        for (int i = 0; i < audit.events.size(); i++) {
            CrudGovernanceAuditEvent event = audit.events.get(i);
            assertSame(authorization.getAuthorizations().get(i).getResult(), event.getAuthorizationResult());
            assertSame(visitor, event.getVisitor());
            assertEquals(authorization.getTargetIds().get(i), event.getAuthorizationResult().getRequest().getTargetId());
            assertEquals(outcome, event.getOutcome());
            assertEquals(stage, event.getStage());
            assertEquals(outcome == CrudGovernanceAuditOutcome.SUCCESS ? CrudGovernanceAuditReasonCode.NONE
                : outcome == CrudGovernanceAuditOutcome.GOVERNANCE_DENIED ? CrudGovernanceAuditReasonCode.PERMISSION_DENIED
                : CrudGovernanceAuditReasonCode.EXECUTION_FAILED, event.getReason());
        }
    }

    private EntityDaoBatchAuthorizationContext batch(String secondTenant, String secondWriteField) {
        return EntityDaoBatchAuthorizationContext.of(Arrays.asList(
            authorization("1", "tenant-a", "name"), authorization("2", secondTenant, secondWriteField)
        ));
    }

    private EntityDaoAuthorizationContext authorization(String id, String tenant, String writeField) {
        AuthorizationRequest request = new AuthorizationRequest(visitor,
            new BusinessOperation(registry.getEntityMeta(BatchProduct.class).getResourceDescriptor().getResourceCode(), "update"), id);
        AuthorizationResult result = mock(AuthorizationResult.class);
        when(result.getDecision()).thenReturn(AccessDecision.ALLOW);
        when(result.getRequest()).thenReturn(request);
        when(result.getScope()).thenReturn(AuthorizationScope.equal("tenantId", tenant));
        when(result.getFields()).thenReturn(AuthorizationFields.of(
            Collections.singletonMap(AuthorizationFields.Use.WRITE, Collections.singletonList(writeField))
        ));
        return EntityDaoAuthorizationContext.of(request, result);
    }

    private UpdatePatch<BatchProduct> patch(Long id, Long version, String field, Object value) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", id);
        if (version != null) {
            payload.put("version", version);
        }
        payload.put(field, value);
        return new DefaultCommandPayloadBinder().bindUpdatePatch(payload, BatchProduct.class,
            registry.getEntityMeta(BatchProduct.class));
    }

    private EntityDaoBatchWriteRequest<BatchProduct, Long> request(EntityDaoBatchAuthorizationContext authorization,
        UpdatePatch<BatchProduct> first, UpdatePatch<BatchProduct> second) {
        return EntityDaoBatchWriteRequest.of(authorization, Arrays.asList(
            EntityDaoBatchWriteRequest.Item.update("1", 1L, first), EntityDaoBatchWriteRequest.Item.update("2", 2L, second)
        ));
    }

    private EntityDaoBatchExecutionResult execute(EntityDaoBatchAuthorizationContext authorization,
        UpdatePatch<BatchProduct> first, UpdatePatch<BatchProduct> second) {
        return factory.scopedAuthorizedBatchWrite(TYPE, authorization).execute(request(authorization, first, second));
    }

    /** 直接获取新连接，不借用 Spring 线程绑定连接，观测已提交数据库状态。 */
    private List<String> committedRows() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(SELECT_ROWS)) {
            List<String> result = new ArrayList<String>();
            while (rows.next()) {
                result.add(rows.getLong("id") + "|" + rows.getString("name") + "|"
                    + rows.getBigDecimal("price").toPlainString() + "|" + rows.getString("tenant_id")
                    + "|" + rows.getLong("version") + "|" + rows.getInt("is_deleted"));
            }
            return result;
        } catch (SQLException failure) {
            throw new AssertionError("独立连接读取提交状态失败", failure);
        }
    }

    private final class ObservingSqlExecutor extends JdbcGuardedSqlExecutor {
        private int updateAttempts;
        private final List<Integer> affectedRows = new ArrayList<Integer>();
        private final List<Long> firstVersionsDuringWrites = new ArrayList<Long>();
        private final List<Integer> auditCountsDuringWrites = new ArrayList<Integer>();
        private final List<List<String>> committedRowsDuringWrites = new ArrayList<List<String>>();

        private ObservingSqlExecutor() {
            super(jdbc, safetyGuard, new SqlExecutionLogger());
        }

        @Override
        public int update(String sql, List<Object> args, CrudExecutionContext context) {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), "批量 SQL 必须在真实事务内执行");
            updateAttempts++;
            int rows = super.update(sql, args, context);
            affectedRows.add(rows);
            firstVersionsDuringWrites.add(jdbc.queryForObject("select version from t_batch_product where id=1", Long.class));
            auditCountsDuringWrites.add(audit.events.size());
            committedRowsDuringWrites.add(committedRows());
            return rows;
        }
    }

    private final class RecordingAuditRecorder implements CrudGovernanceAuditRecorder {
        private final List<CrudGovernanceAuditEvent> events = new ArrayList<CrudGovernanceAuditEvent>();
        private final List<Boolean> transactionActive = new ArrayList<Boolean>();
        private final List<List<String>> rowsAtRecord = new ArrayList<List<String>>();
        private final List<IllegalStateException> failures = new ArrayList<IllegalStateException>();
        private boolean throwOnRecord;

        @Override
        public void record(CrudGovernanceAuditEvent event) {
            events.add(event);
            transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
            rowsAtRecord.add(committedRows());
            if (throwOnRecord) {
                IllegalStateException failure = new IllegalStateException("测试审计故障：" + events.size());
                failures.add(failure);
                throw failure;
            }
        }
    }

    @EntCrudEntity(table = "t_batch_product", idPolicy = CrudIdPolicy.EXPLICIT,
        scopeFields = {"tenantId"}, logicDeleteField = "isDeleted",
        logicDeleteNotDeletedValue = "0", logicDeleteDeletedValue = "1")
    public static class BatchProduct {
        private Long id;
        private String name;
        private BigDecimal price;
        private String tenantId;
        private Long version;
        private Integer isDeleted;
    }
}
