package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlCharacterSetMetadata;
import com.entloom.ddl.api.DdlColumnMetadata;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlExecutionMode;
import com.entloom.ddl.api.DdlExecutionRequest;
import com.entloom.ddl.api.DdlExecutionResult;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlIndexMetadata;
import com.entloom.ddl.api.DdlTableSnapshot;
import com.entloom.ddl.api.DdlTextCapacity;
import com.entloom.ddl.api.QueryStrategy;
import com.entloom.ddl.enums.DdlTableSize;
import com.entloom.ddl.enums.GenerationStrategy;
import com.entloom.ddl.enums.SqlType;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 显式文本解析、结构比较、迁移边界与完整计划阻断合同。 */
class MysqlExplicitTextContractTest {
    private static final DdlCharacterSetMetadata UTF8 = new DdlCharacterSetMetadata("utf8mb4", "utf8mb4_bin", 4);
    private static final DdlCharacterSetMetadata LATIN = new DdlCharacterSetMetadata("latin1", "latin1_bin", 1);
    private final MysqlTypeMapper mapper = new MysqlTypeMapper();

    @Test
    void 显式类型按适用参数解析且不会回退() {
        assertEquals("varchar(200)", resolve(SqlType.VARCHAR, -1, -1).sqlType());
        assertEquals("char(1)", resolve(SqlType.CHAR, -1, -1).sqlType());
        assertEquals("char(12)", resolve(SqlType.CHAR, -1, 12).sqlType());
        assertEquals("text", resolve(SqlType.TEXT, -1, 1000).sqlType());
        assertEquals("longtext", resolve(SqlType.LONGTEXT, -1, 100000).sqlType());
        assertTrue(resolve(SqlType.TEXT, -1, 1000).definition().contains("COLLATE utf8mb4_bin"));
        assertEquals("varchar(100)", mapper.resolve(field("content", SqlType.VARCHAR, 100, -1,
                " VARCHAR ( 100 ) ".trim(), ""), UTF8).sqlType());
    }

