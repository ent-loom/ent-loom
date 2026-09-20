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
import com.entloom.crud.engine.jdbc.log.SqlExecutionLogger;
import com.entloom.crud.engine.jdbc.security.JdbcGuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.security.SqlIdentifierAllowlistValidator;
import com.entloom.crud.engine.jdbc.security.SqlParameterLimiter;
import com.entloom.crud.engine.jdbc.security.SqlSafetyGuard;
import com.entloom.crud.engine.jdbc.test.support.EngineJdbcTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * JDBC 常用 Java 类型矩阵验收。
 */
class JdbcCommonTypesEntityDaoTest extends EngineJdbcTestSupport {
    private static final EntityType<CommonTypesEntity, Long> ENTITY_TYPE =
        EntityType.of(CommonTypesEntity.class, Long.class);

    @Test
    void should_round_trip_common_types_precision_null_and_utf8_text() {
        jdbcTemplate.execute("drop table if exists t_common_types");
        jdbcTemplate.execute(
            "create table t_common_types ("
                + "id bigint primary key,"
                + "tenant_id varchar(64) not null,"
                + "display_name varchar(128) not null,"
                + "enabled boolean not null,"
                + "quantity int not null,"
                + "total_count bigint not null,"
                + "amount decimal(19,4) not null,"
                + "business_date date not null,"
                + "event_at timestamp not null,"
                + "nullable_note varchar(128)"
                + ")"
        );

        EntityMetaRegistry registry = registry();
        EntityDao<CommonTypesEntity, Long> dao = new JdbcEntityDaoFactory(
            registry,
            new JdbcGuardedSqlExecutor(
                jdbcTemplate,
                new SqlSafetyGuard(
                    new SqlIdentifierAllowlistValidator(registry),
                    new SqlParameterLimiter()
                ),
                new SqlExecutionLogger()
            ),
            StandardJdbcDialect.H2
        ).scoped(
            ENTITY_TYPE,
            EntityAccessScope.of(RowConstraint.eq("tenantId", "租户一"))
        );

        CommonTypesEntity entity = new CommonTypesEntity();
        entity.id = 1L;
        entity.displayName = "张三";
        entity.enabled = Boolean.TRUE;
        entity.quantity = 7;
        entity.totalCount = 9000000000L;
        entity.amount = new BigDecimal("1234567890123.4567");
        entity.businessDate = LocalDate.of(2026, 9, 18);
        entity.eventAt = LocalDateTime.of(2026, 9, 18, 10, 20, 30);

        Assertions.assertEquals(Long.valueOf(1L), dao.insert(entity));

        CommonTypesEntity loaded = dao.findById(1L).get();
        Assertions.assertEquals("租户一", loaded.tenantId);
        Assertions.assertEquals("张三", loaded.displayName);
        Assertions.assertEquals(Boolean.TRUE, loaded.enabled);
        Assertions.assertEquals(Integer.valueOf(7), loaded.quantity);
        Assertions.assertEquals(Long.valueOf(9000000000L), loaded.totalCount);
        Assertions.assertEquals(new BigDecimal("1234567890123.4567"), loaded.amount);
        Assertions.assertEquals(LocalDate.of(2026, 9, 18), loaded.businessDate);
        Assertions.assertEquals(LocalDateTime.of(2026, 9, 18, 10, 20, 30), loaded.eventAt);
        Assertions.assertNull(loaded.nullableNote);

        Assertions.assertEquals(
            new BigDecimal("1234567890123.4567"),
            jdbcTemplate.queryForObject("select amount from t_common_types where id = 1", BigDecimal.class)
        );

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1L);
        payload.put("displayName", "李四");
        payload.put("enabled", "false");
        payload.put("quantity", "8");
        payload.put("amount", "9876543210.1234");
        payload.put("nullableNote", null);
        UpdatePatch<CommonTypesEntity> patch = new DefaultCommandPayloadBinder().bindUpdatePatch(
            payload,
            CommonTypesEntity.class,
            registry.getEntityMeta(CommonTypesEntity.class)
        );

        Assertions.assertEquals(1, dao.updateById(1L, patch));
        loaded = dao.findById(1L).get();
        Assertions.assertEquals("李四", loaded.displayName);
        Assertions.assertEquals(Boolean.FALSE, loaded.enabled);
        Assertions.assertEquals(Integer.valueOf(8), loaded.quantity);
        Assertions.assertEquals(new BigDecimal("9876543210.1234"), loaded.amount);
        Assertions.assertNull(loaded.nullableNote);
    }

    private EntityMetaRegistry registry() {
        EntityMetaRegistry registry = new CrudRuntimeModelBackedEntityMetaRegistry(
            new CrudNativeRuntimeModelParser().parse(
                Collections.<Class<?>>singletonList(CommonTypesEntity.class)
            )
        );
        registry.validateOrThrow();
        return registry;
    }

    /** 常用 JDBC 类型测试实体。 */
    @EntCrudEntity(
        table = "t_common_types",
        idField = "id",
        idPolicy = CrudIdPolicy.EXPLICIT,
        scopeFields = {"tenantId"}
    )
    public static class CommonTypesEntity {
        /** 数据库主键。 */
        private Long id;
        /** 租户范围字段。 */
        private String tenantId;
        /** UTF-8 文本。 */
        private String displayName;
        /** 布尔状态。 */
        private Boolean enabled;
        /** 整数数量。 */
        private Integer quantity;
        /** 长整数累计值。 */
        private Long totalCount;
        /** 固定四位小数金额。 */
        private BigDecimal amount;
        /** 不含时区的业务日期。 */
        private LocalDate businessDate;
        /** 不含时区的事件时间。 */
        private LocalDateTime eventAt;
        /** 可空备注。 */
        private String nullableNote;
    }
}
