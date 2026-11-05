package com.entloom.ddl.mysql;

import com.entloom.ddl.annotations.EntDdlEntity;
import com.entloom.ddl.annotations.EntDdlField;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlExecutionMode;
import com.entloom.ddl.api.DdlExecutionRequest;
import com.entloom.ddl.api.DdlExecutionResult;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlTableSnapshot;
import com.entloom.ddl.api.DdlTextCapacity;
import com.entloom.ddl.api.MetadataLoadRequest;
import com.entloom.ddl.api.QueryStrategy;
import com.entloom.ddl.bootstrap.AnnotationMetadataLoader;
import com.entloom.ddl.core.DefaultDdlEngine;
import com.entloom.ddl.enums.DdlTableSize;
import com.entloom.ddl.enums.GenerationStrategy;
import com.entloom.ddl.enums.SqlType;
import com.entloom.ddl.spring.SpringAnnotationMetadataLoader;
import com.entloom.ddl.spring.SpringJdbcQueryStrategy;
import com.entloom.ddl.spring.SpringJdbcSqlExecutor;
import com.entloom.meta.adapter.ddl.MetaDdlAdapter;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.meta.EntMetaText;
import com.entloom.testsupport.mysql.MysqlIntegrationSettings;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** 显式文本的真实 MySQL 8 闭环，不改变自动迁移安全白名单。 */
@EnabledIfSystemProperty(named = MysqlIntegrationSettings.INTEGRATION_ENABLED_PROPERTY, matches = "true")
class DdlExplicitTextMysqlTest {
    @Test
    void 应贯通加载入口建表读写修改重命名与再次比较() {
        MysqlIntegrationSettings settings = MysqlIntegrationSettings.load();
        String schema = "entloom_text_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource source = new DriverManagerDataSource(settings.url(), settings.username(), settings.password());
        JdbcTemplate jdbc = new JdbcTemplate(source);
        QueryStrategy query = new SpringJdbcQueryStrategy(source);
        SpringJdbcSqlExecutor executor = new SpringJdbcSqlExecutor(source);
        DefaultDdlEngine engine = new DefaultDdlEngine();
        try {
            // 先验证缺失库：引擎必须在建库前取得服务器默认值并生成完整计划。
            DdlEntityMetadata fresh = entity("fresh_text", field("content", SqlType.TEXT, -1, 1000, ""));
            DdlExecutionResult created = engine.execute(new DdlExecutionRequest(schema, true,
                    DdlExecutionMode.CREATE_TABLE, Collections.singletonList(fresh)), query, executor);
            assertTrue(created.success(), created.errors().toString());
            assertTrue(created.generatedSql().get(0).contains("DEFAULT CHARACTER SET"));
            assertTrue(created.generatedSql().get(1).contains("DEFAULT CHARACTER SET"));
            jdbc.execute("ALTER DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");

            MetadataLoadRequest request = new MetadataLoadRequest(Collections.emptyList(), Collections.singletonList(NativeText.class));
            List<DdlEntityMetadata> models = Arrays.asList(
                    new AnnotationMetadataLoader().load(request).get(0),
                    new SpringAnnotationMetadataLoader(null, new com.entloom.ddl.api.DdlGenerationDefaults(),
                        new com.entloom.declaration.EntityDeclarations()).load(request).get(0),
                    new MetaDdlAdapter(Collections.singletonList(NativeText.class)).models().get(0));
            for (int index = 0; index < models.size(); index++) {
                DdlEntityMetadata original = models.get(index);
                String table = "entry_text_" + index;
                DdlEntityMetadata model = new DdlEntityMetadata(original.entityClassName(), "", table, "", DdlTableSize.UNSET,
                        original.fields(), original.indexes());
                assertSuccess(engine, schema, model, DdlExecutionMode.CREATE_TABLE, query, executor);
                DdlTableSnapshot snapshot = query.readTable(schema, table);
                assertEquals(Arrays.asList("varchar(100)", "char(12)", "text", "mediumtext", "longtext"),
                        snapshot.columns().stream().map(column -> column.sqlType()).toList());
                jdbc.update("INSERT INTO `" + schema + "`.`" + table + "` VALUES (?, ?, ?, ?, ?)",
                        "中文😀", "中😀", "正文😀".repeat(1000), "中篇😀".repeat(20000), "长文😀".repeat(10000));
                assertEquals("正文😀".repeat(1000), jdbc.queryForObject("SELECT body FROM `" + schema + "`.`" + table + "`", String.class));
                assertEquals("中篇😀".repeat(20000), jdbc.queryForObject("SELECT medium FROM `" + schema + "`.`" + table + "`", String.class));
                assertUnchanged(engine, schema, model, query, executor);
            }
            for (Class<?> type : Arrays.asList(MetaText.class, MixedText.class)) {
                DdlEntityMetadata model = new MetaDdlAdapter(Collections.singletonList(type)).models().get(0);
                assertSuccess(engine, schema, model, DdlExecutionMode.CREATE_TABLE, query, executor);
                assertUnchanged(engine, schema, model, query, executor);
            }

            DdlEntityMetadata auto = new MetaDdlAdapter(Collections.singletonList(AutoText.class)).models().get(0);
            assertSuccess(engine, schema, auto, DdlExecutionMode.CREATE_TABLE, query, executor);
            assertEquals(Arrays.asList("varchar(100)", "mediumtext", "longtext"),
                    query.readTable(schema, auto.tableName()).columns().stream().map(column -> column.sqlType()).toList());
            jdbc.update("INSERT INTO `" + schema + "`.`" + auto.tableName() + "` VALUES (?, ?, ?)",
                    "中文😀", "正文😀".repeat(20000), "长文😀");
            assertUnchanged(engine, schema, auto, query, executor);

            DdlEntityMetadata semantic = new MetaDdlAdapter(Collections.singletonList(SemanticContent.class)).models().get(0);
            assertSuccess(engine, schema, semantic, DdlExecutionMode.CREATE_TABLE, query, executor);
            assertEquals(Arrays.asList("varchar(200)", "text", "text", "mediumtext"),
                    query.readTable(schema, semantic.tableName()).columns().stream().map(column -> column.sqlType()).toList());
            String semanticTable = "`" + schema + "`.`" + semantic.tableName() + "`";
            String body = "正文😀".repeat(1000);
            jdbc.update("INSERT INTO " + semanticTable + " VALUES (?, ?, ?, ?)", "标题", body, "<p>富文本😀</p>", body);
            assertEquals(body, jdbc.queryForObject("SELECT body FROM " + semanticTable, String.class));
            assertUnchanged(engine, schema, semantic, query, executor);

            String qualified = "`" + schema + "`.`preserved_text`";
            jdbc.execute("CREATE TABLE " + qualified + " (legacy VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL)"
                    + " DEFAULT CHARACTER SET latin1 COLLATE latin1_bin");
            jdbc.update("INSERT INTO " + qualified + " VALUES (?)", "中文😀");
            DdlEntityMetadata modified = entity("preserved_text", field("legacy", SqlType.VARCHAR, 100, 50, ""));
            assertSuccess(engine, schema, modified, DdlExecutionMode.CREATE_MODIFY_TABLE_AND_METAS, query, executor);
            DdlEntityMetadata renamed = entity("preserved_text", field("content", SqlType.VARCHAR, 200, 100, "legacy"),
                    field("added", SqlType.VARCHAR, 50, -1, ""));
            assertSuccess(engine, schema, renamed, DdlExecutionMode.CREATE_MODIFY_TABLE_AND_METAS, query, executor);
            DdlTableSnapshot snapshot = query.readTable(schema, "preserved_text");
            assertEquals("latin1_bin", snapshot.characterSet().collation());
            assertEquals("utf8mb4_bin", snapshot.columns().get(0).characterSet().collation());
            assertEquals("latin1_bin", snapshot.columns().get(1).characterSet().collation());
            assertEquals("中文😀", jdbc.queryForObject("SELECT content FROM " + qualified, String.class));
            assertUnchanged(engine, schema, renamed, query, executor);

            // 跨类别转换仍拒绝；全量同步显式开放后保留原字符集并稳定重复执行。
            DdlEntityMetadata text = entity("preserved_text", field("content", SqlType.AUTO, -1, 100000, ""),
                    field("added", SqlType.VARCHAR, 50, -1, ""));
            assertFalse(engine.execute(new DdlExecutionRequest(schema, false, DdlExecutionMode.CREATE_MODIFY_TABLE_AND_METAS,
                    Collections.singletonList(text)), query, executor).success());
            assertSuccess(engine, schema, text, DdlExecutionMode.CREATE_MODIFY_DELETE_ALL, query, executor);
            assertEquals("utf8mb4_bin", query.readTable(schema, "preserved_text").columns().get(0).characterSet().collation());
            assertUnchanged(engine, schema, text, query, executor);

            // 单列合法不能替代整行布局验收；MySQL 的行大小限制仍会拒绝整个建表语句。
            DdlEntityMetadata wide = entity("row_too_wide", field("first", SqlType.VARCHAR, 16383, -1, ""),
                    field("second", SqlType.VARCHAR, 16383, -1, ""));
            DdlExecutionResult rowRejected = engine.execute(new DdlExecutionRequest(schema, false, DdlExecutionMode.CREATE_TABLE,
                    Collections.singletonList(wide)), query, executor);
            assertFalse(rowRejected.success());
            assertTrue(rowRejected.errors().get(0).contains("SQL 执行失败"));
            assertFalse(query.tableExists(schema, "row_too_wide"));

            DdlEntityMetadata bad = new MetaDdlAdapter(Collections.singletonList(InsufficientText.class)).models().get(0);
            DdlExecutionResult rejected = engine.execute(new DdlExecutionRequest(schema, false, DdlExecutionMode.CREATE_TABLE,
                    Arrays.asList(entity("must_not_create", field("content", SqlType.TEXT, -1, 1000, "")), bad)), query, executor);
            assertFalse(rejected.success());
            assertTrue(rejected.errors().get(0).contains("1000"));
            assertTrue(rejected.executedSql().isEmpty());
            assertFalse(query.tableExists(schema, "must_not_create"));
        } finally {
            if (!settings.keepSchema()) { jdbc.execute("DROP DATABASE IF EXISTS `" + schema + "`"); }
        }
    }

