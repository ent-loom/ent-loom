package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlIndexMetadata;
import com.entloom.ddl.enums.DdlTableSize;
import com.entloom.ddl.enums.GenerationStrategy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MySQL 建表 SQL 合同测试。
 */
class MysqlCreateTableSqlBuilderTest {

    private final MysqlCreateTableSqlBuilder builder = new MysqlCreateTableSqlBuilder();

    @Test
    @DisplayName("同一元数据必须生成稳定且分组有序的建表 SQL")
    void shouldGenerateStableCreateTableSql() {
        DdlEntityMetadata entity = userEntity();

        String first = builder.build(entity, "demo");
        String second = builder.build(userEntity(), "demo");

        assertEquals(first, second);
        assertTrue(first.indexOf("`id` bigint") < first.indexOf("`name` varchar(64)"));
        assertTrue(first.indexOf("PRIMARY KEY (`id`)") < first.indexOf("UNIQUE KEY `uk_user_name`"));
        assertTrue(first.indexOf("UNIQUE KEY `uk_user_name`") < first.indexOf("KEY `idx_created_name`"));
        assertTrue(first.indexOf("KEY `idx_created_name`") < first.indexOf("UNIQUE KEY `idx_auto_"));
        assertTrue(first.contains("DEFAULT 'guest''s' COMMENT '名称 ''用户'"));
        assertTrue(first.contains("`amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '金额'"));
        assertTrue(first.contains("`active` tinyint(1) NOT NULL DEFAULT TRUE"));
        assertTrue(first.contains("`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP"));
        assertTrue(first.contains("COMMENT='用户表 ''主表'"));
    }

    @Test
    @DisplayName("主键、复合主键、字段唯一约束、普通索引和表达式索引语义明确")
    void shouldRenderConstraintKinds() {
        DdlEntityMetadata entity = new DdlEntityMetadata(
                "OrderLine",
                "",
                "order_line",
                "",
                DdlTableSize.UNSET,
                Arrays.asList(
                        field("orderId", "order_id", Long.class, false, false, true),
                        field("lineNo", "line_no", Integer.class, false, false, true),
                        field("code", "code", String.class, false, true, false)),
                Arrays.asList(
                        new DdlIndexMetadata("idx_order_code", Arrays.asList("order_id", "code"), false, ""),
                        new DdlIndexMetadata("uk_lower_code", Arrays.<String>asList(), true, "lower(`code`)")
                ));

        String sql = builder.build(entity, "");

        assertTrue(sql.contains("PRIMARY KEY (`order_id`, `line_no`)"));
        assertTrue(sql.contains("UNIQUE KEY `uk_order_line_code` (`code`)"));
        assertTrue(sql.contains("KEY `idx_order_code` (`order_id`, `code`)"));
        assertTrue(sql.contains("UNIQUE KEY `uk_lower_code` ((lower(`code`)))"));
    }

    @Test
    @DisplayName("标识符中的反引号必须转义")
    void shouldEscapeQuotedIdentifiers() {
        DdlEntityMetadata entity = new DdlEntityMetadata(
                "QuotedEntity",
                "schema`part",
                "order`detail",
                "",
                DdlTableSize.UNSET,
                Arrays.asList(field("id", "column`id", Long.class, false, false, true)),
                Arrays.<DdlIndexMetadata>asList());

        String sql = builder.build(entity, entity.schema());

        assertTrue(sql.startsWith("CREATE TABLE IF NOT EXISTS `schema``part`.`order``detail`"));
        assertTrue(sql.contains("`column``id` bigint NOT NULL"));
    }

    @Test
    @DisplayName("类型映射覆盖常用 Java 类型")
    void shouldMapCommonJavaTypes() {
        MysqlTypeMapper mapper = new MysqlTypeMapper();

        assertEquals("varchar(200)", mapper.toSqlType(field("text", "text", String.class, true, false, false)));
        assertEquals("bigint", mapper.toSqlType(field("longValue", "long_value", Long.class, true, false, false)));
        assertEquals("int", mapper.toSqlType(field("shortValue", "short_value", Short.class, true, false, false)));
        assertEquals("tinyint(1)", mapper.toSqlType(field("enabled", "enabled", Boolean.class, false, false, false)));
        assertEquals("decimal(18,2)", mapper.toSqlType(decimalField()));
        assertEquals("double", mapper.toSqlType(field("ratio", "ratio", Double.class, true, false, false)));
        assertEquals("datetime", mapper.toSqlType(field("createdAt", "created_at", LocalDateTime.class, false, false, false)));
        assertEquals("blob", mapper.toSqlType(field("payload", "payload", byte[].class, true, false, false)));
        assertEquals("varchar(64)", mapper.toSqlType(field("state", "state", SampleState.class, true, false, false)));
    }

    @Test
    @DisplayName("AUTO_INCREMENT 生成策略必须进入 MySQL 列定义")
    void shouldRenderAutoIncrementColumn() {
        DdlFieldMetadata id = new DdlFieldMetadata("id", "id", Long.class, "", false, false,
                true, true, -1, -1, -1, "", "主键", "", GenerationStrategy.AUTO_INCREMENT);
        DdlEntityMetadata entity = new DdlEntityMetadata("Account", "", "account", "",
                DdlTableSize.UNSET, Arrays.asList(id), Arrays.<DdlIndexMetadata>asList());

        assertTrue(builder.build(entity, "").contains("`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键'"));
    }

    private static DdlEntityMetadata userEntity() {
        return new DdlEntityMetadata(
                "UserEntity",
                "demo",
                "user",
                "用户表 '主表",
                DdlTableSize.SMALL,
                Arrays.asList(
                        field("id", "id", Long.class, false, false, true),
                        new DdlFieldMetadata("name", "name", String.class, "", false, true, true, false,
                                64, -1, -1, "guest's", "名称 '用户", ""),
                        new DdlFieldMetadata("amount", "amount", BigDecimal.class, "", false, false, true, false,
                                -1, 18, 2, "0.00", "金额", ""),
                        new DdlFieldMetadata("active", "active", Boolean.class, "", false, false, true, false,
                                -1, -1, -1, "true", "", ""),
                        new DdlFieldMetadata("createdAt", "created_at", LocalDateTime.class, "", false, false, true, false,
                                -1, -1, -1, "CURRENT_TIMESTAMP", "", ""),
                        new DdlFieldMetadata("ignored", "ignored", String.class, "", true, false, false, false,
                                -1, -1, -1, "", "", "")
                ),
                Arrays.asList(
                        new DdlIndexMetadata("idx_created_name", Arrays.asList("created_at", "name"), false, ""),
                        new DdlIndexMetadata("", Arrays.<String>asList(), true, "lower(`name`)")
                ));
    }

    private static DdlFieldMetadata decimalField() {
        return new DdlFieldMetadata("amount", "amount", BigDecimal.class, "", true, false, true, false,
                -1, 18, 2, "", "", "");
    }

    private static DdlFieldMetadata field(String fieldName,
                                          String columnName,
                                          Class<?> javaType,
                                          boolean nullable,
                                          boolean unique,
                                          boolean primaryKey) {
        return new DdlFieldMetadata(fieldName, columnName, javaType, "", nullable, unique, true, primaryKey,
                -1, -1, -1, "", "", "");
    }

    private enum SampleState {
        READY
    }
}
