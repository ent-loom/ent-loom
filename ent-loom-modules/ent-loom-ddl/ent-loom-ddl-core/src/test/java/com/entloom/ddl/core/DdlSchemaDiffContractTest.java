package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlColumnMetadata;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlExecutionMode;
import com.entloom.ddl.api.DdlExecutionRequest;
import com.entloom.ddl.api.DdlExecutionResult;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlIndexMetadata;
import com.entloom.ddl.api.DdlTableSnapshot;
import com.entloom.ddl.enums.DdlTableSize;
import com.entloom.ddl.enums.GenerationStrategy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E3 字段、索引差异和执行模式合同测试。
 */
class DdlSchemaDiffContractTest {

    @Test
    @DisplayName("修改模式按表注释、字段、索引顺序生成确定的 ALTER SQL")
    void shouldGenerateStableAlterSqlForAllowedChanges() {
        DdlEntityMetadata desired = entity(
                field("id", "id", Long.class, false, false, true, -1, "", ""),
                field("name", "name", String.class, true, false, false, 80, "", ""),
                field("email", "email", String.class, true, true, false, -1, "", ""));
        DdlTableSnapshot current = snapshot("account", "旧账户表",
                column("id", "bigint", false),
                column("name", "varchar(80)", true));

        RecordingExecutor executor = new RecordingExecutor();
        DdlExecutionResult result = new DefaultDdlEngine().execute(
                request(DdlExecutionMode.CREATE_MODIFY_TABLE_AND_METAS, desired, current),
                new SnapshotQueryStrategy(current), executor);

        assertTrue(result.errors().isEmpty());
        assertEquals(Arrays.asList(
                "ALTER TABLE `account` COMMENT='账户表'",
                "ALTER TABLE `account` ADD COLUMN `email` varchar(200) NULL",
                "ALTER TABLE `account` ADD UNIQUE KEY `uk_account_email` (`email`)"),
                result.generatedSql());
        assertEquals(result.generatedSql(), executor.statements);
        assertEquals(result.generatedSql(), result.executedSql());
    }

    @Test
    @DisplayName("修改模式支持安全扩容和字段重命名，但不允许危险变化")
    void shouldAllowLimitedFieldChangesAndRejectDangerousChanges() {
        DdlEntityMetadata widened = entity(
                field("id", "id", Long.class, false, false, true, -1, "", ""),
                field("name", "name", String.class, true, false, false, 120, "", ""));
        DdlSchemaDiff wideningDiff = new DdlSchemaDiffer().diff(
                widened,
                snapshot("account", "", column("id", "bigint", false), column("name", "varchar(80)", true)));

        assertTrue(wideningDiff.errors().isEmpty());
        assertEquals(1, wideningDiff.changedFields().size());
        assertFalse(wideningDiff.changedFields().get(0).renamed());
        DdlExecutionResult wideningResult = new DefaultDdlEngine().execute(
                request(DdlExecutionMode.CREATE_MODIFY_TABLE_AND_METAS, widened,
                        snapshot("account", "", column("id", "bigint", false), column("name", "varchar(80)", true))),
                new SnapshotQueryStrategy(snapshot("account", "", column("id", "bigint", false), column("name", "varchar(80)", true))),
                null);
        assertEquals(Arrays.asList(
                "ALTER TABLE `account` COMMENT='账户表'",
                "ALTER TABLE `account` MODIFY COLUMN `name` varchar(120) NULL"),
                wideningResult.generatedSql());

        DdlEntityMetadata renamed = entity(
                field("id", "id", Long.class, false, false, true, -1, "", ""),
                field("displayName", "display_name", String.class, true, false, false, -1, "", "name"));
        DdlSchemaDiff renameDiff = new DdlSchemaDiffer().diff(
                renamed,
                snapshot("account", "", column("id", "bigint", false), column("name", "varchar(200)", true)));
        assertTrue(renameDiff.errors().isEmpty());
        assertTrue(renameDiff.changedFields().get(0).renamed());

        DdlEntityMetadata removed = entity(field("id", "id", Long.class, false, false, true, -1, "", ""));
        DdlSchemaDiff removalDiff = new DdlSchemaDiffer().diff(
                removed,
                snapshot("account", "", column("id", "bigint", false), column("name", "varchar(80)", true)));
        assertTrue(removalDiff.errors().stream().anyMatch(error -> error.contains("删除字段")));

        DdlEntityMetadata narrowed = entity(
                field("id", "id", Long.class, false, false, true, -1, "", ""),
                field("name", "name", String.class, true, false, false, 40, "", ""));
        DdlSchemaDiff narrowingDiff = new DdlSchemaDiffer().diff(
                narrowed,
                snapshot("account", "", column("id", "bigint", false), column("name", "varchar(80)", true)));
        assertTrue(narrowingDiff.errors().stream().anyMatch(error -> error.contains("不兼容")));
    }

    @Test
    @DisplayName("DECIMAL 扩容必须同时保留整数位和小数位")
    void shouldRejectDecimalChangeThatLosesIntegerDigits() {
        DdlEntityMetadata desired = entity(
                field("id", "id", Long.class, false, false, true, -1, "", ""),
                new DdlFieldMetadata("amount", "amount", java.math.BigDecimal.class, "", true, false,
                        true, false, -1, 11, 4, "", "", ""));

        DdlSchemaDiff unsafe = new DdlSchemaDiffer().diff(
                desired,
                snapshot("account", "", column("id", "bigint", false), column("amount", "decimal(10,2)", true)));

        assertTrue(unsafe.errors().stream().anyMatch(error -> error.contains("不兼容")));
        assertTrue(unsafe.changedFields().isEmpty());
    }

