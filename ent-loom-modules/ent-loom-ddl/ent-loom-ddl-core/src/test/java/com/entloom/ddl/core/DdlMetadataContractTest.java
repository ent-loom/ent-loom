package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlIndexMetadata;
import com.entloom.ddl.enums.DdlTableSize;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DDL Runtime Model 输入合同测试。
 */
class DdlMetadataContractTest {

    @Test
    @DisplayName("字段必填项和数值边界必须明确拒绝非法输入")
    void shouldRejectIllegalFieldInput() {
        assertThrows(NullPointerException.class, () -> field("id", "id", null, false, false, true));
        assertThrows(IllegalArgumentException.class, () -> new DdlFieldMetadata(
                "id", "id", Long.class, "", false, false, true, true,
                -2, -1, -1, "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> new DdlFieldMetadata(
                "id", "id", Long.class, "", true, false, true, true,
                -1, -1, -1, "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> new DdlFieldMetadata(
                "id", "id", Long.class, "", false, false, false, true,
                -1, -1, -1, "", "", ""));
    }

    @Test
    @DisplayName("索引必须有列或表达式，且列不能重复")
    void shouldRejectIllegalIndexInput() {
        assertThrows(IllegalArgumentException.class, () -> new DdlIndexMetadata(
                "idx_empty", Collections.<String>emptyList(), false, ""));
        assertThrows(IllegalArgumentException.class, () -> new DdlIndexMetadata(
                "idx_blank", Arrays.asList("id", " "), false, ""));
        assertThrows(IllegalArgumentException.class, () -> new DdlIndexMetadata(
                "idx_duplicate", Arrays.asList("id", "id"), false, ""));
    }

    @Test
    @DisplayName("实体必须有持久化字段，并拒绝重复列和未知索引列")
    void shouldRejectIllegalEntityInput() {
        assertThrows(IllegalArgumentException.class, () -> new DdlEntityMetadata(
                "EmptyEntity", "", "empty_table", "", DdlTableSize.UNSET,
                Collections.<DdlFieldMetadata>emptyList(), Collections.<DdlIndexMetadata>emptyList()));
        assertThrows(IllegalArgumentException.class, () -> new DdlEntityMetadata(
                "DuplicateEntity", "", "duplicate_table", "", DdlTableSize.UNSET,
                Arrays.asList(field("id", "same_column"), field("name", "same_column")),
                Collections.<DdlIndexMetadata>emptyList()));
        assertThrows(IllegalArgumentException.class, () -> new DdlEntityMetadata(
                "UnknownIndexEntity", "", "unknown_index_table", "", DdlTableSize.UNSET,
                Collections.singletonList(field("id", "id")),
                Collections.singletonList(new DdlIndexMetadata("idx_unknown", Collections.singletonList("missing"), false, ""))));
    }

    @Test
    @DisplayName("元数据集合保持不可变且保留声明顺序")
    void shouldKeepImmutableDeclarationOrder() {
        ArrayList<DdlFieldMetadata> fields = new ArrayList<DdlFieldMetadata>(Arrays.asList(
                field("first", "first"), field("second", "second")));
        DdlEntityMetadata entity = new DdlEntityMetadata(
                "OrderedEntity", "", "ordered_table", "", DdlTableSize.UNSET, fields,
                Collections.<DdlIndexMetadata>emptyList());
        fields.clear();

        assertEquals("first", entity.fields().get(0).fieldName());
        assertThrows(UnsupportedOperationException.class, () -> entity.fields().add(field("third", "third")));
    }

    private static DdlFieldMetadata field(String fieldName, String columnName) {
        return field(fieldName, columnName, Long.class, false, false, true);
    }

    private static DdlFieldMetadata field(String fieldName,
                                          String columnName,
                                          Class<?> type,
                                          boolean nullable,
                                          boolean unique,
                                          boolean primaryKey) {
        return new DdlFieldMetadata(fieldName, columnName, type, "", nullable, unique, true, primaryKey,
                -1, -1, -1, "", "", "");
    }
}
