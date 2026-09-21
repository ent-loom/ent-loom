package com.entloom.crud.engine.jdbc.dao;

import com.entloom.crud.annotations.EntCommand;
import com.entloom.crud.annotations.EntQuery;
import com.entloom.crud.api.enums.CountMode;
import com.entloom.crud.api.enums.SortDirection;
import com.entloom.crud.api.model.PageQuery;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.api.model.QuerySort;
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
        assertThrows(ValidationException.class, () -> validate(InvalidDao.class, "unknownProperty"));
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
        assertEquals("ACTIVE", JdbcEntityValueBinder.normalize(enumField, "ACTIVE"));
        assertEquals("ACTIVE", JdbcEntityValueBinder.normalize(enumField, SampleStatus.ACTIVE));
        assertEquals(java.sql.Date.valueOf("2026-09-18"),
            JdbcEntityValueBinder.normalize(dateField, "2026-09-18"));
        assertEquals(java.sql.Timestamp.valueOf("2026-09-18 10:20:30"),
            JdbcEntityValueBinder.normalize(dateTimeField, "2026-09-18 10:20:30"));
    }

    @Test
    void should_bind_java_bean_properties_and_nested_paths() throws Exception {
        insertRow(20012L, "ORD-OBJECT", 198L, "tenant-a", 0);
        OrderFilter filter = new OrderFilter(new OrderFilterCriteria(198L), "ORD-OBJECT");
        validate(CustomDao.class, "findByFilter");
        validate(CustomDao.class, "updateByCommand");

        List<OrderTestEntity> rows = invoke(EntityAccessScope.unrestricted(), "findByFilter", filter);
        assertEquals(1, rows.size());
        assertEquals(Long.valueOf(20012L), rows.get(0).getId());
        assertTrue(((List<?>) invoke(
            EntityAccessScope.unrestricted(), "findByFilter",
            new OrderFilter(new OrderFilterCriteria(198L), "ORD-OBJECT-MISSING")
        )).isEmpty());

        assertEquals(1, ((Integer) invoke(
            EntityAccessScope.of(com.entloom.crud.core.capability.dao.RowConstraint.eq("schoolId", 198L)),
            "updateByCommand", new OrderCommand(20012L, "ORD-OBJECT-UPDATED")
        )).intValue());
        assertEquals("ORD-OBJECT-UPDATED", jdbcTemplate.queryForObject(
            "select order_no from t_order where id = ?", String.class, 20012L
        ));

        assertThrows(ValidationException.class, () -> invoke(
            EntityAccessScope.unrestricted(), "findByFilter", new OrderFilter(null, "ORD-OBJECT")
        ));
        assertThrows(ValidationException.class, () -> invoke(
            EntityAccessScope.unrestricted(), "findByFilter", (Object) null
        ));
    }

    @Test
    void should_page_custom_query_with_stable_sort_and_optional_count() throws Exception {
        insertRow(20009L, "ORD-PAGE-1", 198L, "tenant-a", 0);
        insertRow(20010L, "ORD-PAGE-2", 198L, "tenant-a", 0);
        insertRow(20011L, "ORD-PAGE-3", 198L, "tenant-a", 0);
        validate(CustomDao.class, "findPage");
        validate(CustomDao.class, "findSummaryPage");

        PageResult<OrderTestEntity> first = invoke(
            EntityAccessScope.of(com.entloom.crud.core.capability.dao.RowConstraint.eq("schoolId", 198L)),
            "findPage", 198L, new PageQuery(1, 2, java.util.Collections.singletonList(
                new QuerySort("id", SortDirection.DESC)
            ), CountMode.NONE)
        );
        assertEquals(2, first.getItems().size());
        assertEquals(Long.valueOf(20011L), first.getItems().get(0).getId());
        assertEquals(Long.valueOf(20010L), first.getItems().get(1).getId());
        assertTrue(first.getHasNext());
        assertEquals(null, first.getTotal());

        PageResult<OrderSummary> summary = invoke(
            EntityAccessScope.unrestricted(), "findSummaryPage", 198L, new PageQuery(1, 1)
        );
        assertEquals(1, summary.getItems().size());
        assertEquals(Long.valueOf(20009L), summary.getItems().get(0).id);

        PageResult<OrderTestEntity> exact = invoke(
            EntityAccessScope.of(com.entloom.crud.core.capability.dao.RowConstraint.eq("schoolId", 198L)),
            "findPage", 198L, new PageQuery(2, 2, java.util.Collections.singletonList(
                new QuerySort("id", SortDirection.DESC)
            ), CountMode.ALWAYS)
        );
        assertEquals(3L, exact.getTotal().longValue());
        assertEquals(1, exact.getItems().size());
        assertFalse(exact.getHasNext());

        validate(CustomDao.class, "findPageWithOrderParameter");
        PageResult<OrderTestEntity> preferred = invoke(
            EntityAccessScope.unrestricted(),
            "findPageWithOrderParameter",
            198L,
            "ORD-PAGE-2",
            new PageQuery(1, 1)
        );
        assertEquals(Long.valueOf(20010L), preferred.getItems().get(0).getId());
        assertTrue(preferred.getHasNext());
    }

    @Test
    void should_reject_page_query_with_unsafe_sort_or_distinct() throws Exception {
        assertThrows(ValidationException.class, () -> validate(InvalidDao.class, "distinctQuery"));
        assertThrows(ValidationException.class, () -> validate(InvalidDao.class, "rawPage"));
        assertThrows(ValidationException.class, () -> validate(InvalidDao.class, "pageParameterUsed"));
        assertThrows(ValidationException.class, () -> invoke(
            EntityAccessScope.unrestricted(), "findPage", 198L, new PageQuery(1, 1,
                java.util.Collections.singletonList(new QuerySort("unknown", SortDirection.ASC)), CountMode.NONE)
        ));
        assertThrows(ValidationException.class, () -> invoke(
            EntityAccessScope.unrestricted(), "findPage", 198L, new PageQuery(1, 201)
        ));
        assertThrows(ValidationException.class, () -> invoke(
            EntityAccessScope.unrestricted(), "findPage", 198L, new PageQuery(5002, 200)
        ));
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

        @EntQuery("select * from t_order where school_id = :schoolId order by id desc")
        PageResult<OrderTestEntity> findPage(Long schoolId, PageQuery pageQuery);

        @EntQuery("select * from t_order where school_id = :schoolId "
            + "order by case when order_no = :preferredOrderNo then 0 else 1 end, id desc")
        PageResult<OrderTestEntity> findPageWithOrderParameter(
            Long schoolId, String preferredOrderNo, PageQuery pageQuery
        );

        @EntQuery("select id, order_no from t_order where school_id = :schoolId")
        PageResult<OrderSummary> findSummaryPage(Long schoolId, PageQuery pageQuery);

        @EntQuery("select * from t_order where school_id = :filter.criteria.schoolId "
            + "and (order_no = :filter.orderNo or order_no = :filter.orderNo)")
        List<OrderTestEntity> findByFilter(OrderFilter filter);

        @EntCommand("update t_order set order_no = :command.orderNo where id = :command.id")
        int updateByCommand(OrderCommand command);
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

        @EntQuery("select distinct school_id from t_order")
        PageResult<OrderTestEntity> distinctQuery(PageQuery pageQuery);

        @EntQuery("select * from t_order")
        PageResult rawPage(PageQuery pageQuery);

        @EntQuery("select * from t_order where order_no = :pageQuery")
        PageResult<OrderTestEntity> pageParameterUsed(PageQuery pageQuery);

        @EntCommand("delete from t_order where id = :id order by id limit 1")
        long deleteWithLimit(Long id);

        @EntCommand("update t_order set school_id = :schoolId where id = :id")
        int updateScope(Long id, Long schoolId);

        @EntCommand("update t_order set is_deleted = :deleted where id = :id")
        int updateLogicDelete(Long id, Integer deleted);

        @EntCommand("update t_order set order_no where id = :id")
        int incompleteUpdate(Long id);

        @EntQuery("select * from t_order where id = :filter.unknown")
        List<OrderTestEntity> unknownProperty(OrderFilter filter);
    }

    public static class OrderFilter {
        private OrderFilterCriteria criteria;
        private String orderNo;

        public OrderFilter(OrderFilterCriteria criteria, String orderNo) {
            this.criteria = criteria;
            this.orderNo = orderNo;
        }

        public OrderFilterCriteria getCriteria() {
            return criteria;
        }

        public String getOrderNo() {
            return orderNo;
        }
    }

    public static class OrderFilterCriteria {
        private Long schoolId;

        public OrderFilterCriteria(Long schoolId) {
            this.schoolId = schoolId;
        }

        public Long getSchoolId() {
            return schoolId;
        }
    }

    public static class OrderCommand {
        private Long id;
        private String orderNo;

        public OrderCommand(Long id, String orderNo) {
            this.id = id;
            this.orderNo = orderNo;
        }

        public Long getId() {
            return id;
        }

        public String getOrderNo() {
            return orderNo;
        }
    }

    public static class OrderSummary {
        public Long id;
        public String orderNo;
    }

    private enum SampleStatus {
        ACTIVE
    }
}
