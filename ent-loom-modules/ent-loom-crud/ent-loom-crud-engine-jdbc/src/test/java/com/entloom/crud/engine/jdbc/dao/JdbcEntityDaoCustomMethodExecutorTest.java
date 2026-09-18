package com.entloom.crud.engine.jdbc.dao;

import com.entloom.crud.annotations.EntCommand;
import com.entloom.crud.annotations.EntQuery;
import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.exception.QueryNotUniqueException;
import com.entloom.crud.engine.jdbc.test.entity.OrderTestEntity;
import com.entloom.crud.engine.jdbc.test.support.EngineJdbcTestSupport;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
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
        validate(CustomDao.class, "findByLowerOrderNo");

        List<OrderTestEntity> rows = invoke(scope, "findAvailable", 199L);
        assertTrue(rows.isEmpty());
        rows = invoke(scope, "findAvailable", 198L);
        assertEquals(1, rows.size());
        assertEquals(Long.valueOf(20001L), rows.get(0).getId());

        rows = invoke(scope, "findByLowerOrderNo", "ord-custom-1");
        assertEquals(1, rows.size());

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
        assertThrows(ValidationException.class, () -> validate(InvalidDao.class, "commentQuery"));
        assertThrows(ValidationException.class, () -> validate(InvalidDao.class, "multiTableQuery"));
        assertThrows(ValidationException.class, () -> validate(InvalidDao.class, "multiTableUpdate"));
        assertThrows(ValidationException.class, () -> validate(InvalidDao.class, "reservedFrameworkParameter"));
        assertThrows(ValidationException.class, () -> validate(InvalidDao.class, "unsupportedFunction"));
        assertThrows(ValidationException.class, () -> validate(InvalidDao.class, "deleteWithLimit"));
        assertThrows(ValidationException.class, () -> validate(InvalidDao.class, "updateScope"));
        assertThrows(ValidationException.class, () -> validate(InvalidDao.class, "updateLogicDelete"));
        assertThrows(ValidationException.class, () -> validate(InvalidDao.class, "incompleteUpdate"));
    }

    @Test
    void should_apply_scope_and_logic_delete_to_the_complete_or_expression() throws Exception {
        insertRow(20004L, "ORD-OR-OUTSIDE", 199L, "tenant-a", 0);
        insertRow(20005L, "ORD-OR-IN-SCOPE", 198L, "tenant-a", 0);
        EntityAccessScope scope = EntityAccessScope.of(
            com.entloom.crud.core.capability.dao.RowConstraint.eq("schoolId", 198L)
        );

        assertTrue(((List<?>) invoke(scope, "findByOr", 20004L, "ORD-OR-OUTSIDE")).isEmpty());
        assertEquals(0, ((Integer) invoke(scope, "updateByOr", 20004L, "ORD-OR-OUTSIDE", "ORD-NOT-UPDATED")));
        assertEquals(0, ((Long) invoke(scope, "deleteByOr", 20004L, "ORD-OR-OUTSIDE")));
        assertEquals("ORD-OR-OUTSIDE", jdbcTemplate.queryForObject(
            "select order_no from t_order where id = ?", String.class, 20004L
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
            "select is_deleted from t_order where id = ?", Integer.class, 20004L
        ).intValue());
    }

    @Test
    void should_limit_single_object_query_before_mapping_all_rows() throws Exception {
        insertRow(20006L, "ORD-MANY-1", 198L, "tenant-a", 0);
        insertRow(20007L, "ORD-MANY-2", 198L, "tenant-a", 0);

        assertThrows(QueryNotUniqueException.class, () -> invoke(
            EntityAccessScope.unrestricted(), "findOptionalBySchool", 198L
        ));
    }

    @Test
    void should_normalize_collection_temporal_and_enum_parameter_values() throws Exception {
        insertRow(20008L, "ORD-IN", 198L, "tenant-a", 0);
        assertEquals(1, ((List<?>) invoke(
            EntityAccessScope.unrestricted(), "findByIds", Arrays.asList("20008")
        )).size());
        assertThrows(ValidationException.class, () -> invoke(
            EntityAccessScope.unrestricted(), "findByIds", (Object) null
        ));

        EntityFieldMeta enumField = new EntityFieldMeta(
            "status", SampleStatus.class, "status", false, false, true, true
        );
        EntityFieldMeta dateField = new EntityFieldMeta(
            "orderDate", LocalDate.class, "order_date", false, false, true, true
        );
        EntityFieldMeta dateTimeField = new EntityFieldMeta(
            "updatedAt", LocalDateTime.class, "updated_at", false, false, true, true
        );
        assertEquals(SampleStatus.ACTIVE, JdbcEntityValueBinder.normalize(enumField, "ACTIVE"));
        assertEquals(java.sql.Date.valueOf("2026-09-18"),
            JdbcEntityValueBinder.normalize(dateField, "2026-09-18"));
        assertEquals(java.sql.Timestamp.valueOf("2026-09-18 10:20:30"),
            JdbcEntityValueBinder.normalize(dateTimeField, "2026-09-18 10:20:30"));
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

        @EntQuery("select * from t_order where lower(order_no) = lower(:orderNo)")
        List<OrderTestEntity> findByLowerOrderNo(String orderNo);

        @EntCommand("update t_order set order_no = :orderNo where id = :id")
        int updateOrderNo(Long id, String orderNo);

        @EntCommand("delete from t_order where id = :id")
        long deleteOne(Long id);

        @EntQuery("select * from t_order where id = :id or order_no = :orderNo")
        List<OrderTestEntity> findByOr(Long id, String orderNo);

        @EntCommand("update t_order set order_no = :newOrderNo where id = :id or order_no = :orderNo")
        int updateByOr(Long id, String orderNo, String newOrderNo);

        @EntCommand("delete from t_order where id = :id or order_no = :orderNo")
        long deleteByOr(Long id, String orderNo);

        @EntQuery("select * from t_order where school_id = :schoolId")
        Optional<OrderTestEntity> findOptionalBySchool(Long schoolId);

        @EntQuery("select * from t_order where id in (:ids)")
        List<OrderTestEntity> findByIds(List<Long> ids);
    }

    interface InvalidDao {
        @EntQuery("select a.* from t_order a join t_order_item b on b.order_id = a.id")
        List<OrderTestEntity> joinQuery();

        @EntCommand("select * from t_order")
        int readCommand();

        @EntQuery("select * from t_order where id = :__ent_bad")
        Optional<OrderTestEntity> reservedParameter(Long __ent_bad);

        @EntQuery("select * from t_order where id = :__ent_logic_delete")
        Optional<OrderTestEntity> reservedFrameworkParameter(Long __ent_logic_delete);

        @EntQuery("select count(*) from t_order")
        List<OrderTestEntity> aggregateQuery();

        @EntQuery("select * from t_order where id = :id -- governance must not be swallowed")
        Optional<OrderTestEntity> commentQuery(Long id);

        @EntQuery("select * from t_order, t_order_item where t_order.id = :id")
        List<OrderTestEntity> multiTableQuery(Long id);

        @EntCommand("update t_order, t_order_item set t_order.order_no = :orderNo where t_order.id = :id")
        int multiTableUpdate(Long id, String orderNo);

        @EntQuery("select * from t_order where dangerous(order_no) = 'x'")
        List<OrderTestEntity> unsupportedFunction();

        @EntCommand("delete from t_order where id = :id order by id limit 1")
        long deleteWithLimit(Long id);

        @EntCommand("update t_order set school_id = :schoolId where id = :id")
        int updateScope(Long id, Long schoolId);

        @EntCommand("update t_order set is_deleted = :deleted where id = :id")
        int updateLogicDelete(Long id, Integer deleted);

        @EntCommand("update t_order set order_no where id = :id")
        int incompleteUpdate(Long id);
    }

    public static class OrderSummary {
        public Long id;
        public String orderNo;
    }

    private enum SampleStatus {
        ACTIVE
    }
}