    @Test
    @DisplayName("自增字段进入修改计划时保留自增属性")
    void shouldKeepAutoIncrementInModifySql() {
        DdlFieldMetadata id = new DdlFieldMetadata("id", "id", Long.class, "", false, false,
                true, true, -1, -1, -1, "", "", "", GenerationStrategy.AUTO_INCREMENT);
        DdlEntityMetadata desired = entity(id);
        DdlTableSnapshot current = new DdlTableSnapshot(true, "", "account", "",
                Collections.singletonList(new DdlColumnMetadata("id", "bigint", false, "", "", false)),
                Collections.singletonList("id"), Collections.<DdlIndexMetadata>emptyList());

        DdlExecutionResult result = new DefaultDdlEngine().execute(
                request(DdlExecutionMode.CREATE_MODIFY_TABLE_AND_METAS, desired, current),
                new SnapshotQueryStrategy(current), null);

        assertTrue(result.generatedSql().stream().anyMatch(sql -> sql.contains("AUTO_INCREMENT")));
    }

    @Test
    @DisplayName("危险差异不会执行部分 SQL，并保留中文拒绝原因")
    void shouldRejectDangerousPlanBeforeExecution() {
        DdlEntityMetadata desired = entity(
                field("id", "id", Long.class, false, false, true, -1, "", ""));
        DdlTableSnapshot current = snapshot("account", "", column("id", "bigint", false), column("legacy", "varchar(80)", true));
        RecordingExecutor executor = new RecordingExecutor();

        DdlExecutionResult result = new DefaultDdlEngine().execute(
                request(DdlExecutionMode.CREATE_MODIFY_TABLE_AND_METAS, desired, current),
                new SnapshotQueryStrategy(current), executor);

        assertTrue(result.generatedSql().isEmpty());
        assertTrue(result.executedSql().isEmpty());
        assertTrue(result.errors().get(0).contains("删除字段"));
        assertTrue(executor.statements.isEmpty());
    }

    @Test
    @DisplayName("修改模式遇到不存在的表仍复用稳定 CREATE SQL")
    void shouldCreateMissingTableInModifyMode() {
        DdlEntityMetadata desired = entity(field("id", "id", Long.class, false, false, true, -1, "", ""));
        DdlTableSnapshot missing = DdlTableSnapshot.missing("", "account");

        DdlExecutionResult result = new DefaultDdlEngine().execute(
                request(DdlExecutionMode.CREATE_MODIFY_TABLE_AND_METAS, desired, missing),
                new SnapshotQueryStrategy(missing), null);

        assertEquals(1, result.generatedSql().size());
        assertTrue(result.generatedSql().get(0).startsWith("CREATE TABLE IF NOT EXISTS `account`"));
        assertTrue(result.success());
    }

    private static DdlExecutionRequest request(DdlExecutionMode mode,
                                               DdlEntityMetadata entity,
                                               DdlTableSnapshot snapshot) {
        return new DdlExecutionRequest("", false, mode, Collections.singletonList(entity));
    }

    private static DdlEntityMetadata entity(DdlFieldMetadata... fields) {
        return new DdlEntityMetadata("AccountEntity", "", "account", "账户表",
                DdlTableSize.SMALL, Arrays.asList(fields), Collections.<DdlIndexMetadata>emptyList());
    }

    private static DdlFieldMetadata field(String fieldName,
                                          String columnName,
                                          Class<?> javaType,
                                          boolean nullable,
                                          boolean unique,
                                          boolean primaryKey,
                                          int length,
                                          String defaultValue,
                                          String renameFrom) {
        return new DdlFieldMetadata(fieldName, columnName, javaType, "", nullable, unique, true,
                primaryKey, length, -1, -1, defaultValue, "", renameFrom);
    }

    private static DdlColumnMetadata column(String name, String type, boolean nullable) {
        return new DdlColumnMetadata(name, type, nullable, "", "");
    }

    private static DdlTableSnapshot snapshot(String table, String comment, DdlColumnMetadata... columns) {
        return snapshot(table, comment, Arrays.asList(columns));
    }

    private static DdlTableSnapshot snapshot(String table, String comment, List<DdlColumnMetadata> columns) {
        return new DdlTableSnapshot(true, "", table, comment, columns,
                Collections.singletonList("id"), Collections.<DdlIndexMetadata>emptyList());
    }

    private static final class SnapshotQueryStrategy implements com.entloom.ddl.api.QueryStrategy {
        private final DdlTableSnapshot snapshot;

        private SnapshotQueryStrategy(DdlTableSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public boolean tableExists(String schema, String tableName) {
            return snapshot.exists();
        }

        @Override
        public DdlTableSnapshot readTable(String schema, String tableName) {
            return snapshot;
        }
    }

    private static final class RecordingExecutor implements com.entloom.ddl.api.SqlExecutor {
        private List<String> statements = new ArrayList<String>();

        @Override
        public void execute(List<String> sqlStatements) {
            statements.addAll(sqlStatements);
        }
    }
}