    @Test
    void AUTO组合选型分别采用库表与重命名原列字符集() {
        MysqlIntegrationSettings settings = MysqlIntegrationSettings.load();
        String schema = "entloom_auto_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource source = new DriverManagerDataSource(settings.url(), settings.username(), settings.password());
        JdbcTemplate jdbc = new JdbcTemplate(source);
        QueryStrategy query = new SpringJdbcQueryStrategy(source);
        SpringJdbcSqlExecutor executor = new SpringJdbcSqlExecutor(source);
        DefaultDdlEngine engine = new DefaultDdlEngine();
        try {
            jdbc.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
            DdlEntityMetadata fresh = entity("database_default", field("content", SqlType.AUTO, -1, 20000, ""));
            assertSuccess(engine, schema, fresh, DdlExecutionMode.CREATE_TABLE, query, executor);
            assertEquals("mediumtext", query.readTable(schema, fresh.tableName()).columns().get(0).sqlType());

            String qualified = "`" + schema + "`.`mixed_defaults`";
            jdbc.execute("CREATE TABLE " + qualified
                    + " (legacy VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL)"
                    + " DEFAULT CHARACTER SET latin1 COLLATE latin1_bin");
            jdbc.update("INSERT INTO " + qualified + " VALUES (?)", "原文😀");
            DdlEntityMetadata desired = entity("mixed_defaults",
                    field("content", SqlType.AUTO, -1, 20000, "legacy"),
                    field("short_text", SqlType.AUTO, -1, 20000, ""),
                    field("body", SqlType.AUTO, -1, 65534, ""));
            DdlExecutionResult controlled = engine.execute(new DdlExecutionRequest(schema, false,
                    DdlExecutionMode.CREATE_MODIFY_TABLE_AND_METAS, Collections.singletonList(desired)), query, executor);
            assertFalse(controlled.success());
            assertTrue(controlled.executedSql().isEmpty());
            assertEquals("legacy", query.readTable(schema, desired.tableName()).columns().get(0).columnName());

            assertSuccess(engine, schema, desired, DdlExecutionMode.CREATE_MODIFY_DELETE_ALL, query, executor);
            DdlTableSnapshot after = query.readTable(schema, desired.tableName());
            assertEquals(Arrays.asList("mediumtext", "varchar(20000)", "text"),
                    after.columns().stream().map(column -> column.sqlType()).toList());
            assertEquals(Arrays.asList("utf8mb4_bin", "latin1_bin", "latin1_bin"),
                    after.columns().stream().map(column -> column.characterSet().collation()).toList());
            assertEquals("原文😀", jdbc.queryForObject("SELECT content FROM " + qualified, String.class));
            jdbc.update("UPDATE " + qualified + " SET short_text = ?, body = ?", "a".repeat(20000), "b".repeat(65534));
            assertEquals(65534, jdbc.queryForObject("SELECT CHAR_LENGTH(body) FROM " + qualified, Integer.class));
            assertUnchanged(engine, schema, desired, query, executor);
            assertUnchanged(engine, schema, fresh, query, executor);
        } finally {
            if (!settings.keepSchema()) { jdbc.execute("DROP DATABASE IF EXISTS `" + schema + "`"); }
        }
    }

