package com.entloom.crud.engine.jdbc.security;

import com.entloom.crud.core.exception.EntityDaoPersistenceException;
import com.entloom.crud.core.runtime.context.CrudExecutionContext;
import com.entloom.crud.core.runtime.context.DefaultExecutionContext;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.log.SqlExecutionLogger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;
import static org.junit.jupiter.api.Assertions.*;

/** 验证生成键执行失败时的合同，防止静默写入或伪成功。 */
class GeneratedKeyExecutionTest {
    @Test
    void unsupportedExecutorMustRejectBeforeWriting() {
        GuardedSqlExecutor executor = new GuardedSqlExecutor() {
            public List<Map<String, Object>> queryForList(String sql, List<Object> args, CrudExecutionContext ctx) { throw new AssertionError(); }
            public Map<String, Object> queryForMap(String sql, List<Object> args, CrudExecutionContext ctx) { throw new AssertionError(); }
            public Object queryForObject(String sql, List<Object> args, CrudExecutionContext ctx) { throw new AssertionError(); }
            public int update(String sql, List<Object> args, CrudExecutionContext ctx) {
                fail("不支持生成键的执行器不得发生写入");
                return 1;
            }
        };
        assertThrows(UnsupportedOperationException.class,
            () -> executor.insertAndReturnGeneratedKey("insert into t values (?)", Collections.singletonList(1), context()));
    }

    @Test
    void shouldRequireOneAffectedRowAndOneNonNullKey() {
        assertEquals(7L, execute(1, Collections.singletonList(Collections.singletonMap("id", 7L))));
        assertThrows(EntityDaoPersistenceException.class, () -> execute(0, Collections.singletonList(Collections.singletonMap("id", 7L))));
        assertThrows(EntityDaoPersistenceException.class, () -> execute(2, Collections.singletonList(Collections.singletonMap("id", 7L))));
        assertThrows(EntityDaoPersistenceException.class, () -> execute(1, Collections.emptyList()));
        assertThrows(EntityDaoPersistenceException.class, () -> execute(1, Collections.singletonList(Collections.singletonMap("id", null))));
        assertThrows(EntityDaoPersistenceException.class, () -> execute(1, java.util.Arrays.asList(
            Collections.singletonMap("id", 7L), Collections.singletonMap("id", 8L))));
        Map<String, Object> ambiguous = new java.util.LinkedHashMap<String, Object>();
        ambiguous.put("id", 7L);
        ambiguous.put("other", 8L);
        assertThrows(EntityDaoPersistenceException.class, () -> execute(1, Collections.singletonList(ambiguous)));
    }

    private Object execute(int count, List<Map<String, Object>> keys) {
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override public int update(PreparedStatementCreator creator, KeyHolder holder) {
                holder.getKeyList().addAll(keys);
                return count;
            }
        };
        JdbcGuardedSqlExecutor executor = new JdbcGuardedSqlExecutor(jdbc,
            new SqlSafetyGuard(null, new SqlParameterLimiter()), new SqlExecutionLogger());
        return executor.insertAndReturnGeneratedKey("insert into t values (?)", Collections.singletonList(1), context());
    }

    private DefaultExecutionContext context() { return new DefaultExecutionContext("生成键测试", null); }
}
