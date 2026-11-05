package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlColumnMetadata;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlExecutionMode;
import com.entloom.ddl.api.DdlExecutionRequest;
import com.entloom.ddl.api.DdlExecutionResult;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlIndexMetadata;
import com.entloom.ddl.api.DdlTableSnapshot;
import com.entloom.ddl.api.QueryStrategy;
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

/** 显式全量同步的删除、重建、生成及执行边界。 */
class DdlFullSynchronizationTest {
    private final DefaultDdlEngine engine = new DefaultDdlEngine();

    @Test
    @DisplayName("全量同步删除阻碍插入的旧列及其索引，同时新增当前字段")
    void shouldRemoveLegacyColumnsAndIndexes() {
        DdlEntityMetadata desired = entity(Collections.emptyList(), id(), field("merchant_id", Long.class, false));
        DdlTableSnapshot current = snapshot(Arrays.asList(column("id", "bigint", false),
                column("merchant_order_id", "bigint", false), column("merchant_id_old", "bigint", true)),
                Collections.singletonList("id"),
                Collections.singletonList(index("idx_legacy", "merchant_order_id", false)));
        List<String> executed = new ArrayList<String>();
        DdlExecutionResult result = engine.execute(request(desired), query(current), executed::addAll);

        assertTrue(result.success(), result.errors().toString());
        assertEquals(Collections.singletonList("ALTER TABLE `demo`.`account` DROP INDEX `idx_legacy`, "
                + "ADD COLUMN `merchant_id` bigint NOT NULL, DROP COLUMN `merchant_order_id`, "
                + "DROP COLUMN `merchant_id_old`"), result.generatedSql());
        assertEquals(result.generatedSql(), executed);
        assertEquals(executed, result.executedSql());
    }

    @Test
    @DisplayName("全量同步允许显式重命名、类型收缩和收紧非空约束")
    void shouldAllowDestructiveFieldChangesOnlyInFullMode() {
        DdlFieldMetadata renamed = new DdlFieldMetadata("name", "name", String.class, "", false,
                false, true, false, 40, -1, -1, "", "", "old_name");
        DdlEntityMetadata desired = entity(Collections.emptyList(), id(), renamed);
        DdlTableSnapshot current = snapshot(Arrays.asList(column("id", "bigint", false),
                column("old_name", "varchar(80)", true)), Collections.singletonList("id"), Collections.emptyList());

        DdlExecutionResult result = engine.execute(request(desired), query(current), null);
        assertTrue(result.success(), result.errors().toString());
        assertEquals(Collections.singletonList("ALTER TABLE `demo`.`account` "
                + "CHANGE COLUMN `old_name` `name` varchar(40) NOT NULL"), result.generatedSql());
        assertTrue(result.executedSql().isEmpty());

        DdlExecutionRequest safe = new DdlExecutionRequest("demo", false,
                DdlExecutionMode.CREATE_MODIFY_TABLE_AND_METAS, Collections.singletonList(desired));
        assertFalse(engine.execute(safe, query(current), null).success());
    }

    @Test
    @DisplayName("全量同步重建同名索引及字段唯一索引，保留定义一致的索引")
    void shouldRebuildChangedIndexesAndKeepMatchingIndexes() {
        DdlFieldMetadata email = new DdlFieldMetadata("email", "email", String.class, "", true,
                true, true, false, 200, -1, -1, "", "", "");
        DdlIndexMetadata stable = index("idx_stable", "id", false);
        DdlEntityMetadata desired = entity(Arrays.asList(index("idx_email", "email", true), stable), id(), email);
        DdlTableSnapshot current = snapshot(Arrays.asList(column("id", "bigint", false),
                column("email", "varchar(200)", true)), Collections.singletonList("id"),
                Arrays.asList(index("idx_email", "email", false), stable));

        DdlExecutionResult result = engine.execute(request(desired), query(current), null);
        assertTrue(result.success(), result.errors().toString());
        assertEquals(Collections.singletonList("ALTER TABLE `demo`.`account` DROP INDEX `idx_email`, "
                + "ADD UNIQUE KEY `uk_account_email` (`email`), ADD UNIQUE KEY `idx_email` (`email`)"),
                result.generatedSql());
    }

    @Test
    @DisplayName("主键替换与显式取消旧主键自增合并执行，避免非法中间结构")
    void shouldReplacePrimaryKeyAndRemoveAutoIncrementTogether() {
        DdlFieldMetadata oldId = new DdlFieldMetadata("id", "id", Long.class, "", false, false,
                true, false, -1, -1, -1, "", "", "", GenerationStrategy.NONE);
        DdlFieldMetadata newId = new DdlFieldMetadata("newId", "new_id", Long.class, "", false, false,
                true, true, -1, -1, -1, "", "", "", GenerationStrategy.AUTO_INCREMENT);
        DdlEntityMetadata desired = entity(Collections.emptyList(), oldId, newId);
        DdlTableSnapshot current = snapshot(Collections.singletonList(
                new DdlColumnMetadata("id", "bigint", false, null, "", true)),
                Collections.singletonList("id"), Collections.emptyList());

        DdlExecutionResult result = engine.execute(request(desired), query(current), null);
        assertTrue(result.success(), result.errors().toString());
        assertEquals(Collections.singletonList("ALTER TABLE `demo`.`account` DROP PRIMARY KEY, "
                + "ADD COLUMN `new_id` bigint NOT NULL AUTO_INCREMENT, MODIFY COLUMN `id` bigint NOT NULL, "
                + "ADD PRIMARY KEY (`new_id`)"), result.generatedSql());
    }

