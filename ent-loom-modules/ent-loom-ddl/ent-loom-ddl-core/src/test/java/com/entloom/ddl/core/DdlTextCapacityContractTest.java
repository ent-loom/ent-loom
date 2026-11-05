package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlCharacterSetMetadata;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlExecutionMode;
import com.entloom.ddl.api.DdlExecutionRequest;
import com.entloom.ddl.api.DdlExecutionResult;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlTextCapacity;
import com.entloom.ddl.enums.GenerationStrategy;
import com.entloom.ddl.enums.SqlType;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 文本容量模型与计划阻断合同。 */
class DdlTextCapacityContractTest {
    @Test
    void 字符集上下文必须完整且不能拼接非法标识符() {
        assertFalse(DdlCharacterSetMetadata.unknown().known());
        assertEquals(4, new DdlCharacterSetMetadata("utf8mb4", "utf8mb4_bin", 4).maxBytesPerCharacter());
        assertThrows(IllegalArgumentException.class, () -> new DdlCharacterSetMetadata("utf8mb4", "", 4));
        assertThrows(IllegalArgumentException.class, () -> new DdlCharacterSetMetadata("utf8mb4", "utf8mb4_bin", 0));
        assertThrows(IllegalArgumentException.class, () -> new DdlCharacterSetMetadata("utf8mb4;", "utf8mb4_bin", 4));
    }

    @Test
    void 未声明容量不能被默认长度反写() {
        DdlFieldMetadata field = field(SqlType.AUTO, "", -1, DdlTextCapacity.unspecified());
        assertEquals("varchar(200)", new MysqlTypeMapper().toSqlType(field));
        assertEquals(-1, field.textCapacity().businessMaxLength());
        assertEquals(-1, field.textCapacity().physicalLength());
    }

    @Test
    void 非法容量与缺失来源应拒绝() {
        for (int length : new int[] {-2, 0}) {
            assertThrows(IllegalArgumentException.class, () -> new DdlTextCapacity(length, "meta", -1, ""));
            assertThrows(IllegalArgumentException.class, () -> new DdlTextCapacity(-1, "", length, "ddl"));
        }
        assertThrows(IllegalArgumentException.class, () -> new DdlTextCapacity(10, "", -1, ""));
        assertThrows(IllegalArgumentException.class, () -> new DdlTextCapacity(-1, "", 10, ""));
    }

    @Test
    void 物理长度覆盖不能隐藏业务容量不足() {
        DdlTextCapacity capacity = new DdlTextCapacity(1000, "Article#content.text.maxLength", 100,
                "Article#content@EntDdlField.length");
        for (String definition : new String[] {"", "VARCHAR(100)", "CHAR ( 100 )"}) {
            DdlFieldMetadata field = field(SqlType.AUTO, definition, 100, capacity);
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> new MysqlTypeMapper().toSqlType(field));
            assertTrue(error.getMessage().contains("1000"));
            assertTrue(error.getMessage().contains("text.maxLength"));
            assertThrows(IllegalArgumentException.class,
                    () -> new MysqlCreateTableSqlBuilder().build(entity("article", field), ""));
            assertThrows(IllegalArgumentException.class,
                    () -> new MysqlAlterTableSqlBuilder().buildAddColumn(entity("article", field), "", field));
        }
    }

    @Test
    void 未支持显式类型不能回退且不能执行部分计划() {
        AtomicInteger calls = new AtomicInteger();
        for (SqlType type : SqlType.values()) {
            if (type == SqlType.AUTO || type == SqlType.VARCHAR || type == SqlType.CHAR
                    || type == SqlType.TEXT || type == SqlType.MEDIUMTEXT || type == SqlType.LONGTEXT) { continue; }
            for (String definition : new String[] {"", "varchar(100)"}) {
                DdlFieldMetadata field = field(type, definition, 100, DdlTextCapacity.unspecified());
                DdlExecutionResult result = new DefaultDdlEngine().execute(new DdlExecutionRequest("", false,
                        DdlExecutionMode.CREATE_TABLE, Arrays.asList(
                            entity("valid", field(SqlType.AUTO, "", 100, DdlTextCapacity.unspecified())),
                            entity("invalid", field))), new com.entloom.ddl.api.QueryStrategy() {
                            public boolean tableExists(String schema, String table) { return false; }
                            public com.entloom.ddl.api.DdlTableSnapshot readTable(String schema, String table) {
                                return com.entloom.ddl.api.DdlTableSnapshot.missing(schema, table);
                            }
                            public DdlCharacterSetMetadata readDatabaseCharacterSet(String schema) {
                                return new DdlCharacterSetMetadata("utf8mb4", "utf8mb4_bin", 4);
                            }
                        },
                        sql -> calls.incrementAndGet());
                assertFalse(result.success());
                assertTrue(result.errors().get(0).contains("sqlType=" + type));
                assertTrue(result.executedSql().isEmpty());
            }
        }
        assertEquals(0, calls.get());
    }

    private static DdlFieldMetadata field(SqlType type, String definition, int length, DdlTextCapacity capacity) {
        return new DdlFieldMetadata("content", "content", String.class, definition, false, false, true, false,
                length, -1, -1, "", "", "", GenerationStrategy.UNSET, type, capacity);
    }

    private static DdlEntityMetadata entity(String table, DdlFieldMetadata field) {
        return new DdlEntityMetadata("Article", "", table, "", com.entloom.ddl.enums.DdlTableSize.UNSET,
                Collections.singletonList(field), Collections.emptyList());
    }
}
