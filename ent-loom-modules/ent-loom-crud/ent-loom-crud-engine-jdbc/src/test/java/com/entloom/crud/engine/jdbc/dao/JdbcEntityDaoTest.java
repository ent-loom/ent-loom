package com.entloom.crud.engine.jdbc.dao;

import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityDaoAuthorizationContext;
import com.entloom.crud.core.capability.dao.EntityDaoBatch;
import com.entloom.crud.core.capability.dao.EntityDaoBatchAuthorizationContext;
import com.entloom.crud.core.capability.dao.EntityDaoBatchAtomicity;
import com.entloom.crud.core.capability.dao.EntityDaoBatchExecutionResult;
import com.entloom.crud.core.capability.dao.EntityDaoBatchReadResult;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.capability.dao.RowConstraint;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionCallback;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionPolicy;
import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.core.exception.EntityDaoConstraintException;
import com.entloom.crud.core.exception.EntityDaoWriteMissException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.api.enums.CrudIdPolicy;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.runtime.context.CrudExecutionContext;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationResult;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditReasonCode;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.CrudRuntimeModel;
import com.entloom.crud.engine.jdbc.log.SqlExecutionLogger;
import com.entloom.crud.engine.jdbc.security.JdbcGuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.security.JdbcInsertScopeDatabaseValidator;
import com.entloom.crud.engine.jdbc.security.SqlIdentifierAllowlistValidator;
import com.entloom.crud.engine.jdbc.security.SqlParameterLimiter;
import com.entloom.crud.engine.jdbc.security.SqlSafetyGuard;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;
import com.entloom.crud.engine.jdbc.dao.JdbcPaginationPolicy;
import com.entloom.crud.engine.jdbc.test.entity.OrderTestEntity;
import com.entloom.crud.engine.jdbc.test.support.EngineJdbcTestSupport;
import com.entloom.crud.core.governance.admission.AccessContextScope;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcEntityDaoTest extends EngineJdbcTestSupport {
    private static final EntityType<OrderTestEntity, Long> ORDER_TYPE =
        EntityType.of(OrderTestEntity.class, Long.class);

    @Test
    void generated_id_insert_omits_id_column_and_backfills_entity() {
        LinkedHashMap<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", new EntityFieldMeta("id", Long.class, "id", false, false, true, true));
        fields.put("name", new EntityFieldMeta("name", String.class, "name", true, false, true, true));
        EntityMeta generatedMeta = new EntityMeta(
            GeneratedEntity.class,
            new ResourceDescriptor(GeneratedEntity.class, "generated", "test-service", Collections.emptyList()),
            "t_generated",
            "id",
            EntityIdPolicy.GENERATED,
            null,
            fields
        );
        EntityMetaRegistry registry = new CrudRuntimeModelBackedEntityMetaRegistry(
            CrudRuntimeModel.from(Collections.singletonList(generatedMeta), Collections.emptyList())
        );
        GeneratedKeyExecutor executor = new GeneratedKeyExecutor();
        EntityDao<GeneratedEntity, Long> dao = new JdbcEntityDaoFactory(registry, executor)
            .scoped(EntityType.of(GeneratedEntity.class, Long.class), EntityAccessScope.unrestricted());

        GeneratedEntity entity = new GeneratedEntity();
        entity.setName("generated");
        Assertions.assertEquals(Long.valueOf(9527L), dao.insert(entity));
        Assertions.assertEquals(Long.valueOf(9527L), entity.getId());
        Assertions.assertTrue(executor.sql.contains("(name)"), executor.sql);
        Assertions.assertFalse(executor.sql.contains("id)"), executor.sql);
        Assertions.assertThrows(ValidationException.class, () -> dao.insert(entity));
        entity.setId(null);
        executor.generatedKey = null;
        Assertions.assertThrows(com.entloom.crud.core.exception.EntityDaoPersistenceException.class,
            () -> dao.insert(entity));
        Assertions.assertNull(entity.getId());
        executor.generatedKey = "非法主键";
        Assertions.assertThrows(com.entloom.crud.core.exception.EntityDaoPersistenceException.class,
            () -> dao.insert(entity));
        Assertions.assertNull(entity.getId());
    }

    @Test
    void custom_guarded_executor_must_supply_scope_database_validator() {
        JdbcEntityDaoFactory factory = new JdbcEntityDaoFactory(
            metaRegistry,
            new GuardedSqlExecutor() {
                @Override
                public List<Map<String, Object>> queryForList(
                    String sql, List<Object> args, CrudExecutionContext context
                ) {
                    return Collections.emptyList();
                }

                @Override
                public Map<String, Object> queryForMap(
                    String sql, List<Object> args, CrudExecutionContext context
                ) {
                    return Collections.emptyMap();
                }

                @Override
                public Object queryForObject(
                    String sql, List<Object> args, CrudExecutionContext context
                ) {
                    return null;
                }

                @Override
                public int update(String sql, List<Object> args, CrudExecutionContext context) {
                    return 0;
                }
            }
        );

        ValidationException exception = Assertions.assertThrows(
            ValidationException.class,
            () -> factory.scoped(ORDER_TYPE, EntityAccessScope.of(RowConstraint.eq("schoolId", 198L)))
        );

        Assertions.assertTrue(exception.getMessage().contains("必须配置"), exception.getMessage());
    }

    @Test
    void direct_jdbc_dao_rejects_when_business_authorization_scope_is_open() {
        try (AccessContextScope.Scope ignored = AccessContextScope.requireAuthorization()) {
            PermissionDeniedException exception = Assertions.assertThrows(
                PermissionDeniedException.class,
                () -> entityDaoFactory.scoped(ORDER_TYPE, EntityAccessScope.unrestricted())
            );
            Assertions.assertTrue(exception.getMessage().contains("没有单次业务授权"), exception.getMessage());
        }
    }

    @Test
    void authorized_jdbc_dao_applies_target_scope_logic_delete_and_return_fields() {
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            1L, "AUTHORIZED", 10L, "tenant-a", 0
        );
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            2L, "WRONG-TENANT", 10L, "tenant-b", 0
        );
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            3L, "DELETED", 10L, "tenant-a", 1
        );

        EntityDao<OrderTestEntity, Long> dao = entityDaoFactory.scopedAuthorized(
            ORDER_TYPE, authorizationContext("1", "tenant-a")
        );
        Optional<OrderTestEntity> found = dao.findById(1L);
        Assertions.assertTrue(found.isPresent());
        Assertions.assertEquals("AUTHORIZED", found.get().getOrderNo());
        Assertions.assertNull(found.get().getTenantId());
        Assertions.assertThrows(PermissionDeniedException.class, () -> dao.findById(2L));
        EntityDao<OrderTestEntity, Long> wrongTenant = entityDaoFactory.scopedAuthorized(
            ORDER_TYPE, authorizationContext("2", "tenant-a")
        );
        Assertions.assertFalse(wrongTenant.findById(2L).isPresent());
        EntityDao<OrderTestEntity, Long> deleted = entityDaoFactory.scopedAuthorized(
            ORDER_TYPE, authorizationContext("3", "tenant-a")
        );
        Assertions.assertFalse(deleted.findById(3L).isPresent());
        Assertions.assertThrows(PermissionDeniedException.class, () -> dao.findAllById(Collections.singletonList(1L)));

        Assertions.assertEquals(5, auditRecorder.getEvents().size());
        Assertions.assertEquals(CrudGovernanceAuditOutcome.SUCCESS, auditRecorder.getEvents().get(0).getOutcome());
        Assertions.assertEquals("1", auditRecorder.getEvents().get(0).getAuthorizationResult().getRequest().getTargetId());
        Assertions.assertEquals(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED, auditRecorder.getEvents().get(1).getOutcome());
        Assertions.assertEquals(CrudGovernanceAuditReasonCode.PERMISSION_DENIED, auditRecorder.getEvents().get(1).getReason());
        Assertions.assertEquals(CrudGovernanceAuditOutcome.SUCCESS, auditRecorder.getEvents().get(2).getOutcome());
        Assertions.assertEquals(CrudGovernanceAuditOutcome.SUCCESS, auditRecorder.getEvents().get(3).getOutcome());
        Assertions.assertEquals(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED,
            auditRecorder.getEvents().get(4).getOutcome());
        Assertions.assertEquals(CrudGovernanceAuditReasonCode.UNSUPPORTED_OPERATION,
            auditRecorder.getEvents().get(4).getReason());
    }

    @Test
    void authorized_jdbc_dao_rejects_batch_shapes_before_storage_access() {
        EntityDao<OrderTestEntity, Long> dao = entityDaoFactory.scopedAuthorized(
            ORDER_TYPE, authorizationContext("1", "tenant-a", "view", "orderNo")
        );
        OrderTestEntity entity = order(1L, "批量拒绝");
        List<OrderTestEntity> entities = Collections.singletonList(entity);

        for (Executable call : Arrays.<Executable>asList(
            () -> dao.findAllById(Collections.singletonList(1L)),
            () -> dao.findAllByIdAsMap(Collections.singletonList(1L)),
            () -> dao.insertAll(entities),
            () -> dao.updateAll(entities),
            () -> dao.deleteAll(entities)
        )) {
            PermissionDeniedException failure = Assertions.assertThrows(
                PermissionDeniedException.class, call
            );
            Assertions.assertTrue(failure.getMessage().contains("逐目标授权"), failure.getMessage());
        }
        Assertions.assertEquals(5, auditRecorder.getEvents().size());
        for (int i = 0; i < auditRecorder.getEvents().size(); i++) {
            Assertions.assertEquals(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED,
                auditRecorder.getEvents().get(i).getOutcome());
            Assertions.assertEquals(CrudGovernanceAuditReasonCode.UNSUPPORTED_OPERATION,
                auditRecorder.getEvents().get(i).getReason());
        }
    }

    @Test
    void authorized_jdbc_dao_requires_target_bound_authorization_before_creation() {
        EntityDaoAuthorizationContext targetless = authorizationContext(null, "tenant-a");

        PermissionDeniedException failure = Assertions.assertThrows(
            PermissionDeniedException.class,
            () -> entityDaoFactory.scopedAuthorized(ORDER_TYPE, targetless)
        );
        Assertions.assertTrue(failure.getMessage().contains("单一目标"), failure.getMessage());
    }

    @Test
    void authorized_jdbc_batch_reads_each_target_with_its_own_scope() {
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            1L, "BATCH-A", 10L, "tenant-a", 0
        );
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            2L, "BATCH-B", 10L, "tenant-b", 0
        );
        VisitorContext visitor = VisitorContext.anonymous("dao-batch", "request-1", null);
        EntityDaoBatchAuthorizationContext authorization = EntityDaoBatchAuthorizationContext.of(
            Arrays.asList(
                authorizationContext(visitor, "1", "tenant-a"),
                authorizationContext(visitor, "2", "tenant-b")
            )
        );

        EntityDaoBatch<OrderTestEntity, Long> batch = entityDaoFactory.scopedAuthorizedBatch(
            ORDER_TYPE, authorization
        );
        Map<Long, OrderTestEntity> values = batch.findAllByIdAsMap(Arrays.asList(1L, 2L));

        Assertions.assertEquals(Arrays.asList("1", "2"), batch.getTargetIds());
        Assertions.assertEquals("BATCH-A", values.get(1L).getOrderNo());
        Assertions.assertEquals("BATCH-B", values.get(2L).getOrderNo());
        Assertions.assertThrows(PermissionDeniedException.class,
            () -> batch.findAllById(Collections.singletonList(3L)));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> values.clear());
    }

    @Test
    void authorized_jdbc_batch_read_reports_success_for_each_target() {
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            11L, "READ-OK-A", 10L, "tenant-a", 0
        );
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            12L, "READ-OK-B", 10L, "tenant-b", 0
        );
        VisitorContext visitor = VisitorContext.anonymous("dao-batch-read", "request-1", null);
        EntityDaoBatch<OrderTestEntity, Long> batch = entityDaoFactory.scopedAuthorizedBatch(
            ORDER_TYPE,
            EntityDaoBatchAuthorizationContext.of(Arrays.asList(
                authorizationContext(visitor, "11", "tenant-a"),
                authorizationContext(visitor, "12", "tenant-b")
            ))
        );

        EntityDaoBatchReadResult<OrderTestEntity, Long> result = batch.readById(Arrays.asList(11L, 12L));

        Assertions.assertEquals(EntityDaoBatchAtomicity.ALL_OR_NOTHING, result.getExecution().getAtomicity());
        Assertions.assertEquals(
            Arrays.asList(EntityDaoBatchExecutionResult.Status.SUCCEEDED,
                EntityDaoBatchExecutionResult.Status.SUCCEEDED),
            statuses(result)
        );
        Assertions.assertEquals(Arrays.asList(11L, 12L), new ArrayList<Long>(result.getValues().keySet()));
        Assertions.assertEquals("READ-OK-A", result.getValues().get(11L).getOrderNo());
        Assertions.assertEquals("READ-OK-B", result.getValues().get(12L).getOrderNo());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> result.getValues().clear());
    }

    @Test
    void authorized_jdbc_batch_read_consumes_return_fields_per_target() {
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            41L, "FIELD-A", 10L, "tenant-a", 0
        );
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            42L, "FIELD-B", 10L, "tenant-a", 0
        );
        VisitorContext visitor = VisitorContext.anonymous("dao-batch-fields", "request-1", null);
        EntityDaoBatch<OrderTestEntity, Long> batch = entityDaoFactory.scopedAuthorizedBatch(
            ORDER_TYPE,
            EntityDaoBatchAuthorizationContext.of(Arrays.asList(
                authorizationContextWithReturnFields(visitor, "41", "tenant-a", "id", "orderNo"),
                authorizationContextWithReturnFields(visitor, "42", "tenant-a", "id")
            ))
        );

        EntityDaoBatchReadResult<OrderTestEntity, Long> result = batch.readById(Arrays.asList(41L, 42L));

        Assertions.assertEquals("FIELD-A", result.getValues().get(41L).getOrderNo());
        Assertions.assertNull(result.getValues().get(42L).getOrderNo());
        Assertions.assertEquals(Long.valueOf(42L), result.getValues().get(42L).getId());
    }

    @Test
    void authorized_jdbc_batch_read_rejects_invalid_ids_before_storage() {
        GuardedSqlExecutor storageForbidden = new GuardedSqlExecutor() {
            @Override
            public List<Map<String, Object>> queryForList(
                String sql, List<Object> args, CrudExecutionContext context
            ) {
                throw new AssertionError("非法批量主键不应进入查询");
            }

            @Override
            public Map<String, Object> queryForMap(
                String sql, List<Object> args, CrudExecutionContext context
            ) {
                throw new AssertionError("非法批量主键不应进入查询");
            }

            @Override
            public Object queryForObject(
                String sql, List<Object> args, CrudExecutionContext context
            ) {
                throw new AssertionError("非法批量主键不应进入查询");
            }

            @Override
            public int update(String sql, List<Object> args, CrudExecutionContext context) {
                throw new AssertionError("非法批量主键不应进入更新");
            }
        };
        JdbcEntityDaoFactory factory = new JdbcEntityDaoFactory(
            metaRegistry,
            storageForbidden,
            StandardJdbcDialect.GENERIC,
            JdbcEntityDaoFactory.DEFAULT_MAX_PARAMETERS,
            new JdbcInsertScopeDatabaseValidator(dataSource, metaRegistry),
            null,
            JdbcEntityDaoFactory.DEFAULT_MAX_BATCH_SIZE,
            new JdbcPaginationPolicy(),
            auditRecorder
        );
        VisitorContext visitor = VisitorContext.anonymous("dao-batch-read", "request-1", null);
        EntityDaoBatch<OrderTestEntity, Long> batch = factory.scopedAuthorizedBatch(
            ORDER_TYPE,
            EntityDaoBatchAuthorizationContext.of(Arrays.asList(
                authorizationContext(visitor, "21", "tenant-a"),
                authorizationContext(visitor, "22", "tenant-a")
            ))
        );

        Assertions.assertThrows(ValidationException.class, () -> batch.readById(null));
        Assertions.assertThrows(ValidationException.class,
            () -> batch.readById(Collections.singletonList(21L)));
        Assertions.assertThrows(PermissionDeniedException.class,
            () -> batch.readById(Arrays.asList(21L, 21L)));
        Assertions.assertThrows(PermissionDeniedException.class,
            () -> batch.readById(Arrays.asList(21L, 99L)));
    }

    @Test
    void authorized_jdbc_batch_read_reports_no_data_without_failure() {
        EntityDaoBatch<OrderTestEntity, Long> batch = entityDaoFactory.scopedAuthorizedBatch(
            ORDER_TYPE,
            EntityDaoBatchAuthorizationContext.of(Collections.singletonList(
                authorizationContext("13", "tenant-a")
            ))
        );

        EntityDaoBatchReadResult<OrderTestEntity, Long> result = batch.readById(Collections.singletonList(13L));

        Assertions.assertEquals(EntityDaoBatchExecutionResult.Status.NO_DATA,
            result.getExecution().getItems().get(0).getStatus());
        Assertions.assertFalse(result.getExecution().hasFailure());
        Assertions.assertTrue(result.getValues().isEmpty());
    }

    @Test
    void authorized_jdbc_batch_read_reports_sql_failure_without_throwing() {
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            14L, "READ-SQL-FAILURE", 10L, "tenant-a", 0
        );
        EntityDaoBatch<OrderTestEntity, Long> batch = entityDaoFactory.scopedAuthorizedBatch(
            ORDER_TYPE,
            EntityDaoBatchAuthorizationContext.of(Collections.singletonList(
                authorizationContext("14", "tenant-a")
            ))
        );
        jdbcTemplate.execute("drop table t_order");

        EntityDaoBatchReadResult<OrderTestEntity, Long> result = batch.readById(Collections.singletonList(14L));

        Assertions.assertEquals(EntityDaoBatchExecutionResult.Status.EXECUTION_FAILED,
            result.getExecution().getItems().get(0).getStatus());
        Assertions.assertTrue(result.getExecution().hasFailure());
        Assertions.assertTrue(result.getValues().isEmpty());
        Assertions.assertEquals(CrudGovernanceAuditOutcome.EXECUTION_FAILED,
            auditRecorder.getEvents().get(0).getOutcome());
    }

    @Test
    void authorized_jdbc_batch_read_declares_explicit_partial_for_mixed_results() {
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            15L, "READ-MIXED-OK", 10L, "tenant-a", 0
        );
        SqlSafetyGuard safetyGuard = new SqlSafetyGuard(
            new SqlIdentifierAllowlistValidator(metaRegistry), new SqlParameterLimiter()
        );
        JdbcGuardedSqlExecutor delegate = new JdbcGuardedSqlExecutor(
            jdbcTemplate, safetyGuard, new SqlExecutionLogger()
        );
        GuardedSqlExecutor failingExecutor = new GuardedSqlExecutor() {
            @Override
            public List<Map<String, Object>> queryForList(
                String sql, List<Object> args, CrudExecutionContext context
            ) {
                if (args.contains(Long.valueOf(16L))) {
                    throw new IllegalStateException("模拟目标 16 查询失败");
                }
                return delegate.queryForList(sql, args, context);
            }

            @Override
            public Map<String, Object> queryForMap(
                String sql, List<Object> args, CrudExecutionContext context
            ) {
                return delegate.queryForMap(sql, args, context);
            }

            @Override
            public Object queryForObject(
                String sql, List<Object> args, CrudExecutionContext context
            ) {
                return delegate.queryForObject(sql, args, context);
            }

            @Override
            public int update(String sql, List<Object> args, CrudExecutionContext context) {
                return delegate.update(sql, args, context);
            }
        };
        JdbcEntityDaoFactory factory = new JdbcEntityDaoFactory(
            metaRegistry,
            failingExecutor,
            StandardJdbcDialect.GENERIC,
            JdbcEntityDaoFactory.DEFAULT_MAX_PARAMETERS,
            new JdbcInsertScopeDatabaseValidator(dataSource, metaRegistry),
            null,
            JdbcEntityDaoFactory.DEFAULT_MAX_BATCH_SIZE,
            new JdbcPaginationPolicy(),
            auditRecorder
        );
        VisitorContext visitor = VisitorContext.anonymous("dao-batch-read", "request-1", null);
        EntityDaoBatch<OrderTestEntity, Long> batch = factory.scopedAuthorizedBatch(
            ORDER_TYPE,
            EntityDaoBatchAuthorizationContext.of(Arrays.asList(
                authorizationContext(visitor, "15", "tenant-a"),
                authorizationContext(visitor, "16", "tenant-a")
            ))
        );

        EntityDaoBatchReadResult<OrderTestEntity, Long> result = batch.readById(Arrays.asList(15L, 16L));

        Assertions.assertEquals(EntityDaoBatchAtomicity.EXPLICIT_PARTIAL, result.getExecution().getAtomicity());
        Assertions.assertEquals(
            Arrays.asList(EntityDaoBatchExecutionResult.Status.SUCCEEDED,
                EntityDaoBatchExecutionResult.Status.EXECUTION_FAILED),
            statuses(result)
        );
        Assertions.assertEquals("READ-MIXED-OK", result.getValues().get(15L).getOrderNo());
        Assertions.assertFalse(result.getValues().containsKey(16L));
        Assertions.assertEquals(2, auditRecorder.getEvents().size());
        Assertions.assertEquals("15", auditRecorder.getEvents().get(0)
            .getAuthorizationResult().getRequest().getTargetId());
        Assertions.assertEquals(CrudGovernanceAuditOutcome.SUCCESS,
            auditRecorder.getEvents().get(0).getOutcome());
        Assertions.assertEquals("16", auditRecorder.getEvents().get(1)
            .getAuthorizationResult().getRequest().getTargetId());
        Assertions.assertEquals(CrudGovernanceAuditOutcome.EXECUTION_FAILED,
            auditRecorder.getEvents().get(1).getOutcome());
    }

    @Test
    void unsupported_shape_keeps_permission_denied_when_audit_fails() {
        JdbcEntityDaoFactory factory = newFactory(
            metaRegistry,
            event -> { throw new IllegalStateException("审计记录器故障"); }
        );
        EntityDao<OrderTestEntity, Long> dao = factory.scopedAuthorized(
            ORDER_TYPE, authorizationContext("1", "tenant-a")
        );

        PermissionDeniedException failure = Assertions.assertThrows(
            PermissionDeniedException.class,
            () -> dao.findAllById(Collections.singletonList(1L))
        );
        Assertions.assertTrue(failure.getMessage().contains("findAllById"), failure.getMessage());
    }

    @Test
    void authorized_jdbc_dao_records_sql_failure_as_execution_failure() {
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            4L, "SQL-FAILURE", 10L, "tenant-a", 0
        );
        EntityDao<OrderTestEntity, Long> dao = entityDaoFactory.scopedAuthorized(
            ORDER_TYPE, authorizationContext("4", "tenant-a")
        );
        jdbcTemplate.execute("drop table t_order");

        Assertions.assertThrows(RuntimeException.class, () -> dao.findById(4L));
        Assertions.assertEquals(CrudGovernanceAuditOutcome.EXECUTION_FAILED,
            auditRecorder.getEvents().get(0).getOutcome());
        Assertions.assertEquals(CrudGovernanceAuditReasonCode.EXECUTION_FAILED,
            auditRecorder.getEvents().get(0).getReason());
    }

    @Test
    void authorized_jdbc_dao_supports_single_target_update_and_delete() {
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            5L, "DAO-WRITE", 10L, "tenant-a", 0
        );

        EntityDao<OrderTestEntity, Long> updateDao = entityDaoFactory.scopedAuthorized(
            ORDER_TYPE, authorizationContext("5", "tenant-a", "update", "orderNo")
        );
        Assertions.assertEquals(1, updateDao.updateById(5L, patch(5L, "DAO-WRITE-UPDATED")));
        Assertions.assertEquals("DAO-WRITE-UPDATED", jdbcTemplate.queryForObject(
            "select order_no from t_order where id=?", String.class, 5L
        ));

        EntityDao<OrderTestEntity, Long> deleteDao = entityDaoFactory.scopedAuthorized(
            ORDER_TYPE, authorizationContext("5", "tenant-a", "delete")
        );
        Assertions.assertEquals(1, deleteDao.deleteById(5L));
        Assertions.assertEquals(1, jdbcTemplate.queryForObject(
            "select is_deleted from t_order where id=?", Integer.class, 5L
        ).intValue());
        Assertions.assertEquals(2, auditRecorder.getEvents().size());
        Assertions.assertEquals(CrudGovernanceAuditOutcome.SUCCESS,
            auditRecorder.getEvents().get(0).getOutcome());
        Assertions.assertEquals("update", auditRecorder.getEvents().get(0)
            .getAuthorizationResult().getRequest().getOperation().getOperationCode());
        Assertions.assertEquals("delete", auditRecorder.getEvents().get(1)
            .getAuthorizationResult().getRequest().getOperation().getOperationCode());
    }

    @Test
    void authorized_jdbc_dao_rejects_write_without_operation_or_field_grant() {
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            6L, "DAO-WRITE-DENIED", 10L, "tenant-a", 0
        );

        EntityDao<OrderTestEntity, Long> noFieldDao = entityDaoFactory.scopedAuthorized(
            ORDER_TYPE, authorizationContext("6", "tenant-a", "update")
        );
        Assertions.assertThrows(
            PermissionDeniedException.class,
            () -> noFieldDao.updateById(6L, patch(6L, "FORGED"))
        );
        EntityDao<OrderTestEntity, Long> viewDao = entityDaoFactory.scopedAuthorized(
            ORDER_TYPE, authorizationContext("6", "tenant-a", "view", "orderNo")
        );
        Assertions.assertThrows(
            PermissionDeniedException.class,
            () -> viewDao.updateById(6L, patch(6L, "FORGED"))
        );
        Assertions.assertEquals("DAO-WRITE-DENIED", jdbcTemplate.queryForObject(
            "select order_no from t_order where id=?", String.class, 6L
        ));
        Assertions.assertEquals(2, auditRecorder.getEvents().size());
        Assertions.assertEquals(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED,
            auditRecorder.getEvents().get(0).getOutcome());
        Assertions.assertEquals(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED,
            auditRecorder.getEvents().get(1).getOutcome());
    }

    @Test
    void authorized_jdbc_dao_rejects_scope_miss() {
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            7L, "DAO-SCOPE", 10L, "tenant-b", 0
        );
        EntityDao<OrderTestEntity, Long> wrongScope = entityDaoFactory.scopedAuthorized(
            ORDER_TYPE, authorizationContext("7", "tenant-a", "update", "orderNo")
        );
        Assertions.assertThrows(
            EntityDaoWriteMissException.class,
            () -> wrongScope.updateById(7L, patch(7L, "NO-MATCH"))
        );
        Assertions.assertEquals("DAO-SCOPE", jdbcTemplate.queryForObject(
            "select order_no from t_order where id=?", String.class, 7L
        ));
        Assertions.assertEquals(CrudGovernanceAuditOutcome.EXECUTION_FAILED,
            auditRecorder.getEvents().get(0).getOutcome());
    }

    @Test
    void authorized_jdbc_dao_requires_transaction_before_storage_access() {
        SqlSafetyGuard safetyGuard = new SqlSafetyGuard(
            new SqlIdentifierAllowlistValidator(metaRegistry), new SqlParameterLimiter()
        );
        final JdbcGuardedSqlExecutor delegate = new JdbcGuardedSqlExecutor(
            jdbcTemplate, safetyGuard, new SqlExecutionLogger()
        );
        GuardedSqlExecutor customExecutor = new GuardedSqlExecutor() {
            @Override
            public List<Map<String, Object>> queryForList(
                String sql, List<Object> args, CrudExecutionContext context
            ) {
                return delegate.queryForList(sql, args, context);
            }

            @Override
            public Map<String, Object> queryForMap(
                String sql, List<Object> args, CrudExecutionContext context
            ) {
                return delegate.queryForMap(sql, args, context);
            }

            @Override
            public Object queryForObject(
                String sql, List<Object> args, CrudExecutionContext context
            ) {
                return delegate.queryForObject(sql, args, context);
            }

            @Override
            public int update(String sql, List<Object> args, CrudExecutionContext context) {
                throw new AssertionError("缺少事务时不应访问 SQL 执行器");
            }
        };
        JdbcEntityDaoFactory noTransactionFactory = new JdbcEntityDaoFactory(
            metaRegistry,
            customExecutor,
            StandardJdbcDialect.GENERIC,
            JdbcEntityDaoFactory.DEFAULT_MAX_PARAMETERS,
            new JdbcInsertScopeDatabaseValidator(dataSource, metaRegistry),
            null,
            JdbcEntityDaoFactory.DEFAULT_MAX_BATCH_SIZE,
            new JdbcPaginationPolicy(),
            auditRecorder
        );
        EntityDao<OrderTestEntity, Long> dao = noTransactionFactory.scopedAuthorized(
            ORDER_TYPE, authorizationContext("8", "tenant-a", "delete")
        );
        Assertions.assertThrows(PermissionDeniedException.class, () -> dao.deleteById(8L));
        Assertions.assertEquals(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED,
            auditRecorder.getEvents().get(0).getOutcome());
    }

    @Test
    void batch_jdbc_dao_requires_declared_atomicity_before_transaction_callback() {
        SqlSafetyGuard safetyGuard = new SqlSafetyGuard(
            new SqlIdentifierAllowlistValidator(metaRegistry), new SqlParameterLimiter()
        );
        JdbcGuardedSqlExecutor guarded = new JdbcGuardedSqlExecutor(
            jdbcTemplate, safetyGuard, new SqlExecutionLogger()
        );
        AtomicBoolean callbackCalled = new AtomicBoolean();
        CrudWriteTransactionExecutor unsupported = new CrudWriteTransactionExecutor() {
            @Override
            public <T> T execute(
                CrudWriteTransactionPolicy policy,
                CrudWriteTransactionCallback<T> callback
            ) {
                callbackCalled.set(true);
                return callback.execute();
            }
        };
        JdbcEntityDaoFactory factory = new JdbcEntityDaoFactory(
            metaRegistry,
            guarded,
            StandardJdbcDialect.GENERIC,
            JdbcEntityDaoFactory.DEFAULT_MAX_PARAMETERS,
            new JdbcInsertScopeDatabaseValidator(dataSource, metaRegistry),
            unsupported,
            JdbcEntityDaoFactory.DEFAULT_MAX_BATCH_SIZE,
            new JdbcPaginationPolicy(),
            auditRecorder
        );
        EntityDao<OrderTestEntity, Long> dao = factory.scoped(
            ORDER_TYPE, EntityAccessScope.unrestricted()
        );
        OrderTestEntity entity = order(3901L, "能力门禁");

        ValidationException exception = Assertions.assertThrows(
            ValidationException.class,
            () -> dao.insertAll(Collections.singletonList(entity))
        );

        Assertions.assertTrue(exception.getMessage().contains("未声明支持批量原子性"));
        Assertions.assertFalse(callbackCalled.get());
        Assertions.assertEquals(0, countById(3901L));
    }

    private EntityDaoAuthorizationContext authorizationContext(String targetId, String tenantId) {
        return authorizationContext(VisitorContext.anonymous("dao-test", "request-1", null),
            targetId, tenantId, "view");
    }

    private EntityDaoAuthorizationContext authorizationContext(
        String targetId, String tenantId, String operation, String... writeFields
    ) {
        return authorizationContext(VisitorContext.anonymous("dao-test", "request-1", null),
            targetId, tenantId, operation, writeFields);
    }

    private EntityDaoAuthorizationContext authorizationContext(
        VisitorContext visitor, String targetId, String tenantId
    ) {
        return authorizationContext(visitor, targetId, tenantId, "view");
    }

    private EntityDaoAuthorizationContext authorizationContext(
        VisitorContext visitor, String targetId, String tenantId, String operation, String... writeFields
    ) {
        EntityMeta meta = metaRegistry.getEntityMeta(OrderTestEntity.class);
        AuthorizationRequest request = new AuthorizationRequest(
            visitor,
            new BusinessOperation(meta.getResourceDescriptor().getResourceCode(), operation),
            targetId
        );
        AuthorizationResult result = mock(AuthorizationResult.class);
        when(result.getDecision()).thenReturn(AccessDecision.ALLOW);
        when(result.getRequest()).thenReturn(request);
        when(result.getScope()).thenReturn(AuthorizationScope.equal("tenantId", tenantId));
        Map<AuthorizationFields.Use, java.util.Collection<String>> allowed =
            new java.util.EnumMap<AuthorizationFields.Use, java.util.Collection<String>>(AuthorizationFields.Use.class);
        allowed.put(AuthorizationFields.Use.RETURN, Arrays.asList("id", "orderNo"));
        if (writeFields != null && writeFields.length > 0) {
            allowed.put(AuthorizationFields.Use.WRITE, Arrays.asList(writeFields));
        }
        when(result.getFields()).thenReturn(AuthorizationFields.of(allowed));
        return EntityDaoAuthorizationContext.of(request, result);
    }

    private EntityDaoAuthorizationContext authorizationContextWithReturnFields(
        VisitorContext visitor, String targetId, String tenantId, String... returnFields
    ) {
        EntityMeta meta = metaRegistry.getEntityMeta(OrderTestEntity.class);
        AuthorizationRequest request = new AuthorizationRequest(
            visitor,
            new BusinessOperation(meta.getResourceDescriptor().getResourceCode(), "view"),
            targetId
        );
        AuthorizationResult result = mock(AuthorizationResult.class);
        when(result.getDecision()).thenReturn(AccessDecision.ALLOW);
        when(result.getRequest()).thenReturn(request);
        when(result.getScope()).thenReturn(AuthorizationScope.equal("tenantId", tenantId));
        Map<AuthorizationFields.Use, java.util.Collection<String>> allowed =
            new java.util.EnumMap<AuthorizationFields.Use, java.util.Collection<String>>(AuthorizationFields.Use.class);
        allowed.put(AuthorizationFields.Use.RETURN, Arrays.asList(returnFields));
        when(result.getFields()).thenReturn(AuthorizationFields.of(allowed));
        return EntityDaoAuthorizationContext.of(request, result);
    }

    private EntityDaoAuthorizationContext versionedAuthorizationContext(
        EntityMetaRegistry registry, String targetId, String operation, String... writeFields
    ) {
        EntityMeta meta = registry.getEntityMeta(VersionedEntity.class);
        VisitorContext visitor = VisitorContext.anonymous("dao-versioned", "request-1", null);
        AuthorizationRequest request = new AuthorizationRequest(
            visitor,
            new BusinessOperation(meta.getResourceDescriptor().getResourceCode(), operation),
            targetId
        );
        AuthorizationResult result = mock(AuthorizationResult.class);
        when(result.getDecision()).thenReturn(AccessDecision.ALLOW);
        when(result.getRequest()).thenReturn(request);
        when(result.getScope()).thenReturn(AuthorizationScope.equal("tenantId", "tenant-a"));
        Map<AuthorizationFields.Use, java.util.Collection<String>> allowed =
            new java.util.EnumMap<AuthorizationFields.Use, java.util.Collection<String>>(AuthorizationFields.Use.class);
        allowed.put(AuthorizationFields.Use.RETURN, Arrays.asList("id", "name"));
        if (writeFields != null && writeFields.length > 0) {
            allowed.put(AuthorizationFields.Use.WRITE, Arrays.asList(writeFields));
        }
        when(result.getFields()).thenReturn(AuthorizationFields.of(allowed));
        return EntityDaoAuthorizationContext.of(request, result);
    }

    @Test
    void should_complete_scoped_primary_key_crud_with_explicit_logic_delete_values() {
        EntityDao<OrderTestEntity, Long> dao = entityDaoFactory.scoped(
            ORDER_TYPE,
            EntityAccessScope.of(RowConstraint.and(
                RowConstraint.eq("schoolId", "198"),
                RowConstraint.eq("tenantId", "tenant-a")
            ))
        );
        OrderTestEntity order = order(10001L, "ORD-DAO");

        Assertions.assertEquals(Long.valueOf(10001L), dao.insert(order));
        Optional<OrderTestEntity> loaded = dao.findById(10001L);
        Assertions.assertTrue(loaded.isPresent());
        Assertions.assertEquals(Long.valueOf(198L), loaded.get().getSchoolId());
        Assertions.assertEquals("tenant-a", loaded.get().getTenantId());
        Assertions.assertEquals(Integer.valueOf(0), loaded.get().getIsDeleted());

        UpdatePatch<OrderTestEntity> patch = patch(10001L, "ORD-DAO-UPDATED");
        Assertions.assertEquals(1, dao.updateById(10001L, patch));
        Assertions.assertEquals("ORD-DAO-UPDATED", dao.findById(10001L).get().getOrderNo());

        Assertions.assertEquals(1, dao.deleteById(10001L));
        Assertions.assertFalse(dao.findById(10001L).isPresent());
        Assertions.assertEquals(1, jdbcTemplate.queryForObject(
            "select is_deleted from t_order where id=?", Integer.class, 10001L
        ).intValue());
    }

    @Test
    void scope_must_remain_bound_and_multi_in_must_match_entity_value() {
        java.util.List<Long> allowedSchools = new java.util.ArrayList<Long>(Collections.singletonList(198L));
        EntityDao<OrderTestEntity, Long> dao = entityDaoFactory.scoped(
            ORDER_TYPE,
            EntityAccessScope.of(RowConstraint.and(
                RowConstraint.in("schoolId", allowedSchools),
                RowConstraint.eq("tenantId", "tenant-a")
            ))
        );
        allowedSchools.add(199L);

        OrderTestEntity order = order(10002L, "ORD-DAO-IN");
        Assertions.assertEquals(Long.valueOf(10002L), dao.insert(order));
        Assertions.assertEquals(Long.valueOf(198L), dao.findById(10002L).get().getSchoolId());

        EntityDao<OrderTestEntity, Long> otherScope = entityDaoFactory.scoped(
            ORDER_TYPE,
            EntityAccessScope.of(RowConstraint.eq("schoolId", 199L))
        );
        Assertions.assertFalse(otherScope.findById(10002L).isPresent());
    }

    @Test
    void no_change_update_returns_one_and_miss_does_not_run_post_query() {
        EntityDao<OrderTestEntity, Long> dao = entityDaoFactory.scoped(
            ORDER_TYPE,
            EntityAccessScope.of(RowConstraint.eq("schoolId", 198L))
        );
        Assertions.assertEquals(Long.valueOf(10003L), dao.insert(order(10003L, "ORD-SAME")));

        Assertions.assertEquals(1, dao.updateById(10003L, patch(10003L, "ORD-SAME")));
        Assertions.assertThrows(
            EntityDaoWriteMissException.class,
            () -> dao.updateById(99999L, patch(99999L, "ORD-MISS"))
        );
    }

    @Test
    void patch_should_preserve_explicit_null_as_a_sql_value() {
        EntityDao<OrderTestEntity, Long> dao = entityDaoFactory.scoped(
            ORDER_TYPE,
            EntityAccessScope.of(RowConstraint.eq("schoolId", 198L))
        );
        dao.insert(order(10005L, "ORD-NULL"));

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("id", 10005L);
        payload.put("orderNo", null);
        UpdatePatch<OrderTestEntity> patch = new DefaultCommandPayloadBinder().bindUpdatePatch(
            payload, OrderTestEntity.class, metaRegistry.getEntityMeta(OrderTestEntity.class)
        );

        Assertions.assertEquals(1, dao.updateById(10005L, patch));
        Assertions.assertNull(dao.findById(10005L).get().getOrderNo());
    }

    @Test
    void invalid_patch_and_duplicate_primary_key_should_have_stable_errors() {
        EntityDao<OrderTestEntity, Long> dao = entityDaoFactory.scoped(
            ORDER_TYPE,
            EntityAccessScope.of(RowConstraint.eq("schoolId", 198L))
        );
        dao.insert(order(10004L, "ORD-DUP"));
        Assertions.assertThrows(EntityDaoConstraintException.class, () -> dao.insert(order(10004L, "ORD-DUP-2")));

        java.util.Map<String, Object> forged = new java.util.LinkedHashMap<String, Object>();
        forged.put("id", 10004L);
        forged.put("schoolId", 199L);
        UpdatePatch<OrderTestEntity> patch = new DefaultCommandPayloadBinder().bindUpdatePatch(
            forged, OrderTestEntity.class, metaRegistry.getEntityMeta(OrderTestEntity.class)
        );
        Assertions.assertThrows(ValidationException.class, () -> dao.updateById(10004L, patch));
    }

    @Test
    void update_unique_key_conflict_should_have_stable_constraint_error() {
        jdbcTemplate.execute("create unique index uk_t_order_order_no on t_order(order_no)");
        EntityDao<OrderTestEntity, Long> dao = entityDaoFactory.scoped(
            ORDER_TYPE,
            EntityAccessScope.of(RowConstraint.eq("schoolId", 198L))
        );
        dao.insert(order(10007L, "ORD-UNIQUE-1"));
        dao.insert(order(10008L, "ORD-UNIQUE-2"));

        Assertions.assertThrows(
            EntityDaoConstraintException.class,
            () -> dao.updateById(10008L, patch(10008L, "ORD-UNIQUE-1"))
        );
    }

    @Test
    void explicit_logic_delete_values_should_drive_dao_insert_find_and_delete() {
        EntityMetaRegistry customRegistry = registryWithLogicDeleteValues(2, 9);
        EntityDao<OrderTestEntity, Long> dao = newFactory(customRegistry).scoped(
            ORDER_TYPE,
            EntityAccessScope.of(RowConstraint.eq("schoolId", 198L))
        );

        dao.insert(order(10009L, "ORD-STATE"));
        Assertions.assertEquals(2, jdbcTemplate.queryForObject(
            "select is_deleted from t_order where id=?", Integer.class, 10009L
        ).intValue());
        Assertions.assertTrue(dao.findById(10009L).isPresent());

        Assertions.assertEquals(1, dao.deleteById(10009L));
        Assertions.assertEquals(9, jdbcTemplate.queryForObject(
            "select is_deleted from t_order where id=?", Integer.class, 10009L
        ).intValue());
        Assertions.assertFalse(dao.findById(10009L).isPresent());
    }

    @Test
    void logic_delete_unique_conflict_should_have_stable_constraint_error() {
        jdbcTemplate.execute("create unique index uk_t_order_order_no_state on t_order(order_no,is_deleted)");
        EntityMetaRegistry customRegistry = registryWithLogicDeleteValues(2, 9);
        EntityDao<OrderTestEntity, Long> dao = newFactory(customRegistry).scoped(
            ORDER_TYPE,
            EntityAccessScope.of(RowConstraint.eq("schoolId", 198L))
        );

        dao.insert(order(10010L, "ORD-STATE-CONFLICT"));
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            10011L,
            "ORD-STATE-CONFLICT",
            198L,
            "tenant-a",
            9
        );

        Assertions.assertThrows(EntityDaoConstraintException.class, () -> dao.deleteById(10010L));
        Assertions.assertEquals(2, jdbcTemplate.queryForObject(
            "select is_deleted from t_order where id=?", Integer.class, 10010L
        ).intValue());
    }

    @Test
    void concurrent_write_with_wrong_scope_cannot_modify_the_row() throws Exception {
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            10006L,
            "ORD-CONCURRENT",
            198L,
            "tenant-a",
            0
        );
        EntityDao<OrderTestEntity, Long> allowedDao = entityDaoFactory.scoped(
            ORDER_TYPE,
            EntityAccessScope.of(RowConstraint.eq("schoolId", 198L))
        );
        EntityDao<OrderTestEntity, Long> deniedDao = entityDaoFactory.scoped(
            ORDER_TYPE,
            EntityAccessScope.of(RowConstraint.eq("schoolId", 199L))
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> allowed = executor.submit(() -> {
                ready.countDown();
                start.await();
                return allowedDao.updateById(10006L, patch(10006L, "ORD-ALLOWED"));
            });
            Future<Integer> denied = executor.submit(() -> {
                ready.countDown();
                start.await();
                return deniedDao.updateById(10006L, patch(10006L, "ORD-DENIED"));
            });
            ready.await();
            start.countDown();

            Assertions.assertEquals(1, allowed.get().intValue());
            ExecutionException exception = Assertions.assertThrows(ExecutionException.class, denied::get);
            Assertions.assertInstanceOf(EntityDaoWriteMissException.class, exception.getCause());
            Assertions.assertEquals(
                "ORD-ALLOWED",
                jdbcTemplate.queryForObject("select order_no from t_order where id=?", String.class, 10006L)
            );
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void factory_should_reject_wrong_id_type_and_non_scope_constraint() {
        Assertions.assertThrows(
            ValidationException.class,
            () -> entityDaoFactory.scoped(
                EntityType.of(OrderTestEntity.class, String.class), EntityAccessScope.unrestricted()
            )
        );
        Assertions.assertThrows(
            ValidationException.class,
            () -> entityDaoFactory.scoped(
                ORDER_TYPE, EntityAccessScope.of(RowConstraint.eq("orderNo", "x"))
            )
        );
    }

    @Test
    void batch_read_update_and_delete_should_preserve_input_contract() {
        EntityDao<OrderTestEntity, Long> dao = entityDaoFactory.scoped(
            ORDER_TYPE,
            EntityAccessScope.of(RowConstraint.eq("schoolId", 198L))
        );
        List<OrderTestEntity> entities = Arrays.asList(
            order(11001L, "ORD-BATCH-1"),
            order(11002L, "ORD-BATCH-2")
        );
        List<Long> inserted = dao.insertAll(entities);

        Assertions.assertEquals(Arrays.asList(11001L, 11002L), inserted);
        Assertions.assertEquals(
            Arrays.asList(11002L, 11001L),
            ids(dao.findAllById(Arrays.asList(11002L, 99999L, 11001L, 11002L)))
        );

        Map<Long, OrderTestEntity> byId = dao.findAllByIdAsMap(
            Arrays.asList(11002L, 99999L, 11001L, 11002L)
        );
        Assertions.assertEquals(Arrays.asList(11002L, 11001L), new ArrayList<Long>(byId.keySet()));
        Assertions.assertEquals("ORD-BATCH-2", byId.get(11002L).getOrderNo());
        Assertions.assertTrue(dao.findAllByIdAsMap(Collections.<Long>emptyList()).isEmpty());
        EntityDao<OrderTestEntity, Long> otherScope = entityDaoFactory.scoped(
            ORDER_TYPE, EntityAccessScope.of(RowConstraint.eq("schoolId", 199L))
        );
        Assertions.assertTrue(otherScope.findAllByIdAsMap(inserted).isEmpty());

        entities.get(0).setOrderNo("ORD-BATCH-1-UPDATED");
        entities.get(1).setOrderNo("ORD-BATCH-2-UPDATED");
        Assertions.assertEquals(2, dao.updateAll(entities));
        Assertions.assertEquals(
            "ORD-BATCH-1-UPDATED",
            dao.findById(11001L).get().getOrderNo()
        );

        Assertions.assertEquals(2, dao.deleteAll(entities));
        Assertions.assertTrue(dao.findAllByIdAsMap(inserted).isEmpty());
        Assertions.assertEquals(0, dao.deleteById(11001L));
        Assertions.assertFalse(dao.findById(11001L).isPresent());
    }

    @Test
    void dao_batch_should_rollback_when_a_later_insert_fails() {
        jdbcTemplate.update(
            "insert into t_order(id, order_no, is_deleted) values (?,?,?)",
            12002L,
            "ORD-BATCH-EXISTING",
            0
        );
        EntityDao<OrderTestEntity, Long> dao = entityDaoFactory.scoped(
            ORDER_TYPE,
            EntityAccessScope.unrestricted()
        );

        Assertions.assertThrows(
            EntityDaoConstraintException.class,
            () -> dao.insertAll(Arrays.asList(
                order(12001L, "ORD-BATCH-FIRST"),
                order(12002L, "ORD-BATCH-CONFLICT")
            ))
        );

        Assertions.assertEquals(0, countById(12001L));
        Assertions.assertEquals(1, countById(12002L));
    }

    @Test
    void versioned_entity_should_require_and_advance_expected_version() {
        jdbcTemplate.execute("drop table if exists t_versioned_order");
        jdbcTemplate.execute(
            "create table t_versioned_order(" +
                "id bigint primary key, name varchar(64), tenant_id varchar(64) not null, version bigint not null)"
        );
        EntityMetaRegistry registry = versionedRegistry();
        EntityDao<VersionedEntity, Long> dao = versionedDao(registry);

        VersionedEntity entity = new VersionedEntity();
        entity.id = 1L;
        entity.name = "初始";
        Assertions.assertEquals(Long.valueOf(1L), dao.insert(entity));
        Assertions.assertEquals(Long.valueOf(0L), entity.version);

        entity.name = "第一次更新";
        Assertions.assertEquals(1, dao.update(entity));
        Assertions.assertEquals(Long.valueOf(1L), entity.version);

        VersionedEntity stale = new VersionedEntity();
        stale.id = 1L;
        stale.name = "过期更新";
        stale.version = 0L;
        Assertions.assertThrows(EntityDaoWriteMissException.class, () -> dao.update(stale));

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1L);
        payload.put("name", "Patch 更新");
        payload.put("version", 1L);
        UpdatePatch<VersionedEntity> patch = new DefaultCommandPayloadBinder().bindUpdatePatch(
            payload, VersionedEntity.class, registry.getEntityMeta(VersionedEntity.class)
        );
        Assertions.assertEquals(Long.valueOf(1L), patch.getExpectedVersion());
        Assertions.assertEquals(1, dao.updateById(1L, patch));
        Assertions.assertEquals(Long.valueOf(2L), patch.getEntity().version);

        Assertions.assertThrows(ValidationException.class, () -> dao.deleteById(1L));
        Assertions.assertThrows(EntityDaoWriteMissException.class, () -> dao.deleteById(1L, 1L));
        Assertions.assertEquals(1, dao.deleteById(1L, 2L));
    }

    @Test
    void versioned_entity_should_reject_missing_version_on_entity_update() {
        jdbcTemplate.execute("drop table if exists t_versioned_order");
        jdbcTemplate.execute(
            "create table t_versioned_order(" +
                "id bigint primary key, name varchar(64), tenant_id varchar(64) not null, version bigint not null)"
        );
        EntityMetaRegistry registry = versionedRegistry();
        EntityDao<VersionedEntity, Long> dao = versionedDao(registry);

        VersionedEntity entity = new VersionedEntity();
        entity.id = 2L;
        entity.name = "缺失版本";
        dao.insert(entity);
        entity.version = null;
        Assertions.assertThrows(ValidationException.class, () -> dao.update(entity));
    }

    @Test
    void authorized_jdbc_dao_supports_optimistic_lock_update_and_delete() {
        jdbcTemplate.execute("drop table if exists t_versioned_order");
        jdbcTemplate.execute(
            "create table t_versioned_order(" +
                "id bigint primary key, name varchar(64), tenant_id varchar(64) not null, version bigint not null)"
        );
        jdbcTemplate.update(
            "insert into t_versioned_order(id, name, tenant_id, version) values (?,?,?,?)",
            3L, "授权初始", "tenant-a", 0L
        );
        EntityMetaRegistry registry = versionedRegistry();
        JdbcEntityDaoFactory factory = newFactory(registry, auditRecorder);
        EntityDao<VersionedEntity, Long> updateDao = factory.scopedAuthorized(
            EntityType.of(VersionedEntity.class, Long.class),
            versionedAuthorizationContext(registry, "3", "update", "name")
        );
        Map<String, Object> missingVersionPayload = new LinkedHashMap<String, Object>();
        missingVersionPayload.put("id", 3L);
        missingVersionPayload.put("name", "缺失版本");
        UpdatePatch<VersionedEntity> missingVersion = new DefaultCommandPayloadBinder().bindUpdatePatch(
            missingVersionPayload, VersionedEntity.class, registry.getEntityMeta(VersionedEntity.class)
        );
        Assertions.assertThrows(ValidationException.class,
            () -> updateDao.updateById(3L, missingVersion));
        UpdatePatch<VersionedEntity> patch = versionedPatch(registry, 3L, "授权更新");
        Assertions.assertEquals(1, updateDao.updateById(3L, patch));
        Assertions.assertEquals("授权更新", jdbcTemplate.queryForObject(
            "select name from t_versioned_order where id=?", String.class, 3L
        ));
        Assertions.assertEquals(Long.valueOf(1L), jdbcTemplate.queryForObject(
            "select version from t_versioned_order where id=?", Long.class, 3L
        ));
        Assertions.assertEquals(Long.valueOf(1L), patch.getEntity().version);

        EntityDao<VersionedEntity, Long> staleDao = factory.scopedAuthorized(
            EntityType.of(VersionedEntity.class, Long.class),
            versionedAuthorizationContext(registry, "3", "update", "name")
        );
        UpdatePatch<VersionedEntity> stale = versionedPatch(registry, 3L, "过期更新");
        Assertions.assertThrows(EntityDaoWriteMissException.class,
            () -> staleDao.updateById(3L, stale));
        Assertions.assertEquals(CrudGovernanceAuditOutcome.EXECUTION_FAILED,
            auditRecorder.getEvents().get(2).getOutcome());

        EntityDao<VersionedEntity, Long> deleteDao = factory.scopedAuthorized(
            EntityType.of(VersionedEntity.class, Long.class),
            versionedAuthorizationContext(registry, "3", "delete")
        );
        Assertions.assertThrows(ValidationException.class, () -> deleteDao.deleteById(3L));
        Assertions.assertEquals(1, deleteDao.deleteById(3L, 1L));
        Assertions.assertEquals(0, jdbcTemplate.queryForObject(
            "select count(*) from t_versioned_order where id=?", Integer.class, 3L
        ).intValue());
        Assertions.assertEquals(5, auditRecorder.getEvents().size());
        Assertions.assertEquals(CrudGovernanceAuditOutcome.SUCCESS,
            auditRecorder.getEvents().get(4).getOutcome());
    }

    private OrderTestEntity order(Long id, String orderNo) {
        OrderTestEntity order = new OrderTestEntity();
        order.setId(id);
        order.setOrderNo(orderNo);
        return order;
    }

    private UpdatePatch<OrderTestEntity> patch(Long id, String orderNo) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("id", id);
        payload.put("orderNo", orderNo);
        return new DefaultCommandPayloadBinder().bindUpdatePatch(
            payload, OrderTestEntity.class, metaRegistry.getEntityMeta(OrderTestEntity.class)
        );
    }

    private UpdatePatch<VersionedEntity> versionedPatch(
        EntityMetaRegistry registry, Long id, String name
    ) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("id", id);
        payload.put("name", name);
        payload.put("version", 0L);
        return new DefaultCommandPayloadBinder().bindUpdatePatch(
            payload, VersionedEntity.class, registry.getEntityMeta(VersionedEntity.class)
        );
    }

    private List<Long> ids(List<OrderTestEntity> entities) {
        List<Long> result = new ArrayList<Long>();
        for (OrderTestEntity entity : entities) {
            result.add(entity.getId());
        }
        return result;
    }

    private List<EntityDaoBatchExecutionResult.Status> statuses(
        EntityDaoBatchReadResult<OrderTestEntity, Long> result
    ) {
        List<EntityDaoBatchExecutionResult.Status> statuses =
            new ArrayList<EntityDaoBatchExecutionResult.Status>();
        for (EntityDaoBatchExecutionResult.Item item : result.getExecution().getItems()) {
            statuses.add(item.getStatus());
        }
        return statuses;
    }

    private int countById(Long id) {
        return jdbcTemplate.queryForObject(
            "select count(*) from t_order where id=?",
            Integer.class,
            id
        );
    }

    private EntityMetaRegistry versionedRegistry() {
        EntityMetaRegistry registry = new CrudRuntimeModelBackedEntityMetaRegistry(
            new com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser()
                .parse(Collections.<Class<?>>singletonList(VersionedEntity.class))
        );
        registry.validateOrThrow();
        return registry;
    }

    private EntityDao<VersionedEntity, Long> versionedDao(EntityMetaRegistry registry) {
        SqlSafetyGuard safetyGuard = new SqlSafetyGuard(
            new SqlIdentifierAllowlistValidator(registry),
            new SqlParameterLimiter()
        );
        JdbcGuardedSqlExecutor executor = new JdbcGuardedSqlExecutor(
            jdbcTemplate, safetyGuard, new SqlExecutionLogger()
        );
        return new JdbcEntityDaoFactory(registry, executor).scoped(
            EntityType.of(VersionedEntity.class, Long.class), EntityAccessScope.of(RowConstraint.eq("tenantId", "tenant-a"))
        );
    }

    private EntityMetaRegistry registryWithLogicDeleteValues(int notDeleted, int deleted) {
        EntityMeta original = metaRegistry.getEntityMeta(OrderTestEntity.class);
        EntityMeta custom = new EntityMeta(
            OrderTestEntity.class,
            original.getResourceDescriptor(),
            original.getTable(),
            original.getIdField(),
            original.getIdPolicy(),
            original.getLogicDeleteField(),
            Integer.valueOf(notDeleted),
            Integer.valueOf(deleted),
            original.getFieldMetas()
        );
        return new CrudRuntimeModelBackedEntityMetaRegistry(
            CrudRuntimeModel.from(
                Collections.singletonList(custom),
                Collections.emptyList()
            )
        );
    }

    private static final class GeneratedEntity {
        private Long id;
        private String name;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private static final class GeneratedKeyExecutor implements GuardedSqlExecutor {
        private String sql;
        private Object generatedKey = Long.valueOf(9527L);

        @Override
        public List<Map<String, Object>> queryForList(String sql, List<Object> args, CrudExecutionContext context) {
            return Collections.emptyList();
        }

        @Override
        public Map<String, Object> queryForMap(String sql, List<Object> args, CrudExecutionContext context) {
            return Collections.emptyMap();
        }

        @Override
        public Object queryForObject(String sql, List<Object> args, CrudExecutionContext context) {
            return null;
        }

        @Override
        public int update(String sql, List<Object> args, CrudExecutionContext context) {
            throw new AssertionError("生成主键插入不应调用 update");
        }

        @Override
        public Object insertAndReturnGeneratedKey(
            String sql, List<Object> args, CrudExecutionContext context
        ) {
            this.sql = sql;
            return generatedKey;
        }
    }

    /** 启用乐观锁的显式主键测试实体。 */
    @EntCrudEntity(
        table = "t_versioned_order",
        idField = "id",
        idPolicy = CrudIdPolicy.EXPLICIT,
        scopeFields = {"tenantId"}
    )
    public static class VersionedEntity {
        /** 主键。 */
        private Long id;
        /** 业务名称。 */
        private String name;
        /** 租户范围。 */
        private String tenantId;
        /** 乐观锁版本。 */
        private Long version;
    }

    private JdbcEntityDaoFactory newFactory(EntityMetaRegistry registry) {
        return newFactory(registry, null);
    }

    private JdbcEntityDaoFactory newFactory(
        EntityMetaRegistry registry, com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder recorder
    ) {
        SqlSafetyGuard safetyGuard = new SqlSafetyGuard(
            new SqlIdentifierAllowlistValidator(registry),
            new SqlParameterLimiter()
        );
        return new JdbcEntityDaoFactory(
            registry,
            new JdbcGuardedSqlExecutor(jdbcTemplate, safetyGuard, new SqlExecutionLogger()),
            StandardJdbcDialect.GENERIC,
            JdbcEntityDaoFactory.DEFAULT_MAX_PARAMETERS,
            null,
            null,
            JdbcEntityDaoFactory.DEFAULT_MAX_BATCH_SIZE,
            new JdbcPaginationPolicy(),
            recorder
        );
    }
}
