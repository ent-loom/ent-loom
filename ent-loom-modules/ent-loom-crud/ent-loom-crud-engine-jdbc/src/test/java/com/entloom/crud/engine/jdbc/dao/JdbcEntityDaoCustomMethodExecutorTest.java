package com.entloom.crud.engine.jdbc.dao;

import com.entloom.crud.annotations.EntCommand;
import com.entloom.crud.annotations.EntQuery;
import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.engine.jdbc.test.entity.OrderTestEntity;
import com.entloom.crud.engine.jdbc.test.support.EngineJdbcTestSupport;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 DAO 自定义 SQL 的查询、命令和范围治理闭环。 */
class JdbcEntityDaoCustomMethodExecutorTest extends EngineJdbcTestSupport {
    @Test
    void should_execute_query_dto_and_command_inside_scope() throws Exception {
        insertRow(20001L, "ORD-CUSTOM-1", 198L, "tenant-a", 0);
        insertRow(20002L, "ORD-CUSTOM-2", 199L, "tenant-a", 0);
        insertRow(20003L, "ORD-CUSTOM-DELETED", 198L, "tenant-a", 1);

        EntityAccessScope scope = EntityAccessScope.of(
            com.entloom.crud.core.capability.dao.RowConstraint.eq("schoolId", 198L)
        );
        validate(CustomDao.class, "findAvailable");
        validate(CustomDao.class, "findOne");
        validate(CustomDao.class, "summary");
        validate(CustomDao.class, "updateOrderNo");
        validate(CustomDao.class, "deleteOne");

        List<OrderTestEntity> rows = invoke(scope, "findAvailable", 199L);
        assertTrue(rows.isEmpty());
        rows = invoke(scope, "findAvailable", 198L);
        assertEquals(1, rows.size());
        assertEquals(Long.valueOf(20001L), rows.get(0).getId());

        Optional<OrderTestEntity> found = invoke(scope, "findOne", 20001L);
        assertTrue(found.isPresent());
        assertEquals("ORD-CUSTOM-1", found.get().getOrderNo());
        assertFalse(((Optional<?>) invoke(scope, "findOne", 20003L)).isPresent());

        OrderSummary summary = invoke(scope, "summary", 20001L);
        assertEquals(Long.valueOf(20001L), summary.id);
        assertEquals("ORD-CUSTOM-1", summary.orderNo);

        assertEquals(1, ((Integer) invoke(scope, "updateOrderNo", 20001L, "ORD-CUSTOM-UPDATED")).intValue());
        assertEquals("ORD-CUSTOM-UPDATED", jdbcTemplate.queryForObject(
            "select order_no from t_order where id = ?", String.class, 20001L
        ));
        assertEquals(0, ((Long) invoke(scope, "deleteOne", 20002L)).longValue());
        assertEquals(1, ((Long) invoke(scope, "deleteOne", 20001L)).longValue());
        assertEquals(1, jdbcTemplate.queryForObject(
            "select count(1) from t_order where id = ?", Integer.class, 20001L
        ).intValue());
        assertEquals(1, jdbcTemplate.queryForObject(
            "select is_deleted from t_order where id = ?", Integer.class, 20001L
        ).intValue());
    }

    @Test
    void should_reject_unsafe_sql_and_mismatched_contract_at_startup() throws Exception {
        assertThrows(ValidationException.class, () -> validate(InvalidDao.class, "joinQuery"));
        assertThrows(ValidationException.class, () -> validate(InvalidDao.class, "readCommand"));
        assertThrows(ValidationException.class, () -> validate(InvalidDao.class, "reservedParameter"));
        assertThrows(ValidationException.class, () -> validate(InvalidDao.class, "aggregateQuery"));
    }

    private void insertRow(Long id, String orderNo, Long schoolId, String tenantId, Integer deleted) {
        jdbcTemplate.update(
            "insert into t_order(id, order_no, school_id, tenant_id, is_deleted) values (?, ?, ?, ?, ?)",
            id, orderNo, schoolId, tenantId, deleted
        );
    }

    private void validate(Class<?> daoType, String methodName) throws Exception {
        java.lang.reflect.Method method = resolveMethod(daoType, methodName, -1);
        entityDaoFactory.validateCustomMethod(
            com.entloom.crud.core.capability.dao.EntityType.of(OrderTestEntity.class, Long.class),
            method
        );
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(EntityAccessScope scope, String methodName, Object... args) throws Exception {
        java.lang.reflect.Method method = resolveMethod(CustomDao.class, methodName, args.length);
        return (T) entityDaoFactory.invokeCustom(
            com.entloom.crud.core.capability.dao.EntityType.of(OrderTestEntity.class, Long.class),
            scope,
            method,
            args
        );
    }

    private java.lang.reflect.Method resolveMethod(Class<?> type, String methodName, int parameterCount) {
        for (java.lang.reflect.Method method : type.getMethods()) {
            if (method.getName().equals(methodName)
                && (parameterCount < 0 || method.getParameterCount() == parameterCount)) {
                return method;
            }
        }
        throw new IllegalArgumentException(methodName);
    }

    interface CustomDao {
        @EntQuery("select * from t_order where school_id = :schoolId order by id desc")
        List<OrderTestEntity> findAvailable(Long schoolId);

        @EntQuery("select * from t_order where id = :id")
        Optional<OrderTestEntity> findOne(Long id);

        @EntQuery("select id, order_no from t_order where id = :id")
        OrderSummary summary(Long id);

        @EntCommand("update t_order set order_no = :orderNo where id = :id")
        int updateOrderNo(Long id, String orderNo);

        @EntCommand("delete from t_order where id = :id")
        long deleteOne(Long id);
    }

    interface InvalidDao {
        @EntQuery("select a.* from t_order a join t_order_item b on b.order_id = a.id")
        List<OrderTestEntity> joinQuery();

        @EntCommand("select * from t_order")
        int readCommand();

        @EntQuery("select * from t_order where id = :__ent_bad")
        Optional<OrderTestEntity> reservedParameter(Long __ent_bad);

        @EntQuery("select count(*) from t_order")
        List<OrderTestEntity> aggregateQuery();
    }

    public static class OrderSummary {
        public Long id;
        public String orderNo;
    }
}