    private static void assertSuccess(DefaultDdlEngine engine, String schema, DdlEntityMetadata model, DdlExecutionMode mode,
                                      QueryStrategy query, SpringJdbcSqlExecutor executor) {
        DdlExecutionResult result = engine.execute(new DdlExecutionRequest(schema, false, mode, Collections.singletonList(model)), query, executor);
        assertTrue(result.success(), result.errors().toString());
    }

    private static void assertUnchanged(DefaultDdlEngine engine, String schema, DdlEntityMetadata model,
                                        QueryStrategy query, SpringJdbcSqlExecutor executor) {
        for (int attempt = 0; attempt < 2; attempt++) {
            DdlExecutionResult result = engine.execute(new DdlExecutionRequest(schema, false,
                    DdlExecutionMode.CREATE_MODIFY_TABLE_AND_METAS, Collections.singletonList(model)), query, executor);
            assertTrue(result.success(), result.errors().toString());
            assertTrue(result.generatedSql().isEmpty(), result.generatedSql().toString());
        }
    }

    private static DdlEntityMetadata entity(String table, DdlFieldMetadata... fields) {
        return new DdlEntityMetadata("Article", "", table, "", DdlTableSize.UNSET, Arrays.asList(fields), Collections.emptyList());
    }

    private static DdlFieldMetadata field(String name, SqlType type, int length, int business, String rename) {
        return new DdlFieldMetadata(name, name, String.class, "", false, false, true, false, length, -1, -1,
                "", "", rename, GenerationStrategy.UNSET, type,
                new DdlTextCapacity(business, "Article#content.text.maxLength", length, "Article#content@EntDdlField.length"));
    }

