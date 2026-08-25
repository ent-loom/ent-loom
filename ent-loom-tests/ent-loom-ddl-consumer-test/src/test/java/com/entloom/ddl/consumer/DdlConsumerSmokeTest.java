package com.entloom.ddl.consumer;

import com.entloom.ddl.annotations.EntDbEntity;
import com.entloom.ddl.annotations.EntDbField;
import com.entloom.ddl.api.DdlExecutionMode;
import com.entloom.ddl.api.DdlExecutionResult;
import com.entloom.ddl.api.SqlExecutor;
import com.entloom.ddl.bootstrap.DdlBootstrap;
import com.entloom.ddl.bootstrap.DdlBootstrapRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DDL 公开构件消费者冒烟测试。
 *
 * <p>该测试模拟业务项目，只使用公开注解、Bootstrap 入口和 API 接口，
 * 不直接引用 DDL Core 实现。</p>
 */
class DdlConsumerSmokeTest {

    @Test
    @DisplayName("消费者只使用公开注解和 API 即可生成并执行最小建表 SQL")
    void shouldBuildAndExecuteDdlFromPublicContract() {
        List<String> executedSql = new ArrayList<String>();
        SqlExecutor sqlExecutor = statements -> executedSql.addAll(statements);
        DdlBootstrap bootstrap = new DdlBootstrap(
                null,
                null,
                (schema, tableName) -> false,
                sqlExecutor);

        DdlExecutionResult result = bootstrap.execute(new DdlBootstrapRequest(
                "consumer_schema",
                false,
                DdlExecutionMode.CREATE_TABLE,
                Collections.<String>emptyList(),
                Collections.<Class<?>>singletonList(ConsumerAccount.class)));

        assertTrue(result.success());
        assertFalse(result.generatedSql().isEmpty());
        assertEquals(result.generatedSql(), result.executedSql());
        assertEquals(result.generatedSql(), executedSql);
        String createTableSql = result.generatedSql().get(0);
        assertTrue(createTableSql.contains("`consumer_account`"));
        assertTrue(createTableSql.contains("`id` bigint"));
        assertTrue(createTableSql.contains("`display_name` varchar(64)"));
    }

    /** 业务侧实体只依赖 DDL 注解，不继承框架实现类。 */
    @EntDbEntity(table = "consumer_account", comment = "消费者冒烟实体")
    static final class ConsumerAccount {
        @EntDbField(comment = "主键")
        private Long id;

        @EntDbField(length = 64, comment = "展示名称")
        private String displayName;
    }
}
