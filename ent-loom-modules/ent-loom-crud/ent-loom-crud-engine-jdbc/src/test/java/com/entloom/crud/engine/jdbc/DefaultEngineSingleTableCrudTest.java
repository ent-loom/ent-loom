package com.entloom.crud.engine.jdbc;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.api.enums.SortDirection;
import com.entloom.crud.core.exception.QueryNotUniqueException;
import com.entloom.crud.core.exception.RouteNotFoundException;
import com.entloom.crud.core.exception.EntityDaoWriteMissException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.capability.command.scene.AbstractPatchUpdateSceneHandler;
import com.entloom.crud.core.capability.command.spec.BatchCommand;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditReasonCode;
import com.entloom.crud.engine.jdbc.test.entity.OrderTestEntity;
import com.entloom.crud.engine.jdbc.test.support.EngineJdbcTestSupport;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultEngineSingleTableCrudTest extends EngineJdbcTestSupport {
    @Test
    void order_root_metadata_should_mark_scope_fields_and_exclude_items_relation() {
        com.entloom.crud.core.runtime.meta.EntityMeta meta = metaRegistry.getEntityMeta(OrderTestEntity.class);

        Assertions.assertTrue(meta.resolveFieldMeta("schoolId").isScopeField());
        Assertions.assertTrue(meta.resolveFieldMeta("tenantId").isScopeField());
        Assertions.assertFalse(meta.resolveFieldMeta("schoolId").isWritable());
        Assertions.assertFalse(meta.resolveFieldMeta("tenantId").isWritable());
        Assertions.assertNull(meta.resolveFieldMeta("items"));
        Assertions.assertEquals("isDeleted", meta.getLogicDeleteField());
    }

    @Test
    void order_scope_fields_should_not_be_updateable_through_gateway() {
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            1010L,
            "ORD-SCOPE",
            198L,
            "tenant-a",
            0
        );
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1010L);
        payload.put("schoolId", 199L);

        ValidationException exception = Assertions.assertThrows(
            ValidationException.class,
            () -> commandGateway.action(commandSpec(CommandOperation.UPDATE, "u-scope", payload))
        );

        Assertions.assertTrue(exception.getMessage().contains("schoolId"));
        Assertions.assertEquals(198L, jdbcTemplate.queryForObject(
            "select school_id from t_order where id=?", Long.class, 1010L
        ));
    }

    @Test
    void create_update_and_delete_order_should_work() {
        Map<String, Object> createPayload = new LinkedHashMap<String, Object>();
        createPayload.put("id", 1001L);
        createPayload.put("orderNo", "ORD-1001");
        createPayload.put("isDeleted", 0);

        CommandResult createResult = commandGateway.action(commandSpec(CommandOperation.CREATE, "c-1", createPayload));
        Assertions.assertTrue(createResult.isSuccess());
        Assertions.assertEquals(1, countById(1001L));

        Map<String, Object> updatePayload = new LinkedHashMap<String, Object>();
        updatePayload.put("id", 1001L);
        updatePayload.put("orderNo", "ORD-1001-UPDATED");
        CommandResult updateResult = commandGateway.action(commandSpec(CommandOperation.UPDATE, "u-1", updatePayload));
        Assertions.assertTrue(updateResult.isSuccess());
        Assertions.assertEquals("ORD-1001-UPDATED", orderNoById(1001L));

        Map<String, Object> deletePayload = new LinkedHashMap<String, Object>();
        deletePayload.put("id", 1001L);
        CommandResult deleteResult = commandGateway.action(
            commandSpec(CommandOperation.DELETE, "d-1", deletePayload)
        );
        Assertions.assertTrue(deleteResult.isSuccess());
        Assertions.assertEquals(1, deletedFlagById(1001L));
    }

    @Test
    void dao_primary_key_path_remains_under_gateway_idempotency() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1008L);
        payload.put("orderNo", "ORD-IDEMPOTENT-DAO");
        payload.put("isDeleted", 0);
        CommandSpec<Object> spec = commandSpec(CommandOperation.CREATE, "c-idempotent-dao", payload);

        CommandResult first = commandGateway.action(spec);
        CommandResult second = commandGateway.action(spec);

        Assertions.assertTrue(first.isSuccess());
        Assertions.assertTrue(second.isSuccess());
        Assertions.assertEquals(1, countById(1008L));
    }

    @Test
    void gateway_should_record_complete_dao_success_and_execution_failure_audit() {
        int before = auditRecorder.getEvents().size();
        Map<String, Object> createPayload = new LinkedHashMap<String, Object>();
        createPayload.put("id", 1011L);
        createPayload.put("orderNo", "ORD-AUDIT");
        createPayload.put("isDeleted", 0);
        Map<String, Object> attributes = new LinkedHashMap<String, Object>();
        attributes.put("requestId", "request-audit-1");
        attributes.put("traceId", "trace-audit-1");

        CommandSpec<Object> create = commandSpec(CommandOperation.CREATE, "c-audit-1", createPayload)
            .toBuilder()
            .attributes(attributes)
            .build();
        CommandResult result = commandGateway.action(create);
        Assertions.assertTrue(result.isSuccess());

        CrudGovernanceAuditEvent success = auditRecorder.getEvents().get(before);
        Assertions.assertEquals(CrudGovernanceAuditOutcome.SUCCESS, success.getOutcome());
        Assertions.assertEquals(CrudGovernanceAuditReasonCode.NONE, success.getReason());
        Assertions.assertNull(success.getStage());
        Assertions.assertTrue(success.isAllowed());
        Assertions.assertNotNull(success.getSubject());
        Assertions.assertEquals("tenant-a", success.getSubject().getTenantId());
        Assertions.assertNotNull(success.getAction());
        Assertions.assertNotNull(success.getRouteKey());
        Assertions.assertEquals("request-audit-1", success.getRequestId());
        Assertions.assertEquals("trace-audit-1", success.getTraceId());
        Assertions.assertTrue(success.getGovernanceScope().isExplicitAll());

        Map<String, Object> missingPayload = new LinkedHashMap<String, Object>();
        missingPayload.put("id", 10999L);
        missingPayload.put("orderNo", "ORD-MISSING");
        Assertions.assertThrows(
            EntityDaoWriteMissException.class,
            () -> commandGateway.action(commandSpec(CommandOperation.UPDATE, "u-audit-miss", missingPayload))
        );

        CrudGovernanceAuditEvent failure = auditRecorder.getEvents().get(before + 1);
        Assertions.assertEquals(CrudGovernanceAuditOutcome.EXECUTION_FAILED, failure.getOutcome());
        Assertions.assertEquals(CrudErrorStage.EXECUTE, failure.getStage());
        Assertions.assertTrue(failure.isAllowed());
        Assertions.assertNotEquals(CrudGovernanceAuditReasonCode.NONE, failure.getReason());
        Assertions.assertNotNull(failure.getRouteKey());
    }

    @Test
    void gateway_should_join_caller_transaction_and_rollback_dao_write() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1012L);
        payload.put("orderNo", "ORD-ROLLBACK");
        payload.put("isDeleted", 0);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        Assertions.assertThrows(
            IllegalStateException.class,
            () -> transaction.execute(status -> {
                commandGateway.action(commandSpec(CommandOperation.CREATE, "c-rollback", payload));
                throw new IllegalStateException("测试外层事务回滚");
            })
        );

        Assertions.assertEquals(0, countById(1012L));
    }

    @Test
    void non_empty_update_scene_should_delegate_to_dao_primary_key_path() {
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            1009L,
            "ORD-SCENE",
            198L,
            "tenant-a",
            0
        );
        ((com.entloom.crud.core.runtime.router.DefaultCommandRouter) commandRouter).registerSceneHandler(
            new AbstractPatchUpdateSceneHandler<OrderTestEntity, Object>(
                metaRegistry,
                OrderTestEntity.class,
                "dao.patch"
            ) {
                @Override
                protected Object handlePatch(
                    CommandSpec<com.entloom.crud.core.capability.command.patch.UpdatePatch<OrderTestEntity>> spec,
                    com.entloom.crud.core.runtime.scene.SceneDelegate<CommandSpec<Object>, Object> delegate
                ) {
                    return invokeDelegateUpdate(spec, delegate);
                }
            }
        );

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1009L);
        payload.put("orderNo", "ORD-SCENE-UPDATED");
        CommandResult result = commandGateway.action(
            commandSpec(CommandOperation.UPDATE, "u-scene-dao", payload).toBuilder()
                .scene("dao.patch")
                .build()
        );

        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals("ORD-SCENE-UPDATED", orderNoById(1009L));
    }

    @Test
    void create_with_crud_record_payload_should_work() {
        Map<String, Object> createPayload = new LinkedHashMap<String, Object>();
        createPayload.put("id", 1003L);
        createPayload.put("orderNo", "ORD-1003");
        createPayload.put("isDeleted", 0);
        CrudRecord payload = CrudRecord.copyOf(createPayload);

        CommandResult createResult = commandGateway.action(commandSpec(CommandOperation.CREATE, "c-3", payload));

        Assertions.assertTrue(createResult.isSuccess());
        Assertions.assertEquals(1, countById(1003L));
        Assertions.assertEquals("ORD-1003", orderNoById(1003L));
    }

    @Test
    void create_with_illegal_field_in_crud_record_should_be_rejected() {
        Map<String, Object> createPayload = new LinkedHashMap<String, Object>();
        createPayload.put("id", 1004L);
        createPayload.put("orderNo", "ORD-1004");
        createPayload.put("isDeleted", 0);
        createPayload.put("illegalField", "x");
        CrudRecord payload = CrudRecord.copyOf(createPayload);

        ValidationException ex = Assertions.assertThrows(
            ValidationException.class,
            () -> commandGateway.action(commandSpec(CommandOperation.CREATE, "c-4", payload))
        );

        Assertions.assertTrue(ex.getMessage().contains("未知载荷字段"));
    }

    @Test
    void create_with_pojo_payload_should_be_rejected() {
        OrderPayload payload = new OrderPayload();
        payload.id = 1005L;
        payload.orderNo = "ORD-1005";
        payload.isDeleted = 0;

        ValidationException ex = Assertions.assertThrows(
            ValidationException.class,
            () -> commandGateway.action(commandSpec(CommandOperation.CREATE, "c-5", payload))
        );

        Assertions.assertTrue(ex.getMessage().contains("命令载荷必须是 Map、CrudRecord 或 WriteCommand"));
    }

    @Test
    void update_soft_deleted_order_should_be_treated_as_not_found() {
        jdbcTemplate.update("insert into t_order(id, order_no, is_deleted) values (?,?,?)", 3001L, "ORD-SOFT-DELETED", 1);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 3001L);
        payload.put("orderNo", "ORD-SHOULD-NOT-UPDATE");

        RouteNotFoundException exception = Assertions.assertThrows(
            RouteNotFoundException.class,
            () -> commandGateway.action(commandSpec(CommandOperation.UPDATE, "u-soft-delete", payload))
        );

        Assertions.assertTrue(exception.getMessage().contains("目标不存在"));
        Assertions.assertEquals("ORD-SOFT-DELETED", orderNoById(3001L));
    }

    @Test
    void delete_soft_deleted_order_should_be_treated_as_not_found() {
        jdbcTemplate.update("insert into t_order(id, order_no, is_deleted) values (?,?,?)", 3002L, "ORD-ALREADY-DELETED", 1);

        RouteNotFoundException exception = Assertions.assertThrows(
            RouteNotFoundException.class,
            () ->
                commandGateway.action(commandSpec(CommandOperation.DELETE, "d-soft-delete", Collections.singletonMap("id", 3002L)))
        );

        Assertions.assertTrue(exception.getMessage().contains("目标不存在"));
        Assertions.assertEquals(1, deletedFlagById(3002L));
    }

    @Test
    void read_orders_should_page_seed_data() {
        jdbcTemplate.update("insert into t_order(id, order_no, is_deleted) values (?,?,?)", 2001L, "ORD-A", 0);
        jdbcTemplate.update("insert into t_order(id, order_no, is_deleted) values (?,?,?)", 2002L, "ORD-B", 0);

        QuerySpec<OrderTestEntity> spec = QuerySpec.<OrderTestEntity>builder()
            .scene(null)
            .rootType(OrderTestEntity.class)
            .subject(testSubject())
            .resultType(OrderTestEntity.class)
            .op(QueryOperation.PAGE)
            .page(new PageRequest(1, 10))
            .filters(Collections.singletonList(new QueryFilter("isDeleted", FilterOperator.EQ, 0)))
            .build();

        PageResult<OrderTestEntity> page = queryGateway.page(spec);
        Assertions.assertEquals(2, page.getItems().size());
        Assertions.assertEquals(2L, page.getTotal());
        Assertions.assertTrue(page.getItems().get(0).getId() < page.getItems().get(1).getId());
    }

    @Test
    void find_one_should_return_null_when_not_found() {
        QuerySpec<OrderTestEntity> spec = QuerySpec.<OrderTestEntity>builder()
            .scene(null)
            .rootType(OrderTestEntity.class)
            .subject(testSubject())
            .resultType(OrderTestEntity.class)
            .op(QueryOperation.FIND_ONE)
            .filters(Collections.singletonList(new QueryFilter("id", FilterOperator.EQ, 99999L)))
            .build();

        OrderTestEntity one = queryGateway.findOne(spec);
        Assertions.assertNull(one);
    }

    @Test
    void find_one_should_fail_when_hit_multi_rows() {
        jdbcTemplate.update("insert into t_order(id, order_no, is_deleted) values (?,?,?)", 2101L, "ORD-M1", 0);
        jdbcTemplate.update("insert into t_order(id, order_no, is_deleted) values (?,?,?)", 2102L, "ORD-M2", 0);

        QuerySpec<OrderTestEntity> spec = QuerySpec.<OrderTestEntity>builder()
            .scene(null)
            .rootType(OrderTestEntity.class)
            .subject(testSubject())
            .resultType(OrderTestEntity.class)
            .op(QueryOperation.FIND_ONE)
            .filters(Collections.singletonList(new QueryFilter("isDeleted", FilterOperator.EQ, 0)))
            .build();

        QueryNotUniqueException ex = Assertions.assertThrows(QueryNotUniqueException.class, () -> queryGateway.findOne(spec));
        Assertions.assertTrue(ex.getMessage().contains("命中多条"));
    }

    @Test
    void detail_should_fail_when_hit_multi_rows() {
        jdbcTemplate.update("insert into t_order(id, order_no, is_deleted) values (?,?,?)", 2201L, "ORD-D1", 0);
        jdbcTemplate.update("insert into t_order(id, order_no, is_deleted) values (?,?,?)", 2202L, "ORD-D2", 0);

        QuerySpec<OrderTestEntity> spec = QuerySpec.<OrderTestEntity>builder()
            .scene(null)
            .rootType(OrderTestEntity.class)
            .subject(testSubject())
            .resultType(OrderTestEntity.class)
            .op(QueryOperation.DETAIL)
            .filters(Collections.singletonList(new QueryFilter("isDeleted", FilterOperator.EQ, 0)))
            .build();

        QueryNotUniqueException ex = Assertions.assertThrows(QueryNotUniqueException.class, () -> queryGateway.detail(spec));
        Assertions.assertTrue(ex.getMessage().contains("命中多条"));
    }

    @Test
    void default_query_engine_should_apply_select_fields_projection() {
        jdbcTemplate.update("insert into t_order(id, order_no, is_deleted) values (?,?,?)", 2301L, "ORD-PROJECTION", 0);

        QuerySpec<OrderTestEntity> spec = QuerySpec.<OrderTestEntity>builder()
            .scene(null)
            .rootType(OrderTestEntity.class)
            .subject(testSubject())
            .resultType(OrderTestEntity.class)
            .op(QueryOperation.LIST)
            .limit(10)
            .filters(Collections.singletonList(new QueryFilter("id", FilterOperator.EQ, 2301L)))
            .selectFields(Arrays.asList("id", "orderNo"))
            .build();

        List<OrderTestEntity> rows = queryGateway.list(spec);

        Assertions.assertEquals(1, rows.size());
        Assertions.assertEquals(Long.valueOf(2301L), rows.get(0).getId());
        Assertions.assertEquals("ORD-PROJECTION", rows.get(0).getOrderNo());
        Assertions.assertNull(rows.get(0).getIsDeleted());
    }

    @Test
    void default_query_engine_should_fail_early_for_undeclared_relation_filter() {
        QuerySpec<OrderTestEntity> spec = QuerySpec.<OrderTestEntity>builder()
            .scene(null)
            .rootType(OrderTestEntity.class)
            .subject(testSubject())
            .resultType(OrderTestEntity.class)
            .op(QueryOperation.LIST)
            .limit(10)
            .filters(Collections.singletonList(new QueryFilter("items.skuCode", FilterOperator.EQ, "SKU-1")))
            .build();

        ValidationException ex = Assertions.assertThrows(ValidationException.class, () -> queryGateway.list(spec));
        Assertions.assertTrue(ex.getMessage().contains("关联过滤"));
    }

    @Test
    void default_query_engine_should_fail_early_for_undeclared_relation_sort() {
        QuerySpec<OrderTestEntity> spec = QuerySpec.<OrderTestEntity>builder()
            .scene(null)
            .rootType(OrderTestEntity.class)
            .subject(testSubject())
            .resultType(OrderTestEntity.class)
            .op(QueryOperation.LIST)
            .limit(10)
            .sorts(Collections.singletonList(new QuerySort("items.skuCode", SortDirection.ASC)))
            .build();

        ValidationException ex = Assertions.assertThrows(ValidationException.class, () -> queryGateway.list(spec));
        Assertions.assertTrue(ex.getMessage().contains("关联排序"));
    }

    @Test
    void default_command_engine_should_require_payload_id_or_target_filters_for_update_and_delete() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("orderNo", "ORD-MISSING-TARGET");

        ValidationException updateError = Assertions.assertThrows(
            ValidationException.class,
            () -> commandGateway.action(commandSpec(CommandOperation.UPDATE, "u-missing-target", payload))
        );
        Assertions.assertTrue(updateError.getMessage().contains("payload.id"));

        ValidationException deleteError = Assertions.assertThrows(
            ValidationException.class,
            () -> commandGateway.action(commandSpec(CommandOperation.DELETE, "d-missing-target", Collections.<String, Object>emptyMap()))
        );
        Assertions.assertTrue(deleteError.getMessage().contains("payload.id"));
    }

    @Test
    void save_or_update_batch_should_create_missing_and_update_existing_by_payload_id() {
        jdbcTemplate.update("insert into t_order(id, order_no, is_deleted) values (?,?,?)", 2501L, "ORD-BEFORE", 0);

        Map<String, Object> updatePayload = new LinkedHashMap<String, Object>();
        updatePayload.put("orderNo", "ORD-AFTER");
        Map<String, Object> createPayload = new LinkedHashMap<String, Object>();
        createPayload.put("orderNo", "ORD-NEW");
        createPayload.put("isDeleted", 0);

        BatchCommand<Object> batchCommand = BatchCommand.of(Arrays.<WriteCommand<Object>>asList(
            new WriteCommand<Object>(CommandOperation.SAVE_OR_UPDATE, 2501L, updatePayload),
            new WriteCommand<Object>(CommandOperation.SAVE_OR_UPDATE, 2502L, createPayload)
        ));

        CommandResult result = commandGateway.action(commandSpec(CommandOperation.SAVE_OR_UPDATE_BATCH, "save-or-update-batch-1", batchCommand));

        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals("ORD-AFTER", orderNoById(2501L));
        Assertions.assertEquals("ORD-NEW", orderNoById(2502L));
    }

    @Test
    void save_or_update_batch_should_honor_explicit_create_or_update_child_operation() {
        jdbcTemplate.update("insert into t_order(id, order_no, is_deleted) values (?,?,?)", 2503L, "ORD-BEFORE", 0);

        Map<String, Object> createPayload = new LinkedHashMap<String, Object>();
        createPayload.put("orderNo", "ORD-CREATE-EXISTING");
        RuntimeException createError = Assertions.assertThrows(
            RuntimeException.class,
            () -> commandGateway.action(commandSpec(
                CommandOperation.SAVE_OR_UPDATE_BATCH,
                "save-or-update-batch-explicit-create",
                BatchCommand.of(Collections.<WriteCommand<Object>>singletonList(
                    new WriteCommand<Object>(CommandOperation.CREATE, 2503L, createPayload)
                )
            )))
        );
        Assertions.assertNotNull(createError);
        Assertions.assertEquals("ORD-BEFORE", orderNoById(2503L));

        Map<String, Object> updatePayload = new LinkedHashMap<String, Object>();
        updatePayload.put("orderNo", "ORD-UPDATE-MISSING");
        RouteNotFoundException updateError = Assertions.assertThrows(
            RouteNotFoundException.class,
            () -> commandGateway.action(commandSpec(
                CommandOperation.SAVE_OR_UPDATE_BATCH,
                "save-or-update-batch-explicit-update",
                BatchCommand.of(Collections.<WriteCommand<Object>>singletonList(
                    new WriteCommand<Object>(CommandOperation.UPDATE, 2504L, updatePayload)
                )
            )))
        );
        Assertions.assertTrue(updateError.getMessage().contains("目标不存在"));
        Assertions.assertEquals(0, countById(2504L));
    }

    private CommandSpec<Object> commandSpec(CommandOperation operation, String idempotencyKey, Object payload) {
        return CommandSpec.<Object>builder()
            .scene(null)
            .rootType(OrderTestEntity.class)
            .subject(testSubject())
            .resultType(CommandResult.class)
            .op(operation)
            .idempotencyKey(idempotencyKey)
            .payload(payload)
            .build();
    }

    private CommandSpec<Object> targetCommandSpec(CommandOperation operation, String idempotencyKey, Object payload, Long id) {
        return commandSpec(operation, idempotencyKey, payload)
            .toBuilder()
            .targetFilters(Collections.singletonList(new QueryFilter("id", FilterOperator.EQ, id)))
            .build();
    }

    private int countById(Long id) {
        Integer count = jdbcTemplate.queryForObject("select count(1) from t_order where id=?", Integer.class, id);
        return count == null ? 0 : count.intValue();
    }

    private String orderNoById(Long id) {
        return jdbcTemplate.queryForObject("select order_no from t_order where id=?", String.class, id);
    }

    private int deletedFlagById(Long id) {
        Integer deleted = jdbcTemplate.queryForObject("select is_deleted from t_order where id=?", Integer.class, id);
        return deleted == null ? 0 : deleted.intValue();
    }

    private static final class OrderPayload {
        private Long id;
        private String orderNo;
        private Integer isDeleted;
    }
}
