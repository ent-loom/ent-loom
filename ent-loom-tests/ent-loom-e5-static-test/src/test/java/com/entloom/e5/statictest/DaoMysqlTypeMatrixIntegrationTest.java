package com.entloom.e5.statictest;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.api.enums.CrudIdPolicy;
import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.crud.engine.jdbc.dao.JdbcEntityDaoFactory;
import com.entloom.crud.engine.jdbc.log.SqlExecutionLogger;
import com.entloom.crud.engine.jdbc.security.JdbcGuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.security.SqlIdentifierAllowlistValidator;
import com.entloom.crud.engine.jdbc.security.SqlParameterLimiter;
import com.entloom.crud.engine.jdbc.security.SqlSafetyGuard;
import com.entloom.testsupport.mysql.MysqlIntegrationSettings;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * D4.1：在实际 MySQL 8 上验收常用 Java 类型、字符集、精度和 DATETIME 时区边界。
 */
@EnabledIfSystemProperty(named = MysqlIntegrationSettings.INTEGRATION_ENABLED_PROPERTY, matches = "true")
class DaoMysqlTypeMatrixIntegrationTest {
    private static final EntityType<CommonTypesEntity, Long> ENTITY_TYPE =
        EntityType.of(CommonTypesEntity.class, Long.class);

