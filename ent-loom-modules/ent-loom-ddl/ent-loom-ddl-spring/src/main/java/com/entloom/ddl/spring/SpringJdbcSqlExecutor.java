package com.entloom.ddl.spring;

import com.entloom.ddl.api.SqlExecutor;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 基于 Spring JDBC 的 SQL 执行器。
 *
 * <p>语句按输入顺序逐条执行；Spring JDBC 产生的运行时数据访问异常原样向上
 * 抛出，由 DDL Core 负责归类到执行结果。每条语句的 JDBC 资源由
 * {@link JdbcTemplate} 管理。</p>
 */
public final class SpringJdbcSqlExecutor implements SqlExecutor {
    private final JdbcTemplate jdbcTemplate;

    public SpringJdbcSqlExecutor(DataSource dataSource) {
        this(new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null")));
    }

    public SpringJdbcSqlExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public void execute(List<String> sqlStatements) {
        if (sqlStatements == null || sqlStatements.isEmpty()) {
            return;
        }
        for (String sqlStatement : sqlStatements) {
            if (sqlStatement == null || sqlStatement.trim().isEmpty()) {
                throw new IllegalArgumentException("sqlStatements must not contain blank statement");
            }
        }
        for (String sqlStatement : sqlStatements) {
            jdbcTemplate.execute(sqlStatement.trim());
        }
    }
}
