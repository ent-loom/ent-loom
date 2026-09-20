package com.entloom.crud.engine.jdbc.dao;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.api.enums.CrudIdPolicy;
import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.capability.dao.RowConstraint;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;
import com.entloom.crud.engine.jdbc.security.JdbcGuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.security.SqlIdentifierAllowlistValidator;
import com.entloom.crud.engine.jdbc.security.SqlParameterLimiter;
import com.entloom.crud.engine.jdbc.security.SqlSafetyGuard;
import com.entloom.crud.engine.jdbc.log.SqlExecutionLogger;
import com.entloom.crud.engine.jdbc.test.support.EngineJdbcTestSupport;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * DAO 无时区时间类型验收。
 */
class JdbcTemporalEntityDaoTest extends EngineJdbcTestSupport {
    private static final EntityType<TemporalEntity, Long> TEMPORAL_TYPE =
        EntityType.of(TemporalEntity.class, Long.class);

    @Test
    void should_round_trip_and_update_local_date_and_local_date_time() {
        jdbcTemplate.execute("drop table if exists t_temporal");
        jdbcTemplate.execute(
            "create table t_temporal ("
                + "id bigint primary key,"
                + "tenant_id varchar(64) not null,"
                + "business_date date not null,"
                + "order_date date,"
                + "event_at timestamp"
                + ")"
        );

        EntityMetaRegistry temporalRegistry = temporalRegistry();
        EntityDao<TemporalEntity, Long> dao = temporalDao(temporalRegistry);
        TemporalEntity entity = new TemporalEntity();
        entity.id = 1L;
        entity.orderDate = LocalDate.of(2026, 1, 3);
        entity.eventAt = LocalDateTime.of(2026, 1, 3, 9, 15, 30);

        EntityAccessScope scope = EntityAccessScope.of(RowConstraint.and(
            RowConstraint.eq("tenantId", "tenant-a"),
            RowConstraint.eq("businessDate", "2026-01-02")
        ));
        dao = temporalDao(temporalRegistry, scope);

        Assertions.assertEquals(Long.valueOf(1L), dao.insert(entity));
        TemporalEntity loaded = dao.findById(1L).get();
        Assertions.assertEquals(LocalDate.of(2026, 1, 2), loaded.businessDate);
        Assertions.assertEquals(LocalDate.of(2026, 1, 3), loaded.orderDate);
        Assertions.assertEquals(LocalDateTime.of(2026, 1, 3, 9, 15, 30), loaded.eventAt);
        Assertions.assertEquals(
            Date.valueOf("2026-01-02"),
            jdbcTemplate.queryForObject("select business_date from t_temporal where id = 1", Date.class)
        );
        Assertions.assertEquals(
            Timestamp.valueOf("2026-01-03 09:15:30"),
            jdbcTemplate.queryForObject("select event_at from t_temporal where id = 1", Timestamp.class)
        );

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1L);
        payload.put("orderDate", "2026-02-04");
        payload.put("eventAt", "2026-02-04 10:20:30");
        UpdatePatch<TemporalEntity> patch = new DefaultCommandPayloadBinder().bindUpdatePatch(
            payload,
            TemporalEntity.class,
            temporalRegistry.getEntityMeta(TemporalEntity.class)
        );

        Assertions.assertEquals(1, dao.updateById(1L, patch));
        loaded = dao.findById(1L).get();
        Assertions.assertEquals(LocalDate.of(2026, 2, 4), loaded.orderDate);
        Assertions.assertEquals(LocalDateTime.of(2026, 2, 4, 10, 20, 30), loaded.eventAt);
    }

    private EntityDao<TemporalEntity, Long> temporalDao(EntityMetaRegistry registry) {
        return temporalDao(registry, EntityAccessScope.unrestricted());
    }

    private EntityDao<TemporalEntity, Long> temporalDao(
        EntityMetaRegistry registry,
        EntityAccessScope scope
    ) {
        SqlSafetyGuard safetyGuard = new SqlSafetyGuard(
            new SqlIdentifierAllowlistValidator(registry),
            new SqlParameterLimiter()
        );
        JdbcGuardedSqlExecutor executor = new JdbcGuardedSqlExecutor(
            jdbcTemplate,
            safetyGuard,
            new SqlExecutionLogger()
        );
        return new JdbcEntityDaoFactory(registry, executor, StandardJdbcDialect.H2).scoped(
            TEMPORAL_TYPE,
            scope
        );
    }

    private EntityMetaRegistry temporalRegistry() {
        EntityMetaRegistry registry = new CrudRuntimeModelBackedEntityMetaRegistry(
            new CrudNativeRuntimeModelParser().parse(Collections.<Class<?>>singletonList(TemporalEntity.class))
        );
        registry.validateOrThrow();
        return registry;
    }

    /**
     * 使用无时区 Java 时间类型的测试实体。
     */
    @EntCrudEntity(
        table = "t_temporal",
        idField = "id",
        idPolicy = CrudIdPolicy.EXPLICIT,
        scopeFields = {"tenantId", "businessDate"}
    )
    public static class TemporalEntity {
        /** 数据库主键。 */
        private Long id;
        /** 租户范围字段。 */
        private String tenantId;
        /** 业务日期范围字段，不包含时区。 */
        private LocalDate businessDate;
        /** 订单日期，不包含时区。 */
        private LocalDate orderDate;
        /** 事件时间，不包含时区。 */
        private LocalDateTime eventAt;
    }
}
