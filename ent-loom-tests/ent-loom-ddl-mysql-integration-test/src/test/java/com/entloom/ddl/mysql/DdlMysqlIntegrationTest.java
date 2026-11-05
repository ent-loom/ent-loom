package com.entloom.ddl.mysql;

import com.entloom.ddl.api.DdlIndexMetadata;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlTableSnapshot;
import com.entloom.ddl.api.QueryStrategy;
import com.entloom.ddl.api.DdlExecutionMode;
import com.entloom.ddl.api.DdlEngine;
import com.entloom.ddl.api.DdlExecutionRequest;
import com.entloom.ddl.api.DdlExecutionResult;
import com.entloom.ddl.api.MetadataLoader;
import com.entloom.ddl.api.MetadataLoadRequest;
import com.entloom.ddl.api.SqlExecutor;
import com.entloom.ddl.enums.GenerationStrategy;
import com.entloom.ddl.mysql.fixture.MysqlAccount;
import com.entloom.ddl.spring.EntDdlSpringOptions;
import com.entloom.ddl.starter.EntDdlAutoConfiguration;
import com.entloom.testsupport.mysql.MysqlIntegrationSettings;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MySQL 8 定向集成测试。
 *
 * <p>默认不参与普通测试；通过 {@code mysql-integration} profile 指向隔离的
 * MySQL 8 实例后，验证 Starter、Spring JDBC 和实际建库建表结果。</p>
 */
@EnabledIfSystemProperty(named = MysqlIntegrationSettings.INTEGRATION_ENABLED_PROPERTY, matches = "true")
class DdlMysqlIntegrationTest {

    @Test
    @DisplayName("MySQL 8 中完成建库、建表并保留字段主键索引")
    void shouldCreateMysqlSchemaTableAndIndexes() {
        MysqlIntegrationSettings settings = MysqlIntegrationSettings.load();
        String url = settings.url();
        String username = settings.username();
        String password = settings.password();
        String schema = "entloom_ddl_e25_" + UUID.randomUUID().toString().replace("-", "");
        String table = "mysql_account";

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(EntDdlSpringOptions.class, () -> options(schema));
        context.register(EntDdlAutoConfiguration.class);
        Throwable primaryFailure = null;
        try {
            context.refresh();

            assertEquals(1, count(jdbcTemplate,
                    "SELECT COUNT(*) FROM information_schema.tables"
                            + " WHERE table_schema = ? AND table_name = ?",
                    schema, table));
            assertEquals("bigint", columnType(jdbcTemplate, schema, table, "id"));
            assertEquals("varchar(80)", columnType(jdbcTemplate, schema, table, "display_name"));
            assertEquals("auto_increment", columnExtra(jdbcTemplate, schema, table, "id"));
            assertEquals(1, count(jdbcTemplate,
                    "SELECT COUNT(*) FROM information_schema.statistics"
                            + " WHERE table_schema = ? AND table_name = ?"
                            + " AND index_name = 'PRIMARY' AND column_name = 'id'",
                    schema, table));
            assertEquals(1, count(jdbcTemplate,
                    "SELECT COUNT(*) FROM information_schema.statistics"
                            + " WHERE table_schema = ? AND table_name = ?"
                            + " AND index_name = ? AND column_name = 'display_name'",
                    schema, table, "idx_mysql_account_display_name"));

            DdlTableSnapshot snapshot = context.getBean(QueryStrategy.class).readTable(schema, table);
            assertEquals("tinyint(1)", snapshot.columns().stream()
                    .filter(column -> "active".equals(column.columnName()))
                    .findFirst().orElseThrow().sqlType());
            assertTrue(snapshot.indexes().stream()
                    .anyMatch(index -> "idx_mysql_account_lower_name".equals(index.name())
                            && index.expression().contains("display_name")));

            // 已建表后连续执行修改模式，布尔字段不能被 JDBC 的 BIT 映射误判。
            DdlExecutionRequest modifyRequest = new DdlExecutionRequest(schema, false,
                    DdlExecutionMode.CREATE_MODIFY_TABLE_AND_METAS,
                    context.getBean(MetadataLoader.class).load(new MetadataLoadRequest(
                            Collections.emptyList(), Collections.singletonList(MysqlAccount.class))));
            for (int attempt = 0; attempt < 2; attempt++) {
                DdlExecutionResult result = context.getBean(DdlEngine.class).execute(modifyRequest,
                        context.getBean(QueryStrategy.class), context.getBean(SqlExecutor.class));
                assertTrue(result.success(), result.errors().toString());
                assertTrue(result.generatedSql().isEmpty(), result.generatedSql().toString());
            }
            verifyExtraColumns(context, jdbcTemplate, schema, table, modifyRequest);
            verifyFullSynchronization(context, jdbcTemplate, schema, table, modifyRequest);
        } catch (RuntimeException | Error exception) {
            primaryFailure = exception;
            throw exception;
        } finally {
            cleanUp(context, jdbcTemplate, schema, primaryFailure, settings.keepSchema());
        }
    }

