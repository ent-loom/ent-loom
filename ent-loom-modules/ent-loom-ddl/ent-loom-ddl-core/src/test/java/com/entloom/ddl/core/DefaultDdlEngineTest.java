package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlExecutionMode;
import com.entloom.ddl.api.DdlExecutionRequest;
import com.entloom.ddl.api.DdlExecutionResult;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.QueryStrategy;
import com.entloom.ddl.api.SqlExecutor;
import com.entloom.ddl.enums.DdlTableSize;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DDL Core 执行编排测试。
 */
class DefaultDdlEngineTest {

    private final DefaultDdlEngine engine = new DefaultDdlEngine();

    @Test
    @DisplayName("生成模式只返回稳定 SQL，不伪造已执行结果")
    void shouldSeparateGeneratedAndExecutedInGenerateMode() {
        DdlExecutionResult result = engine.execute(
                request(DdlExecutionMode.CREATE_TABLE, entity("account")),
                (schema, table) -> false,
                null);

        assertEquals(1, result.generatedSql().size());
        assertTrue(result.executedSql().isEmpty());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.success());
    }

    @Test
    @DisplayName("显式 Noop 执行器只能产生 dry-run 结果")
    void shouldNotReportNoopAsExecuted() {
        DdlExecutionResult result = engine.execute(
                request(DdlExecutionMode.CREATE_TABLE, entity("account")),
                new NoopQueryStrategy(),
                new NoopSqlExecutor());

        assertFalse(result.generatedSql().isEmpty());
        assertTrue(result.executedSql().isEmpty());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("执行模式按数据库、表和实体声明顺序分类结果")
    void shouldExecuteGeneratedSqlInStableOrder() {
        RecordingExecutor executor = new RecordingExecutor();
        QueryStrategy queryStrategy = (schema, table) -> "existing".equals(table);
        DdlExecutionRequest request = new DdlExecutionRequest(
                "demo", true, DdlExecutionMode.CREATE_TABLE_AND_METAS,
                Arrays.asList(entity("first"), entity("existing"), entity("last")));

        DdlExecutionResult result = engine.execute(request, queryStrategy, executor);

        assertEquals(3, result.generatedSql().size());
        assertEquals("CREATE DATABASE IF NOT EXISTS `demo`", result.generatedSql().get(0));
        assertTrue(result.generatedSql().get(1).contains("`first`"));
        assertTrue(result.generatedSql().get(2).contains("`last`"));
        assertEquals(result.generatedSql(), result.executedSql());
        assertEquals(result.generatedSql(), executor.statements);
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("空实体输入不查询、不执行且返回空成功结果")
    void shouldHandleEmptyInput() {
        RecordingQueryStrategy queryStrategy = new RecordingQueryStrategy();
        RecordingExecutor executor = new RecordingExecutor();

        DdlExecutionResult result = engine.execute(
                request(DdlExecutionMode.CREATE_TABLE, Collections.<DdlEntityMetadata>emptyList()),
                queryStrategy,
                executor);

        assertTrue(result.generatedSql().isEmpty());
        assertTrue(result.executedSql().isEmpty());
        assertTrue(result.errors().isEmpty());
        assertEquals(0, queryStrategy.calls);
        assertTrue(executor.statements.isEmpty());
    }

    @Test
    @DisplayName("执行器异常进入 errors，generated 保留而 executed 为空")
    void shouldClassifyExecutorException() {
        SqlExecutor failingExecutor = statements -> {
            throw new IllegalStateException("数据库连接失败");
        };

        DdlExecutionResult result = engine.execute(
                request(DdlExecutionMode.CREATE_TABLE, entity("account")),
                (schema, table) -> false,
                failingExecutor);

        assertFalse(result.generatedSql().isEmpty());
        assertTrue(result.executedSql().isEmpty());
        assertEquals(Collections.singletonList("SQL 执行失败: 数据库连接失败"), result.errors());
        assertFalse(result.success());
    }

    @Test
    @DisplayName("逐条执行遇到异常时保留已确认执行的 SQL")
    void shouldPreserveCompletedStatementsWhenLaterStatementFails() {
        List<String> completed = new ArrayList<String>();
        SqlExecutor partiallyFailingExecutor = statements -> {
            if (completed.size() == 1) {
                throw new IllegalStateException("第二条 SQL 失败");
            }
            completed.addAll(statements);
        };

        DdlExecutionResult result = engine.execute(
                request(DdlExecutionMode.CREATE_TABLE,
                        Arrays.asList(entity("first"), entity("second"))),
                (schema, table) -> false,
                partiallyFailingExecutor);

        assertEquals(2, result.generatedSql().size());
        assertEquals(1, result.executedSql().size());
        assertEquals(completed, result.executedSql());
        assertTrue(result.errors().get(0).contains("已确认执行 1 条"));
    }

    @Test
    @DisplayName("查询异常阻止不完整计划执行并保留错误上下文")
    void shouldClassifyQueryException() {
        RecordingExecutor executor = new RecordingExecutor();
        DdlExecutionResult result = engine.execute(
                request(DdlExecutionMode.CREATE_TABLE, entity("account")),
                (schema, table) -> {
                    throw new IllegalStateException("查询不可用");
                },
                executor);

        assertEquals(Collections.singletonList("表 account 处理失败: 查询不可用"), result.errors());
        assertTrue(result.executedSql().isEmpty());
        assertTrue(executor.statements.isEmpty());
    }

    @Test
    @DisplayName("E3 明确拒绝删除模式")
    void shouldRejectDeleteModeOutsideE3() {
        DdlExecutionResult result = engine.execute(
                request(DdlExecutionMode.CREATE_MODIFY_DELETE_ALL, entity("account")),
                (schema, table) -> false,
                new RecordingExecutor());

        assertTrue(result.generatedSql().isEmpty());
        assertTrue(result.executedSql().isEmpty());
        assertEquals(Collections.singletonList("E3 不支持执行模式: CREATE_MODIFY_DELETE_ALL"), result.errors());
    }

    @Test
    @DisplayName("NONE 模式和空请求分别保持 dry-run 与非法请求边界")
    void shouldHandleNoneAndNullRequest() {
        DdlExecutionResult result = engine.execute(
                request(DdlExecutionMode.NONE, entity("account")),
                null,
                null);

        assertTrue(result.success());
        assertTrue(result.generatedSql().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> engine.execute(null, null, null));
    }

    private static DdlExecutionRequest request(DdlExecutionMode mode, DdlEntityMetadata entity) {
        return request(mode, Collections.singletonList(entity));
    }

    private static DdlExecutionRequest request(DdlExecutionMode mode, List<DdlEntityMetadata> entities) {
        return new DdlExecutionRequest("", false, mode, entities);
    }

    private static DdlEntityMetadata entity(String tableName) {
        return new DdlEntityMetadata(
                tableName + "Entity", "", tableName, "", DdlTableSize.UNSET,
                Collections.singletonList(new DdlFieldMetadata(
                        "id", "id", Long.class, "", false, false, true, true,
                        -1, -1, -1, "", "", "")),
                Collections.emptyList());
    }

    private static final class RecordingExecutor implements SqlExecutor {
        private List<String> statements = new ArrayList<String>();

        @Override
        public void execute(List<String> sqlStatements) {
            statements.addAll(sqlStatements);
        }
    }

    private static final class RecordingQueryStrategy implements QueryStrategy {
        private int calls;

        @Override
        public boolean tableExists(String schema, String tableName) {
            calls++;
            return false;
        }
    }
}