    @Test
    @DisplayName("全量同步未声明生成策略时仍保留已有自增")
    void shouldPreserveUnspecifiedAutoIncrement() {
        DdlFieldMetadata renamedId = new DdlFieldMetadata("id", "new_id", Long.class, "", false,
                false, true, true, -1, -1, -1, "", "", "id");
        DdlEntityMetadata desired = entity(Collections.emptyList(), renamedId);
        DdlTableSnapshot current = snapshot(Collections.singletonList(
                new DdlColumnMetadata("id", "bigint", false, null, "", true)),
                Collections.singletonList("id"), Collections.emptyList());

        DdlExecutionResult result = engine.execute(request(desired), query(current), null);
        assertTrue(result.success(), result.errors().toString());
        assertEquals(Collections.singletonList("ALTER TABLE `demo`.`account` DROP PRIMARY KEY, "
                + "CHANGE COLUMN `id` `new_id` bigint NOT NULL AUTO_INCREMENT, ADD PRIMARY KEY (`new_id`)"),
                result.generatedSql());
    }

    @Test
    @DisplayName("全量同步支持建缺失表，无差异时不产生 SQL")
    void shouldCreateMissingTableAndSkipMatchingTable() {
        DdlEntityMetadata desired = entity(Collections.emptyList(), id());
        DdlExecutionResult created = engine.execute(request(desired),
                query(DdlTableSnapshot.missing("demo", "account")), null);
        assertTrue(created.success());
        assertEquals(1, created.generatedSql().size());
        assertTrue(created.generatedSql().get(0).startsWith("CREATE TABLE IF NOT EXISTS `demo`.`account`"));

        DdlExecutionResult unchanged = engine.execute(request(desired), query(snapshot(
                Collections.singletonList(column("id", "bigint", false)),
                Collections.singletonList("id"), Collections.emptyList())), null);
        assertTrue(unchanged.success());
        assertTrue(unchanged.generatedSql().isEmpty());
    }

    @Test
    @DisplayName("任一实体生成失败时不执行其他表的删除计划")
    void shouldNotExecutePartialDestructivePlanWhenMetadataIsInvalid() {
        DdlEntityMetadata valid = entity(Collections.emptyList(), id());
        DdlFieldMetadata invalidId = new DdlFieldMetadata("id", "id", Long.class, "", false, false,
                true, true, -1, -1, -1, "", "", "", GenerationStrategy.SEQUENCE);
        DdlEntityMetadata invalid = new DdlEntityMetadata("Invalid", "", "invalid", "", DdlTableSize.UNSET,
                Collections.singletonList(invalidId), Collections.emptyList());
        DdlExecutionRequest request = new DdlExecutionRequest("demo", false,
                DdlExecutionMode.CREATE_MODIFY_DELETE_ALL, Arrays.asList(valid, invalid));
        List<String> executed = new ArrayList<String>();
        DdlExecutionResult result = engine.execute(request, query(snapshot(Arrays.asList(
                column("id", "bigint", false), column("legacy", "bigint", false)),
                Collections.singletonList("id"), Collections.emptyList())), executed::addAll);

        assertFalse(result.success());
        assertTrue(result.generatedSql().get(0).contains("DROP COLUMN `legacy`"));
        assertTrue(executed.isEmpty());
        assertTrue(result.executedSql().isEmpty());
    }

    private static DdlExecutionRequest request(DdlEntityMetadata entity) {
        return new DdlExecutionRequest("demo", false, DdlExecutionMode.CREATE_MODIFY_DELETE_ALL,
                Collections.singletonList(entity));
    }

    private static QueryStrategy query(DdlTableSnapshot snapshot) {
        return new QueryStrategy() {
            @Override
            public boolean tableExists(String schema, String tableName) {
                return snapshot.exists();
            }

            @Override
            public DdlTableSnapshot readTable(String schema, String tableName) {
                return snapshot;
            }
        };
    }

    private static DdlEntityMetadata entity(List<DdlIndexMetadata> indexes, DdlFieldMetadata... fields) {
        return new DdlEntityMetadata("Account", "", "account", "", DdlTableSize.UNSET,
                Arrays.asList(fields), indexes);
    }

    private static DdlFieldMetadata id() {
        return new DdlFieldMetadata("id", "id", Long.class, "", false, false,
                true, true, -1, -1, -1, "", "", "");
    }

    private static DdlFieldMetadata field(String name, Class<?> type, boolean nullable) {
        return new DdlFieldMetadata(name, name, type, "", nullable, false,
                true, false, -1, -1, -1, "", "", "");
    }

    private static DdlColumnMetadata column(String name, String type, boolean nullable) {
        return new DdlColumnMetadata(name, type, nullable, null, "");
    }

    private static DdlIndexMetadata index(String name, String column, boolean unique) {
        return new DdlIndexMetadata(name, Collections.singletonList(column), unique, "");
    }

    private static DdlTableSnapshot snapshot(List<DdlColumnMetadata> columns, List<String> primaryKeys,
                                              List<DdlIndexMetadata> indexes) {
        return new DdlTableSnapshot(true, "demo", "account", "", columns, primaryKeys, indexes);
    }
}
