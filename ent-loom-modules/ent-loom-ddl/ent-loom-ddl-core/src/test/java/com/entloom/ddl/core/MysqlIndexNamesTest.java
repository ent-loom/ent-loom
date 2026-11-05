package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlColumnMetadata;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlIndexMetadata;
import com.entloom.ddl.api.DdlTableSnapshot;
import com.entloom.ddl.enums.DdlTableSize;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 自动命名、MySQL 长度边界及建表/迁移一致性。 */
class MysqlIndexNamesTest {
    @Test
    void shouldNamePhysicalColumnsInDeclaredOrder() {
        assertEquals("idx_commerce_order_merchant_id_status", MysqlIndexNames.resolve("commerce_order",
                index(false, "merchant_id", "status")));
        assertEquals("uk_commerce_order_status_merchant_id", MysqlIndexNames.resolve("commerce_order",
                index(true, "status", "merchant_id")));
    }

    @Test
    void shouldShortenOnlyNamesBeyond64WithSha256() throws Exception {
        String table = repeat('a', 58);
        assertEquals(64, MysqlIndexNames.resolve(table, index(false, "x")).length());
        String name = MysqlIndexNames.resolve(table, index(false, "xy"));
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(("58:" + table + "5:false2:xy0:").getBytes(StandardCharsets.UTF_8));
        StringBuilder suffix = new StringBuilder();
        for (int i = 0; i < 4; i++) suffix.append(String.format("%02x", hash[i] & 255));
        assertEquals(("idx_" + table).substring(0, 55) + "_" + suffix, name);
        assertEquals(64, name.length());
        assertNotEquals(name, MysqlIndexNames.resolve(table, index(false, "xz")));
        assertNotEquals(name, MysqlIndexNames.resolve(table, index(true, "xy")));
        assertEquals(64, MysqlCreateTableSqlBuilder.fieldUniqueIndexName(table, "long_column").length());
    }

    @Test
    void shouldHandleExpressionsAndExplicitNames() {
        DdlIndexMetadata expression = new DdlIndexMetadata("", Collections.<String>emptyList(), true, "lower(code)");
        String name = MysqlIndexNames.resolve("account", expression);
        assertTrue(name.matches("uk_account_expr_[0-9a-f]{8}"));
        assertEquals(name, MysqlIndexNames.resolve("account", expression));
        assertNotEquals(name, MysqlIndexNames.resolve("account", new DdlIndexMetadata("",
                Collections.<String>emptyList(), true, "upper(code)")));
        assertEquals("custom", MysqlIndexNames.resolve("account", new DdlIndexMetadata("custom",
                Arrays.asList("code"), false, "")));
        assertThrows(IllegalArgumentException.class, () -> MysqlIndexNames.resolve("account",
                new DdlIndexMetadata(repeat('a', 65), Arrays.asList("code"), false, "")));
    }

    @Test
    void shouldRejectGeneratedAndExplicitNameCollisionBeforeSql() {
        DdlIndexMetadata automatic = index(false, "code");
        DdlEntityMetadata entity = entity(automatic, new DdlIndexMetadata("IDX_ACCOUNT_CODE",
                Arrays.asList("code"), false, ""));
        assertThrows(IllegalArgumentException.class, () -> new MysqlCreateTableSqlBuilder().build(entity, ""));
        assertThrows(IllegalArgumentException.class, () -> new DdlSchemaDiffer().diff(entity,
                DdlTableSnapshot.missing("", "account")));
    }

    @Test
    void shouldUseSameNameForCreateAlterAndSubsequentDiff() {
        DdlIndexMetadata automatic = new DdlIndexMetadata("", Collections.<String>emptyList(), false, "lower(code)");
        DdlEntityMetadata entity = entity(automatic);
        String name = MysqlIndexNames.resolve("account", automatic);
        String fragment = "KEY `" + name + "` ((lower(code)))";
        assertTrue(new MysqlCreateTableSqlBuilder().build(entity, "").contains(fragment));
        assertTrue(new MysqlAlterTableSqlBuilder().buildAddIndex(entity, "", automatic).contains(fragment));
        DdlTableSnapshot snapshot = new DdlTableSnapshot(true, "", "account", "", Arrays.asList(new DdlColumnMetadata("code", "bigint", false, null, "")),
                Collections.emptyList(), Arrays.asList(new DdlIndexMetadata(name, Collections.emptyList(), false, "lower(code)")));
        DdlSchemaDiff diff = new DdlSchemaDiffer().diff(entity, snapshot, true);
        assertTrue(diff.errors().isEmpty());
        assertTrue(diff.addedIndexes().isEmpty());
        assertTrue(diff.removedIndexes().isEmpty());
    }

    private static DdlIndexMetadata index(boolean unique, String... columns) {
        return new DdlIndexMetadata("", Arrays.asList(columns), unique, "");
    }

    private static DdlEntityMetadata entity(DdlIndexMetadata... indexes) {
        return new DdlEntityMetadata("Account", "", "account", "", DdlTableSize.UNSET,
                Arrays.asList(new DdlFieldMetadata("code", "code", Long.class, "", false, false, true, false,
                        -1, -1, -1, "", "", "")), Arrays.asList(indexes));
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }
}