    private static void verifyExtraColumns(AnnotationConfigApplicationContext context,
                                            JdbcTemplate jdbcTemplate,
                                            String schema,
                                            String table,
                                            DdlExecutionRequest modifyRequest) {
        String qualifiedTable = "`" + schema + "`.`" + table + "`";
        jdbcTemplate.execute("ALTER TABLE " + qualifiedTable
                + " ADD COLUMN nullable_legacy BIGINT NULL,"
                + " ADD COLUMN empty_legacy VARCHAR(80) NOT NULL DEFAULT '',"
                + " ADD COLUMN virtual_legacy INT GENERATED ALWAYS AS (CHAR_LENGTH(display_name)) VIRTUAL,"
                + " ADD COLUMN stored_legacy INT GENERATED ALWAYS AS (CHAR_LENGTH(display_name)) STORED,"
                + " ADD COLUMN timestamp_legacy TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
        QueryStrategy queryStrategy = context.getBean(QueryStrategy.class);
        DdlTableSnapshot snapshot = queryStrategy.readTable(schema, table);
        assertTrue(snapshot.columns().stream().filter(column -> "empty_legacy".equals(column.columnName()))
                .allMatch(column -> column.hasDefaultValue() && column.defaultValue().isEmpty()));
        assertEquals(2L, snapshot.columns().stream().filter(column -> column.generated()).count());
        assertTrue(snapshot.columns().stream().filter(column -> "timestamp_legacy".equals(column.columnName()))
                .allMatch(column -> column.hasDefaultValue() && !column.generated()));
        for (int attempt = 0; attempt < 2; attempt++) {
            DdlExecutionResult result = context.getBean(DdlEngine.class).execute(modifyRequest,
                    queryStrategy, context.getBean(SqlExecutor.class));
            assertTrue(result.success(), result.errors().toString());
            assertEquals(5, result.warnings().size());
            assertTrue(result.generatedSql().isEmpty(), result.generatedSql().toString());
        }
        jdbcTemplate.update("INSERT INTO " + qualifiedTable + " (display_name, active) VALUES (?, ?)", "测试账户", true);
        assertEquals("", jdbcTemplate.queryForObject("SELECT empty_legacy FROM " + qualifiedTable, String.class));
        assertEquals(4, jdbcTemplate.queryForObject("SELECT stored_legacy FROM " + qualifiedTable, Integer.class));

        jdbcTemplate.execute("ALTER TABLE " + qualifiedTable + " ADD COLUMN required_legacy BIGINT NOT NULL");
        DdlExecutionResult rejected = context.getBean(DdlEngine.class).execute(modifyRequest,
                queryStrategy, context.getBean(SqlExecutor.class));
        assertFalse(rejected.success());
        assertTrue(rejected.errors().stream().anyMatch(error -> error.contains("required_legacy")
                && error.contains("无默认值")));
        assertTrue(rejected.executedSql().isEmpty());
    }

    private static EntDdlSpringOptions options(String schema) {
        EntDdlSpringOptions options = new EntDdlSpringOptions();
        options.setSchema(schema);
        options.setCreateDatabaseIfMissing(true);
        options.setMode(DdlExecutionMode.CREATE_TABLE);
        options.setBasePackages(Collections.singletonList("com.entloom.ddl.mysql.fixture"));
        return options;
    }

    private static void verifyFullSynchronization(AnnotationConfigApplicationContext context,
                                                   JdbcTemplate jdbcTemplate,
                                                   String schema,
                                                   String table,
                                                   DdlExecutionRequest modifyRequest) {
        String qualifiedTable = "`" + schema + "`.`" + table + "`";
        // 同时覆盖类型收缩、索引重建及删除带普通/表达式索引的多余列。
        jdbcTemplate.execute("ALTER TABLE " + qualifiedTable
                + " MODIFY COLUMN display_name VARCHAR(120) NOT NULL COMMENT '展示名称',"
                + " DROP INDEX idx_mysql_account_display_name,"
                + " ADD UNIQUE KEY idx_mysql_account_display_name (display_name),"
                + " ADD KEY idx_required_legacy (required_legacy),"
                + " ADD KEY idx_legacy_expression ((nullable_legacy + 1))");
        jdbcTemplate.execute("CREATE TABLE `" + schema + "`.`unmanaged` (id BIGINT NOT NULL PRIMARY KEY)");
        jdbcTemplate.update("INSERT INTO `" + schema + "`.`unmanaged` VALUES (9)");
        DdlExecutionRequest fullRequest = new DdlExecutionRequest(schema, false,
                DdlExecutionMode.CREATE_MODIFY_DELETE_ALL, modifyRequest.entities());
        DdlEngine engine = context.getBean(DdlEngine.class);
        QueryStrategy query = context.getBean(QueryStrategy.class);
        SqlExecutor executor = context.getBean(SqlExecutor.class);
        DdlExecutionResult result = engine.execute(fullRequest, query, executor);
        assertTrue(result.success(), result.errors().toString());
        assertEquals(1, result.executedSql().size());
        DdlTableSnapshot synchronizedTable = query.readTable(schema, table);
        assertEquals(3, synchronizedTable.columns().size());
        assertEquals("varchar(80)", columnType(jdbcTemplate, schema, table, "display_name"));
        assertTrue(synchronizedTable.indexes().stream().anyMatch(index ->
                "idx_mysql_account_display_name".equals(index.name()) && !index.unique()));
        assertEquals("测试账户", jdbcTemplate.queryForObject("SELECT display_name FROM " + qualifiedTable, String.class));
        // 删除旧的必填列后，正常业务插入无需再提供旧字段。
        jdbcTemplate.update("INSERT INTO " + qualifiedTable + " (display_name, active) VALUES (?, ?)", "同步后账户", true);
        assertEquals(2, count(jdbcTemplate, "SELECT COUNT(*) FROM " + qualifiedTable));
        assertEquals(9, jdbcTemplate.queryForObject("SELECT id FROM `" + schema + "`.`unmanaged`", Integer.class));
        assertNoFurtherChanges(engine, query, executor, fullRequest);

        // MySQL 必须在同一 ALTER 中完成自增主键替换，否则中间结构不合法。
        DdlEntityMetadata original = fullRequest.entities().get(0);
        DdlFieldMetadata renamedId = new DdlFieldMetadata("id", "account_id", Long.class, "", false,
                false, true, true, -1, -1, -1, "", "主键", "id",
                GenerationStrategy.AUTO_INCREMENT);
        List<DdlFieldMetadata> renamedFields = new ArrayList<>(original.fields());
        int idPosition = 0;
        while (!"id".equals(renamedFields.get(idPosition).columnName())) {
            idPosition++;
        }
        renamedFields.set(idPosition, renamedId);
        DdlEntityMetadata renamed = new DdlEntityMetadata(original.entityClassName(), original.schema(), table,
                original.comment(), original.tableSize(), renamedFields, original.indexes());
        DdlExecutionRequest renameRequest = new DdlExecutionRequest(schema, false,
                DdlExecutionMode.CREATE_MODIFY_DELETE_ALL, Collections.singletonList(renamed));
        DdlExecutionResult renameResult = engine.execute(renameRequest, query, executor);
        assertTrue(renameResult.success(), renameResult.errors().toString());
        assertEquals(Collections.singletonList("account_id"), query.readTable(schema, table).primaryKeyColumns());
        assertEquals("auto_increment", columnExtra(jdbcTemplate, schema, table, "account_id"));
        assertNoFurtherChanges(engine, query, executor, renameRequest);

        // 取消自增并替换为复合主键，验证多个主键列的声明顺序。
        DdlFieldMetadata explicitId = new DdlFieldMetadata("id", "account_id", Long.class, "", false,
                false, true, true, -1, -1, -1, "", "主键", "",
                GenerationStrategy.NONE);
        DdlFieldMetadata partition = new DdlFieldMetadata("partition", "partition_id", Long.class, "", false,
                false, true, true, -1, -1, -1, "0", "", "");
        renamedFields.set(idPosition, explicitId);
        renamedFields.add(partition);
        DdlEntityMetadata composite = new DdlEntityMetadata(original.entityClassName(), original.schema(), table,
                original.comment(), original.tableSize(), renamedFields, original.indexes());
        DdlExecutionRequest compositeRequest = new DdlExecutionRequest(schema, false,
                DdlExecutionMode.CREATE_MODIFY_DELETE_ALL, Collections.singletonList(composite));
        DdlExecutionResult compositeResult = engine.execute(compositeRequest, query, executor);
        assertTrue(compositeResult.success(), compositeResult.errors().toString());
        assertEquals(Arrays.asList("account_id", "partition_id"), query.readTable(schema, table).primaryKeyColumns());
        assertEquals("", columnExtra(jdbcTemplate, schema, table, "account_id"));
        assertNoFurtherChanges(engine, query, executor, compositeRequest);
    }

    private static void assertNoFurtherChanges(DdlEngine engine, QueryStrategy query, SqlExecutor executor,
                                               DdlExecutionRequest request) {
        for (int attempt = 0; attempt < 2; attempt++) {
            DdlExecutionResult result = engine.execute(request, query, executor);
            assertTrue(result.success(), result.errors().toString());
            assertTrue(result.generatedSql().isEmpty(), result.generatedSql().toString());
        }
    }

    private static int count(JdbcTemplate jdbcTemplate, String sql, Object... arguments) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private static String columnType(JdbcTemplate jdbcTemplate,
                                     String schema,
                                     String table,
                                     String column) {
        return jdbcTemplate.queryForObject(
                "SELECT column_type FROM information_schema.columns"
                        + " WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                String.class, schema, table, column);
    }

    private static String columnExtra(JdbcTemplate jdbcTemplate,
                                      String schema,
                                      String table,
                                      String column) {
        return jdbcTemplate.queryForObject(
                "SELECT extra FROM information_schema.columns"
                        + " WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                String.class, schema, table, column);
    }

    private static void cleanUp(AnnotationConfigApplicationContext context,
                                JdbcTemplate jdbcTemplate,
                                String schema,
                                Throwable primaryFailure,
                                boolean keepSchema) {
        RuntimeException cleanupFailure = null;
        try {
            context.close();
        } catch (RuntimeException exception) {
            cleanupFailure = exception;
        }
        if (keepSchema) {
            System.out.println("保留 MySQL 集成测试 schema 供检查: " + schema);
        } else {
            try {
                jdbcTemplate.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            } catch (RuntimeException exception) {
                if (cleanupFailure == null) {
                    cleanupFailure = exception;
                } else {
                    cleanupFailure.addSuppressed(exception);
                }
            }
        }
        if (cleanupFailure == null) {
            return;
        }
        if (primaryFailure != null) {
            primaryFailure.addSuppressed(cleanupFailure);
            return;
        }
        throw cleanupFailure;
    }

}
