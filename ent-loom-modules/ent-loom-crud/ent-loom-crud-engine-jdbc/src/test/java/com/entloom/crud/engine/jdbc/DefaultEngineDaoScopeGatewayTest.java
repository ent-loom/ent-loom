package com.entloom.crud.engine.jdbc;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.exception.EntityDaoWriteMissException;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.governance.scope.CrudDataScopeResolver;
import com.entloom.crud.engine.jdbc.test.support.EngineJdbcTestSupport;
import java.util.LinkedHashMap;
import java.util.Map;
import com.entloom.crud.engine.jdbc.test.entity.OrderTestEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** DAO 主键路径的 Gateway 范围传递测试。 */
class DefaultEngineDaoScopeGatewayTest extends EngineJdbcTestSupport {
    @Test
    void payload_cannot_widen_governed_scope() {
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            1007L,
            "ORD-GOVERNANCE",
            199L,
            "tenant-a",
            0
        );
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            1008L,
            "ORD-WRONG-TENANT",
            198L,
            "tenant-b",
            0
        );
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?,?,?,?,?)",
            1009L,
            "ORD-WRONG-SCHOOL",
            199L,
            "tenant-a",
            0
        );
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1007L);
        payload.put("orderNo", "ORD-SHOULD-NOT-UPDATE");

        Assertions.assertThrows(
            EntityDaoWriteMissException.class,
            () -> commandGateway.action(commandSpec(payload))
        );
        Assertions.assertEquals(
            "ORD-GOVERNANCE",
            jdbcTemplate.queryForObject("select order_no from t_order where id=?", String.class, 1007L)
        );

        assertScopeWriteRejected(1008L, "ORD-WRONG-TENANT", "u-governance-tenant");
        assertScopeWriteRejected(1009L, "ORD-WRONG-SCHOOL", "u-governance-school");

        Map<String, Object> ordinaryConditionPayload = new LinkedHashMap<String, Object>();
        ordinaryConditionPayload.put("id", 1007L);
        ordinaryConditionPayload.put("orderNo", "ORD-ORDINARY-CONDITION");
        CommandSpec<Object> ordinaryCondition = commandSpec(ordinaryConditionPayload).toBuilder()
            .targetFilters(java.util.Collections.singletonList(
                new com.entloom.crud.api.model.QueryFilter(
                    "orderNo",
                    com.entloom.crud.api.enums.FilterOperator.EQ,
                    "ORD-GOVERNANCE"
                )
            ))
            .idempotencyKey("u-governance-ordinary-condition")
            .build();
        Assertions.assertThrows(
            com.entloom.crud.core.exception.ValidationException.class,
            () -> commandGateway.action(ordinaryCondition)
        );
        Assertions.assertEquals(
            "ORD-GOVERNANCE",
            jdbcTemplate.queryForObject("select order_no from t_order where id=?", String.class, 1007L)
        );
    }

    private void assertScopeWriteRejected(Long id, String originalOrderNo, String idempotencyKey) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", id);
        payload.put("orderNo", "ORD-SHOULD-NOT-UPDATE-" + id);
        Assertions.assertThrows(
            EntityDaoWriteMissException.class,
            () -> commandGateway.action(commandSpec(payload).toBuilder().idempotencyKey(idempotencyKey).build())
        );
        Assertions.assertEquals(
            originalOrderNo,
            jdbcTemplate.queryForObject("select order_no from t_order where id=?", String.class, id)
        );
    }

    @Override
    protected CrudDataScopeResolver createDataScopeResolver() {
        return new CrudDataScopeResolver() {
            @Override
            public CrudDataScope resolveQueryScope(
                CrudResourceAction action,
                com.entloom.crud.api.model.SubjectContext subject,
                com.entloom.crud.core.capability.query.spec.QuerySpec<?> spec
            ) {
                return CrudDataScope.allowAll();
            }

            @Override
            public CrudDataScope resolveCommandScope(
                CrudResourceAction action,
                com.entloom.crud.api.model.SubjectContext subject,
                CommandSpec<?> spec
            ) {
                Map<String, Object> dimensions = new LinkedHashMap<String, Object>();
                dimensions.put("schoolId", 198L);
                dimensions.put("tenantId", "tenant-a");
                return CrudDataScope.scoped(dimensions);
            }
        };
    }

    private CommandSpec<Object> commandSpec(Map<String, Object> payload) {
        return CommandSpec.<Object>builder()
            .scene(null)
            .rootType(OrderTestEntity.class)
            .subject(testSubject())
            .resultType(CommandResult.class)
            .op(CommandOperation.UPDATE)
            .idempotencyKey("u-governance-scope")
            .payload(payload)
            .build();
    }
}