    @Test
    void 容量边界按最坏编码宽度检查() {
        assertEquals("varchar(16383)", resolve(SqlType.VARCHAR, 16383, -1).sqlType());
        assertThrows(IllegalArgumentException.class, () -> resolve(SqlType.VARCHAR, 16384, -1));
        assertEquals("varchar(65533)", mapper.resolve(field("content", SqlType.VARCHAR, 65533, -1, "", ""), LATIN).sqlType());
        assertThrows(IllegalArgumentException.class, () -> mapper.resolve(field("content", SqlType.VARCHAR, 65534, -1, "", ""), LATIN));
        assertEquals("text", resolve(SqlType.TEXT, -1, 16383).sqlType());
        assertThrows(IllegalArgumentException.class, () -> resolve(SqlType.TEXT, -1, 16384));
        assertThrows(IllegalArgumentException.class, () -> resolve(SqlType.LONGTEXT, -1, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> resolve(SqlType.CHAR, 256, -1));
        assertThrows(IllegalArgumentException.class, () -> resolve(SqlType.VARCHAR, 100, 1000));
    }

    @Test
    void AUTO按业务上限选最小类型且显式物理配置优先() {
        int[] limits = {16383, 16384, 4194303, 4194304};
        String[] types = {"varchar(16383)", "mediumtext", "mediumtext", "longtext"};
        for (int index = 0; index < limits.length; index++) {
            assertEquals(types[index], resolve(SqlType.AUTO, -1, limits[index]).sqlType());
        }
        assertEquals("varchar(65533)", mapper.resolve(field("content", SqlType.AUTO, -1, 65533, "", ""), LATIN).sqlType());
        assertEquals("text", mapper.resolve(field("content", SqlType.AUTO, -1, 65534, "", ""), LATIN).sqlType());
        assertEquals("text", mapper.resolve(field("content", SqlType.AUTO, -1, 65535, "", ""), LATIN).sqlType());
        assertEquals("mediumtext", mapper.resolve(field("content", SqlType.AUTO, -1, 65536, "", ""), LATIN).sqlType());
        assertThrows(IllegalArgumentException.class, () -> resolve(SqlType.AUTO, -1, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> mapper.toSqlType(field("content", SqlType.AUTO, -1, 100, "", "")));
        assertThrows(IllegalArgumentException.class, () -> resolve(SqlType.AUTO, 100, 20000));
        assertEquals("longtext", mapper.resolve(field("content", SqlType.AUTO, -1, 100, "LONGTEXT", ""), UTF8).sqlType());
        DdlFieldMetadata auto = field("content", SqlType.AUTO, -1, 20000, "", "");
        DdlEntityMetadata indexed = new DdlEntityMetadata("Article", "", "article", "", DdlTableSize.UNSET,
                Collections.singletonList(auto), Collections.singletonList(new DdlIndexMetadata("idx_content",
                    Collections.singletonList("content"), false, "")));
        assertThrows(IllegalArgumentException.class, () -> mapper.resolveTable(indexed, null, UTF8));
        assertFalse(new DdlSchemaDiffer().diff(entity(auto), snapshot("varchar(100)", UTF8)).errors().isEmpty());
    }

    @Test
    void 中等文本复用容量参数与原始定义合同() {
        assertEquals("mediumtext", resolve(SqlType.MEDIUMTEXT, -1, 4194303).sqlType());
        assertThrows(IllegalArgumentException.class, () -> resolve(SqlType.MEDIUMTEXT, -1, 4194304));
        assertEquals("mediumtext", mapper.resolve(field("content", SqlType.MEDIUMTEXT, -1, 16777215, "", ""), LATIN).sqlType());
        assertThrows(IllegalArgumentException.class, () -> mapper.resolve(
                field("content", SqlType.MEDIUMTEXT, -1, 16777216, "", ""), LATIN));
        assertThrows(IllegalArgumentException.class, () -> resolve(SqlType.MEDIUMTEXT, 100, -1));
        assertThrows(IllegalArgumentException.class, () -> mapper.toSqlType(
                field("content", SqlType.MEDIUMTEXT, -1, 100000, "", "")));
        assertEquals("mediumtext", mapper.resolve(field("content", SqlType.MEDIUMTEXT, -1, 100000,
                "MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin", ""), UTF8).sqlType());
        assertThrows(IllegalArgumentException.class, () -> mapper.resolve(
                field("content", SqlType.MEDIUMTEXT, -1, -1, "mediumtext(100)", ""), UTF8));
        assertThrows(IllegalArgumentException.class, () -> mapper.resolve(
                field("content", SqlType.TEXT, -1, -1, "mediumtext", ""), UTF8));
        DdlFieldMetadata medium = field("content", SqlType.MEDIUMTEXT, -1, 100000, "", "");
        DdlEntityMetadata indexed = new DdlEntityMetadata("Article", "", "article", "", DdlTableSize.UNSET,
                Collections.singletonList(medium), Collections.singletonList(new DdlIndexMetadata("idx_content",
                    Collections.singletonList("content"), false, "")));
        assertThrows(IllegalArgumentException.class, () -> mapper.resolveTable(indexed, null, UTF8));
        DdlSchemaDiff controlled = new DdlSchemaDiffer().diff(entity(medium), snapshot("text", UTF8));
        assertFalse(controlled.errors().isEmpty());
        assertTrue(new DdlSchemaDiffer().diff(entity(medium), snapshot("text", UTF8), true).errors().isEmpty());
    }

    @Test
    void 覆盖冲突与已知不兼容类型必须报错() {
        assertThrows(IllegalArgumentException.class, () -> mapper.resolve(field("content", SqlType.TEXT, 100, -1, "", ""), UTF8));
        assertThrows(IllegalArgumentException.class, () -> mapper.resolve(field("content", SqlType.TEXT, -1, -1, "varchar(100)", ""), UTF8));
        assertThrows(IllegalArgumentException.class, () -> mapper.resolve(field("content", SqlType.VARCHAR, 100, -1, "varchar(200)", ""), UTF8));
        assertThrows(IllegalArgumentException.class, () -> mapper.resolve(field("content", SqlType.AUTO, -1, -1, "bigint unsigned", ""), UTF8));
        assertThrows(IllegalArgumentException.class, () -> mapper.resolve(field("content", SqlType.AUTO, -1, -1,
                "varchar(100) CHARACTER SET latin1 COLLATE latin1_bin", ""), UTF8));
        assertThrows(IllegalArgumentException.class, () -> mapper.resolve(new DdlFieldMetadata("content", "content", String.class,
                "", false, false, true, false, -1, 10, 2, "", "", "", GenerationStrategy.UNSET,
                SqlType.TEXT, DdlTextCapacity.unspecified()), UTF8));
        assertThrows(IllegalArgumentException.class, () -> mapper.resolve(new DdlFieldMetadata("number", "number", Long.class,
                "", false, false, true, false, -1, -1, -1, "", "", "", GenerationStrategy.UNSET,
                SqlType.TEXT, DdlTextCapacity.unspecified()), UTF8));
    }

    @Test
    void 缺少字符集不能宣称字节容量通过且未知原始定义有有限告警() {
        assertThrows(IllegalArgumentException.class, () -> mapper.toSqlType(field("content", SqlType.VARCHAR, 100, -1, "", "")));
        assertThrows(IllegalArgumentException.class, () -> mapper.toSqlType(field("content", SqlType.TEXT, -1, 1000, "", "")));
        assertEquals("text", mapper.toSqlType(field("content", SqlType.TEXT, -1, -1, "", "")));
        MysqlColumnType unknown = mapper.resolve(field("content", SqlType.AUTO, -1, 1000, "varchar(2000) binary", ""), UTF8);
        assertTrue(unknown.warning().contains("仅有限校验"));
        assertEquals("varchar(2000) binary", unknown.sqlType());
        assertEquals("varchar(2000) binary", unknown.definition());
        assertEquals("varchar(2000) binary CHARACTER SET latin1", mapper.resolve(
                field("content", SqlType.AUTO, -1, 1000, "varchar(2000) binary CHARACTER SET latin1", ""), UTF8).definition());
        assertThrows(IllegalArgumentException.class, () -> mapper.resolveTable(
                entity(field("content", SqlType.AUTO, -1, 1000, "varchar(2000) binary", "")),
                snapshot("varchar(2000)", UTF8), LATIN));
        assertThrows(IllegalArgumentException.class, () -> mapper.resolve(field("content", SqlType.AUTO, -1, 1000,
                "varchar(100) binary", ""), UTF8));
        assertThrows(IllegalArgumentException.class, () -> mapper.resolve(field("content", SqlType.AUTO, -1, -1,
                "varchar(-1)", ""), UTF8));
    }

    @Test
    void 文本索引冲突不得通过删除或截断修复() {
        DdlFieldMetadata text = field("content", SqlType.TEXT, -1, 1000, "", "");
        DdlEntityMetadata indexed = new DdlEntityMetadata("Article", "", "article", "", DdlTableSize.UNSET,
                Collections.singletonList(text), Collections.singletonList(new DdlIndexMetadata("idx_content",
                    Collections.singletonList("content"), false, "")));
        assertThrows(IllegalArgumentException.class, () -> mapper.resolveTable(indexed, null, UTF8));
        for (boolean primary : new boolean[] {true, false}) {
            DdlFieldMetadata constrained = new DdlFieldMetadata("content", "content", String.class, "", false,
                    !primary, true, primary, -1, -1, -1, "", "", "", GenerationStrategy.UNSET,
                    SqlType.TEXT, DdlTextCapacity.unspecified());
            assertThrows(IllegalArgumentException.class, () -> mapper.resolveTable(entity(constrained), null, UTF8));
        }
    }

    @Test
    void 修改重命名与再次比较共用结果并保留原列字符集() {
        DdlFieldMetadata desired = field("renamed", SqlType.VARCHAR, 200, 100, "", "content");
        DdlEntityMetadata entity = entity(desired);
        DdlTableSnapshot current = snapshot("varchar(100)", UTF8);
        MysqlTableResolution resolution = mapper.resolveTable(entity, current, LATIN);
        assertTrue(resolution.column(desired).definition().contains("utf8mb4_bin"));
        DdlSchemaDiff diff = new DdlSchemaDiffer().diff(entity, current, false, resolution);
        assertTrue(diff.errors().isEmpty(), diff.errors().toString());
        assertEquals(1, diff.changedFields().size());
        String sql = new MysqlAlterTableSqlBuilder().buildChangeColumn(entity, "", diff.changedFields().get(0), resolution);
        assertTrue(sql.contains("CHANGE COLUMN `content` `renamed` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin"), sql);
        DdlTableSnapshot after = new DdlTableSnapshot(true, "", "article", "", Collections.singletonList(
                new DdlColumnMetadata("renamed", "varchar(200)", false, null, "", false, false, UTF8)),
                Collections.emptyList(), Collections.emptyList(), LATIN);
        DdlSchemaDiff repeated = new DdlSchemaDiffer().diff(entity, after);
        assertTrue(repeated.errors().isEmpty());
        assertTrue(repeated.changedFields().isEmpty());
    }

    @Test
    void 受控模式不放行跨类别但全量同步仍做容量校验() {
        DdlEntityMetadata entity = entity(field("content", SqlType.TEXT, -1, 1000, "", ""));
        DdlSchemaDiff controlled = new DdlSchemaDiffer().diff(entity, snapshot("varchar(100)", UTF8));
        assertFalse(controlled.errors().isEmpty());
        assertTrue(new DdlSchemaDiffer().diff(entity, snapshot("varchar(100)", UTF8), true).errors().isEmpty());
        DdlEntityMetadata insufficient = entity(field("content", SqlType.TEXT, -1, 20000, "", ""));
        assertThrows(IllegalArgumentException.class, () -> new DdlSchemaDiffer().diff(insufficient, snapshot("text", UTF8), true));
    }

    @Test
    void 新库读取服务器默认值并固定SQL且错误阻止整个计划() {
        AtomicInteger executed = new AtomicInteger();
        AtomicInteger serverReads = new AtomicInteger();
        QueryStrategy query = new QueryStrategy() {
            public boolean tableExists(String schema, String table) { return false; }
            public DdlTableSnapshot readTable(String schema, String table) { return DdlTableSnapshot.missing(schema, table); }
            public DdlCharacterSetMetadata readDatabaseCharacterSet(String schema) { return DdlCharacterSetMetadata.unknown(); }
            public DdlCharacterSetMetadata readServerCharacterSet() { serverReads.incrementAndGet(); return UTF8; }
        };
        DdlEntityMetadata valid = entity(field("content", SqlType.TEXT, -1, 1000, "", ""));
        DdlExecutionResult result = new DefaultDdlEngine().execute(new DdlExecutionRequest("new_schema", true,
                DdlExecutionMode.CREATE_TABLE, Collections.singletonList(valid)), query, null);
        assertTrue(result.success(), result.errors().toString());
        assertTrue(result.generatedSql().get(0).contains("DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin"));
        assertTrue(result.generatedSql().get(1).contains("DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin"));
        DdlExecutionResult rejected = new DefaultDdlEngine().execute(new DdlExecutionRequest("new_schema", true,
                DdlExecutionMode.CREATE_TABLE, Arrays.asList(valid,
                    entity(field("content", SqlType.TEXT, -1, 20000, "", "")))), query, sql -> executed.incrementAndGet());
        assertFalse(rejected.success());
        assertTrue(rejected.errors().get(0).contains("业务最大字符数=20000"));
        assertEquals(0, executed.get());
        assertEquals(2, serverReads.get());
    }

    @Test
    void 字符与枚举沿用AUTO映射并保留已有文本列属性() {
        for (Class<?> type : Arrays.asList(Character.class, TestState.class)) {
            DdlFieldMetadata field = new DdlFieldMetadata("content", "content", type, "", false, false, true, false,
                    -1, -1, -1, "", "", "");
            MysqlColumnType resolved = mapper.resolve(field, UTF8);
            assertEquals(type == Character.class ? "char(1)" : "varchar(64)", resolved.sqlType());
            assertTrue(resolved.definition().contains("COLLATE utf8mb4_bin"));
        }
    }

    private enum TestState {
        /** 启用。 */
        ENABLED
    }

    private MysqlColumnType resolve(SqlType type, int length, int business) {
        return mapper.resolve(field("content", type, length, business, "", ""), UTF8);
    }

    private static DdlFieldMetadata field(String name, SqlType type, int length, int business, String definition, String rename) {
        return new DdlFieldMetadata(name, name, String.class, definition, false, false, true, false,
                length, -1, -1, "", "", rename, GenerationStrategy.UNSET, type,
                new DdlTextCapacity(business, "Article#content.text.maxLength", length, "Article#content@EntDdlField.length"));
    }

    private static DdlEntityMetadata entity(DdlFieldMetadata field) {
        return new DdlEntityMetadata("Article", "", "article", "", DdlTableSize.UNSET,
                Collections.singletonList(field), Collections.emptyList());
    }

    private static DdlTableSnapshot snapshot(String type, DdlCharacterSetMetadata columnCharacterSet) {
        return new DdlTableSnapshot(true, "", "article", "", Collections.singletonList(
                new DdlColumnMetadata("content", type, false, null, "", false, false, columnCharacterSet)),
                Collections.emptyList(), Collections.emptyList(), LATIN);
    }
}