    @EntDdlEntity
    static class NativeText {
        @EntDdlField(sqlType = SqlType.VARCHAR, length = 100, columnDefinition = "VARCHAR ( 100 )") String shortText;
        @EntDdlField(sqlType = SqlType.CHAR, length = 12) String code;
        @EntDdlField(sqlType = SqlType.TEXT) String body;
        @EntDdlField(sqlType = SqlType.MEDIUMTEXT) String medium;
        @EntDdlField(sqlType = SqlType.LONGTEXT) String archive;
    }

    @EntEntity
    private static class SemanticContent {
        String title;
        @com.entloom.meta.annotations.EntField(kind = com.entloom.meta.enums.EntFieldKind.CONTENT) String body;
        @com.entloom.meta.annotations.EntField(kind = com.entloom.meta.enums.EntFieldKind.RICH_CONTENT) String rich;
        @com.entloom.meta.annotations.EntField(kind = com.entloom.meta.enums.EntFieldKind.RICH_CONTENT)
        @com.entloom.meta.annotations.meta.EntMetaRichContent(maxLength = 20000) String bounded;
    }

    @EntEntity
    private static class MetaText {
        @EntMetaText(maxLength = 1000) String content;
    }

    @EntEntity
    private static class AutoText {
        @EntMetaText(maxLength = 100) String title;
        @EntMetaText(maxLength = 20000) String body;
        @EntMetaText(maxLength = 4194304) String archive;
    }

    @EntEntity
    private static class MixedText {
        @EntMetaText(maxLength = 1000) @EntDdlField(sqlType = SqlType.TEXT) String content;
    }

    @EntEntity
    private static class InsufficientText {
        @EntMetaText(maxLength = 1000) @EntDdlField(sqlType = SqlType.VARCHAR, length = 100) String content;
    }
}
