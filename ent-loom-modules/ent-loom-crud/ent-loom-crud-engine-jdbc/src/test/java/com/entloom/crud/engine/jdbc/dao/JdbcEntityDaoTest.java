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
import com.entloom.crud.api.enums.CrudIdPolicy;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.runtime.context.CrudExecutionContext;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.CrudRuntimeModel;
import com.entloom.crud.engine.jdbc.log.SqlExecutionLogger;
import com.entloom.crud.engine.jdbc.security.JdbcGuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.security.SqlIdentifierAllowlistValidator;
import com.entloom.crud.engine.jdbc.security.SqlParameterLimiter;
import com.entloom.crud.engine.jdbc.security.SqlSafetyGuard;
import com.entloom.crud.engine.jdbc.test.entity.OrderTestEntity;
import com.entloom.crud.engine.jdbc.test.support.EngineJdbcTestSupport;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

        entities.get(0).setOrderNo("ORD-BATCH-1-UPDATED");
        entities.get(1).setOrderNo("ORD-BATCH-2-UPDATED");
        Assertions.assertEquals(2, dao.updateAll(entities));
        Assertions.assertEquals(
            "ORD-BATCH-1-UPDATED",
            dao.findById(11001L).get().getOrderNo()
        );

        Assertions.assertEquals(2, dao.deleteAll(entities));
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

    private List<Long> ids(List<OrderTestEntity> entities) {
        List<Long> result = new ArrayList<Long>();
        for (OrderTestEntity entity : entities) {
            result.add(entity.getId());
        }
        return result;
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
        SqlSafetyGuard safetyGuard = new SqlSafetyGuard(
            new SqlIdentifierAllowlistValidator(registry),
            new SqlParameterLimiter()
        );
        return new JdbcEntityDaoFactory(
            registry,
            new JdbcGuardedSqlExecutor(jdbcTemplate, safetyGuard, new SqlExecutionLogger())
        );
    }
}