    @Test
    @DisplayName("MySQL 8 应验证常用类型、utf8mb4、DECIMAL 精度和 DATETIME 时区稳定性")
    void should_validate_common_types_and_datetime_timezone_on_mysql8() {
        MysqlIntegrationSettings settings = MysqlIntegrationSettings.load();
        String schema = "entloom_type_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(dataSource(settings.url(), settings.username(), settings.password()));
        Throwable primaryFailure = null;
        try {
            admin.execute("create database `" + schema
                + "` character set utf8mb4 collate utf8mb4_0900_ai_ci");
            JdbcTemplate jdbcTemplate = new JdbcTemplate(
                dataSource(withSchema(settings.url(), schema, false), settings.username(), settings.password())
            );
            jdbcTemplate.execute("set time_zone = '+08:00'");
            jdbcTemplate.execute(
                "create table dao_type_matrix ("
                    + "id bigint not null primary key,"
                    + "display_name varchar(128) not null,"
                    + "enabled tinyint(1) not null,"
                    + "quantity int not null,"
                    + "total_count bigint not null,"
                    + "amount decimal(19,4) not null,"
                    + "business_date date not null,"
                    + "event_at datetime(6) not null,"
                    + "nullable_note varchar(128) null"
                    + ") engine=InnoDB default character set=utf8mb4 collate=utf8mb4_0900_ai_ci"
            );

            assertEquals(
                "utf8mb4",
                jdbcTemplate.queryForObject(
                    "select character_set_name from information_schema.columns"
                        + " where table_schema = database() and table_name = 'dao_type_matrix'"
                        + " and column_name = 'display_name'",
                    String.class
                )
            );
            assertEquals(
                "decimal(19,4)",
                jdbcTemplate.queryForObject(
                    "select column_type from information_schema.columns"
                        + " where table_schema = database() and table_name = 'dao_type_matrix'"
                        + " and column_name = 'amount'",
                    String.class
                )
            );

            EntityMetaRegistry registry = new CrudRuntimeModelBackedEntityMetaRegistry(
                new CrudNativeRuntimeModelParser().parse(
                    Collections.<Class<?>>singletonList(CommonTypesEntity.class)
                )
            );
            registry.validateOrThrow();
            SqlSafetyGuard safetyGuard = new SqlSafetyGuard(
                new SqlIdentifierAllowlistValidator(registry),
                new SqlParameterLimiter()
            );
            EntityDao<CommonTypesEntity, Long> dao = new JdbcEntityDaoFactory(
                registry,
                new JdbcGuardedSqlExecutor(jdbcTemplate, safetyGuard, new SqlExecutionLogger())
            ).scoped(ENTITY_TYPE, com.entloom.crud.core.capability.dao.EntityAccessScope.unrestricted());

            CommonTypesEntity entity = new CommonTypesEntity();
            entity.id = 1L;
            entity.displayName = "张三";
            entity.enabled = Boolean.TRUE;
            entity.quantity = 7;
            entity.totalCount = 9000000000L;
            entity.amount = new BigDecimal("1234567890123.4567");
            entity.businessDate = LocalDate.of(2026, 9, 18);
            entity.eventAt = LocalDateTime.of(2026, 9, 18, 10, 20, 30, 123456000);
            entity.nullableNote = "备注";

            assertEquals(Long.valueOf(1L), dao.insert(entity));
            jdbcTemplate.execute("set time_zone = '+00:00'");

            CommonTypesEntity loaded = dao.findById(1L).get();
            assertEquals("张三", loaded.displayName);
            assertEquals(Boolean.TRUE, loaded.enabled);
            assertEquals(Integer.valueOf(7), loaded.quantity);
            assertEquals(Long.valueOf(9000000000L), loaded.totalCount);
            assertEquals(new BigDecimal("1234567890123.4567"), loaded.amount);
            assertEquals(LocalDate.of(2026, 9, 18), loaded.businessDate);
            assertEquals(LocalDateTime.of(2026, 9, 18, 10, 20, 30, 123456000), loaded.eventAt);
            assertEquals("备注", loaded.nullableNote);
            assertEquals(
                "2026-09-18 10:20:30.123456",
                jdbcTemplate.queryForObject(
                    "select date_format(event_at, '%Y-%m-%d %H:%i:%s.%f') from dao_type_matrix where id = 1",
                    String.class
                )
            );

            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("id", 1L);
            payload.put("displayName", "李四");
            payload.put("enabled", "false");
            payload.put("amount", "9876543210.1234");
            payload.put("nullableNote", null);
            UpdatePatch<CommonTypesEntity> patch = new DefaultCommandPayloadBinder().bindUpdatePatch(
                payload,
                CommonTypesEntity.class,
                registry.getEntityMeta(CommonTypesEntity.class)
            );
            assertEquals(1, dao.updateById(1L, patch));
            loaded = dao.findById(1L).get();
            assertEquals("李四", loaded.displayName);
            assertEquals(Boolean.FALSE, loaded.enabled);
            assertEquals(new BigDecimal("9876543210.1234"), loaded.amount);
            assertNull(loaded.nullableNote);
        } catch (RuntimeException | Error exception) {
            primaryFailure = exception;
            throw exception;
        } finally {
            try {
                admin.execute("drop database if exists `" + schema + "`");
            } catch (RuntimeException cleanupFailure) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    private DataSource dataSource(String url, String username, String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    private String withSchema(String baseUrl, String schema, boolean useAffectedRows) {
        int queryStart = baseUrl.indexOf('?');
        String authority = queryStart < 0 ? baseUrl : baseUrl.substring(0, queryStart);
        String query = queryStart < 0 ? "" : baseUrl.substring(queryStart + 1);
        int slash = authority.lastIndexOf('/');
        StringBuilder result = new StringBuilder(authority.substring(0, slash + 1)).append(schema);
        StringBuilder parameters = new StringBuilder();
        if (!query.isEmpty()) {
            for (String parameter : query.split("&")) {
                if (!parameter.toLowerCase(java.util.Locale.ROOT).startsWith("useaffectedrows=")) {
                    if (parameters.length() > 0) {
                        parameters.append('&');
                    }
                    parameters.append(parameter);
                }
            }
        }
        if (parameters.length() > 0) {
            parameters.append('&');
        }
        parameters.append("useAffectedRows=").append(useAffectedRows);
        return result.append('?').append(parameters).toString();
    }

    /** MySQL 类型矩阵测试实体。 */
    @EntCrudEntity(
        name = "dao_type_matrix",
        table = "dao_type_matrix",
        idField = "id",
        idPolicy = CrudIdPolicy.EXPLICIT
    )
    public static class CommonTypesEntity {
        /** 数据库主键。 */
        private Long id;
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
