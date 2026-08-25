package com.entloom.ddl.spring;

import com.entloom.ddl.api.DdlColumnMetadata;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlExecutionMode;
import com.entloom.ddl.api.DdlExecutionRequest;
import com.entloom.ddl.api.DdlIndexMetadata;
import com.entloom.ddl.api.DdlExecutionResult;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlTableSnapshot;
import com.entloom.ddl.core.DefaultDdlEngine;
import com.entloom.ddl.enums.DdlTableSize;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring JDBC DDL 执行合同测试。
 */
class SpringJdbcExecutionContractTest {

    @Test
    @DisplayName("Spring JDBC 查询和执行成功，并释放连接资源")
    void shouldQueryExecuteAndReleaseConnections() {
        TrackingDataSource dataSource = new TrackingDataSource(h2DataSource("ddl_execution_success"));
        JdbcTemplate setup = new JdbcTemplate(dataSource);
        setup.execute("CREATE TABLE account (id BIGINT PRIMARY KEY)");
        int closedBeforeDdl = dataSource.closedConnections();

        SpringJdbcQueryStrategy queryStrategy = new SpringJdbcQueryStrategy(dataSource);
        SpringJdbcSqlExecutor sqlExecutor = new SpringJdbcSqlExecutor(dataSource);

        assertTrue(queryStrategy.tableExists("PUBLIC", "ACCOUNT"));
        assertFalse(queryStrategy.tableExists("PUBLIC", "missing_table"));
        sqlExecutor.execute(Arrays.asList(
                "CREATE TABLE first_table (id BIGINT PRIMARY KEY)",
                "CREATE TABLE second_table AS SELECT id FROM first_table"));

        assertTrue(queryStrategy.tableExists("PUBLIC", "SECOND_TABLE"));
        assertTrue(dataSource.closedConnections() > closedBeforeDdl);
    }

    @Test
    @DisplayName("Spring JDBC 执行异常向上保留为数据访问异常")
    void shouldPropagateExecutionException() {
        SpringJdbcSqlExecutor sqlExecutor = new SpringJdbcSqlExecutor(
                h2DataSource("ddl_execution_failure"));

        assertThrows(DataAccessException.class,
                () -> sqlExecutor.execute(Collections.singletonList("THIS IS NOT SQL")));
        assertThrows(IllegalArgumentException.class,
                () -> sqlExecutor.execute(Arrays.asList("CREATE TABLE valid_table (id BIGINT)", " ")));
    }

    @Test
    @DisplayName("空 SQL 输入不获取数据库连接")
    void shouldIgnoreEmptyStatements() {
        TrackingDataSource dataSource = new TrackingDataSource(h2DataSource("ddl_execution_empty"));

        new SpringJdbcSqlExecutor(dataSource).execute(Collections.<String>emptyList());
        new SpringJdbcSqlExecutor(dataSource).execute(null);

        assertTrue(dataSource.closedConnections() == 0);
    }

    @Test
    @DisplayName("Spring JDBC 能读取当前表的字段、主键和唯一索引快照")
    void shouldReadCurrentTableSnapshot() {
        DataSource dataSource = h2DataSource("ddl_snapshot");
        JdbcTemplate setup = new JdbcTemplate(dataSource);
        setup.execute("CREATE TABLE account (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(80) NOT NULL)");
        setup.execute("CREATE UNIQUE INDEX uk_account_name ON account(name)");

        DdlTableSnapshot snapshot = new SpringJdbcQueryStrategy(dataSource).readTable("PUBLIC", "account");

        assertTrue(snapshot.exists());
        assertEquals(Arrays.asList("ID", "NAME"), snapshot.columns().stream()
                .map(DdlColumnMetadata::columnName).map(String::toUpperCase).collect(java.util.stream.Collectors.toList()));
        assertEquals(Collections.singletonList("ID"), snapshot.primaryKeyColumns().stream()
                .map(String::toUpperCase).collect(java.util.stream.Collectors.toList()));
        assertEquals(1, snapshot.indexes().size());
        DdlIndexMetadata index = snapshot.indexes().get(0);
        assertEquals("UK_ACCOUNT_NAME", index.name().toUpperCase());
        assertTrue(index.unique());
        assertEquals(Collections.singletonList("NAME"), index.fields().stream()
                .map(String::toUpperCase).collect(java.util.stream.Collectors.toList()));
    }

    @Test
    @DisplayName("Spring JDBC 通过 E3 修改模式实际执行新增字段")
    void shouldExecuteAddedColumnWithCoreEngine() {
        DataSource dataSource = h2DataSource("ddl_modify_column");
        JdbcTemplate setup = new JdbcTemplate(dataSource);
        setup.execute("CREATE TABLE account (id BIGINT NOT NULL PRIMARY KEY)");

        DdlEntityMetadata entity = new DdlEntityMetadata(
                "AccountEntity", "", "account", "", DdlTableSize.SMALL,
                Arrays.asList(
                        new DdlFieldMetadata("id", "id", Long.class, "", false, false, true, true,
                                -1, -1, -1, "", "", ""),
                        new DdlFieldMetadata("nickname", "nickname", String.class, "", true, false, true, false,
                                32, -1, -1, "", "", "")),
                Collections.emptyList());
        SpringJdbcQueryStrategy queryStrategy = new SpringJdbcQueryStrategy(dataSource);
        SpringJdbcSqlExecutor sqlExecutor = new SpringJdbcSqlExecutor(dataSource);

        DdlExecutionResult result = new DefaultDdlEngine().execute(
                new DdlExecutionRequest("", false, DdlExecutionMode.CREATE_MODIFY_TABLE_AND_METAS,
                        Collections.singletonList(entity)),
                queryStrategy, sqlExecutor);

        assertTrue(result.errors().isEmpty(), result.errors().toString());
        assertEquals(Collections.singletonList(
                "ALTER TABLE `account` ADD COLUMN `nickname` varchar(32) NULL"), result.generatedSql());
        assertEquals(result.generatedSql(), result.executedSql());
        assertTrue(queryStrategy.readTable("PUBLIC", "account").columns().stream()
                .map(DdlColumnMetadata::columnName)
                .map(String::toLowerCase)
                .anyMatch("nickname"::equals));
    }

    private static DataSource h2DataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";MODE=MYSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static final class TrackingDataSource extends DelegatingDataSource {
        private final AtomicInteger closedConnections = new AtomicInteger();

        private TrackingDataSource(DataSource targetDataSource) {
            super(targetDataSource);
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection target = super.getConnection();
            AtomicBoolean closed = new AtomicBoolean(false);
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        if ("close".equals(method.getName()) && closed.compareAndSet(false, true)) {
                            closedConnections.incrementAndGet();
                        }
                        try {
                            return method.invoke(target, args);
                        } catch (InvocationTargetException ex) {
                            throw ex.getCause();
                        }
                    });
        }

        private int closedConnections() {
            return closedConnections.get();
        }
    }
}
