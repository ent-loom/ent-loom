package com.entloom.crud.engine.jdbc.dao;

import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.capability.dao.RowConstraint;
import com.entloom.crud.core.exception.EntityDaoConstraintException;
import com.entloom.crud.core.exception.EntityDaoWriteMissException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.engine.jdbc.test.entity.OrderTestEntity;
import com.entloom.crud.engine.jdbc.test.support.EngineJdbcTestSupport;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JdbcEntityDaoTest extends EngineJdbcTestSupport {
    private static final EntityType<OrderTestEntity, Long> ORDER_TYPE =
        EntityType.of(OrderTestEntity.class, Long.class);

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
}
