package com.example.minicommerce.order;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 仅显式连接专用 MySQL 验收库时执行，检查实体自动生成的真实表结构。 */
@EnabledIfEnvironmentVariable(named = "COMMERCE_TEST_JDBC_URL", matches = "jdbc:mysql:.*")
@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=${COMMERCE_TEST_JDBC_URL}",
    "spring.datasource.username=${COMMERCE_TEST_JDBC_USER}",
    "spring.datasource.password=${COMMERCE_TEST_JDBC_PASSWORD}"
})
class MysqlSchemaAcceptanceTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void 统一订单表和明细表匹配实体且使用事务引擎() {
        String version = jdbc.queryForObject("select version()", String.class);
        assertTrue(version.startsWith("8."), version);
        assertEquals(Set.of("id", "customer_id", "merchant_id", "status", "total_amount", "created_at"),
            columns("order"));
        assertEquals(Set.of("id", "order_id", "merchant_product_id", "product_name", "unit_price",
            "quantity", "line_amount"), columns("order_item"));
        assertEquals(Set.of("id", "merchant_id", "name", "price", "active", "version"),
            columns("merchant_product"));
        assertEquals(0, jdbc.queryForObject("""
            select count(*) from information_schema.tables
            where table_schema=database() and table_name='merchant_order'
            """, Integer.class));
        for (String table : List.of("order", "order_item", "merchant_product")) {
            assertEquals("InnoDB", jdbc.queryForObject("""
                select engine from information_schema.tables where table_schema=database() and table_name=?
                """, String.class, table), table);
            assertEquals("auto_increment", jdbc.queryForObject("""
                select extra from information_schema.columns
                where table_schema=database() and table_name=? and column_name='id'
                """, String.class, table), table);
        }
    }

    @Test
    void 金额字段使用十进制定点类型() {
        for (String[] field : List.of(new String[] {"order", "total_amount"},
            new String[] {"order_item", "unit_price"}, new String[] {"order_item", "line_amount"},
            new String[] {"merchant_product", "price"})) {
            assertEquals("decimal", jdbc.queryForObject("""
                select data_type from information_schema.columns
                where table_schema=database() and table_name=? and column_name=?
                """, String.class, field[0], field[1]));
            assertTrue(jdbc.queryForObject("""
                select numeric_scale from information_schema.columns
                where table_schema=database() and table_name=? and column_name=?
                """, Integer.class, field[0], field[1]) >= 2);
        }
    }

    @Test
    void 双端归属和明细关联索引存在() {
        for (String[] index : List.of(new String[] {"order", "idx_order_customer", "customer_id"},
            new String[] {"order", "idx_order_merchant", "merchant_id"},
            new String[] {"order_item", "idx_order_item_order", "order_id"})) {
            assertEquals(List.of(index[2]), jdbc.queryForList("""
                select column_name from information_schema.statistics
                where table_schema=database() and table_name=? and index_name=? order by seq_in_index
                """, String.class, index[0], index[1]));
        }
    }

    private Set<String> columns(String table) {
        return Set.copyOf(jdbc.queryForList("""
            select column_name from information_schema.columns where table_schema=database() and table_name=?
            """, String.class, table));
    }
}
