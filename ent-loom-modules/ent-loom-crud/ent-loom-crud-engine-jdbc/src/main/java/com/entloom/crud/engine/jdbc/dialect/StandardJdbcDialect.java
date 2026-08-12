package com.entloom.crud.engine.jdbc.dialect;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

/**
 * 内置主流数据库方言枚举。
 */
public enum StandardJdbcDialect implements JdbcDialect {
    /**
     * MySQL/MariaDB 方言。
     */
    MYSQL,
    /**
     * PostgreSQL 方言。
     */
    POSTGRESQL,
    /**
     * Oracle 方言（基于 12c+ OFFSET/FETCH）。
     */
    ORACLE,
    /**
     * SQL Server 方言（基于 2012+ OFFSET/FETCH）。
     */
    SQL_SERVER,
    /**
     * H2 方言。
     */
    H2,
    /**
     * 未识别数据库的兜底方言（按 MySQL 语法回退）。
     */
    GENERIC;

    /**
     * 追加分页子句并绑定参数。
     */
    @Override
    public void appendPageClause(StringBuilder sql, int limit, int offset, List<Object> args) {
        if (this == ORACLE || this == SQL_SERVER) {
            sql.append(" offset ? rows fetch next ? rows only");
            args.add(offset);
            args.add(limit);
            return;
        }
        sql.append(" limit ? offset ?");
        args.add(limit);
        args.add(offset);
    }

    @Override
    public void appendListClause(StringBuilder sql, int limit, List<Object> args) {
        if (this == ORACLE || this == SQL_SERVER) {
            sql.append(" offset 0 rows fetch next ? rows only");
            args.add(limit);
            return;
        }
        sql.append(" limit ?");
        args.add(limit);
    }

    @Override
    public void appendFindOneClause(StringBuilder sql, List<Object> args) {
        if (this == ORACLE || this == SQL_SERVER) {
            sql.append(" offset 0 rows fetch next 2 rows only");
            return;
        }
        sql.append(" limit 2");
    }

    @Override
    public void appendDetailClause(StringBuilder sql) {
        if (this == ORACLE || this == SQL_SERVER) {
            sql.append(" offset 0 rows fetch next 2 rows only");
            return;
        }
        sql.append(" limit 2");
    }

    /**
     * 生成幂等表的建表 SQL。
     */
    @Override
    public String createIdempotencyTableSql(String tableName) {
        switch (this) {
            case MYSQL:
                return "create table if not exists " + tableName + " (" +
                    "storage_key_hash varchar(64) primary key," +
                    "storage_key varchar(512) not null," +
                    "payload_hash varchar(64) not null," +
                    "status varchar(32) not null," +
                    "result_body longtext null," +
                    "expires_at datetime not null," +
                    "created_at datetime not null," +
                    "updated_at datetime not null" +
                    ")";
            case POSTGRESQL:
                return "create table if not exists " + tableName + " (" +
                    "storage_key_hash varchar(64) primary key," +
                    "storage_key varchar(512) not null," +
                    "payload_hash varchar(64) not null," +
                    "status varchar(32) not null," +
                    "result_body text null," +
                    "expires_at timestamp not null," +
                    "created_at timestamp not null," +
                    "updated_at timestamp not null" +
                    ")";
            case ORACLE:
                return "create table " + tableName + " (" +
                    "storage_key_hash varchar2(64) primary key," +
                    "storage_key varchar2(512) not null," +
                    "payload_hash varchar2(64) not null," +
                    "status varchar2(32) not null," +
                    "result_body clob null," +
                    "expires_at timestamp not null," +
                    "created_at timestamp not null," +
                    "updated_at timestamp not null" +
                    ")";
            case SQL_SERVER:
                return "if object_id('" + tableName + "', 'U') is null " +
                    "create table " + tableName + " (" +
                    "storage_key_hash varchar(64) primary key," +
                    "storage_key varchar(512) not null," +
                    "payload_hash varchar(64) not null," +
                    "status varchar(32) not null," +
                    "result_body nvarchar(max) null," +
                    "expires_at datetime2 not null," +
                    "created_at datetime2 not null," +
                    "updated_at datetime2 not null" +
                    ")";
            case H2:
                return "create table if not exists " + tableName + " (" +
                    "storage_key_hash varchar(64) primary key," +
                    "storage_key varchar(512) not null," +
                    "payload_hash varchar(64) not null," +
                    "status varchar(32) not null," +
                    "result_body clob null," +
                    "expires_at timestamp not null," +
                    "created_at timestamp not null," +
                    "updated_at timestamp not null" +
                    ")";
            case GENERIC:
            default:
                return "create table " + tableName + " (" +
                    "storage_key_hash varchar(64) primary key," +
                    "storage_key varchar(512) not null," +
                    "payload_hash varchar(64) not null," +
                    "status varchar(32) not null," +
                    "result_body clob null," +
                    "expires_at timestamp not null," +
                    "created_at timestamp not null," +
                    "updated_at timestamp not null" +
                    ")";
        }
    }

    /**
     * 判断异常是否表示数据表已存在。
     */
    @Override
    public boolean isTableAlreadyExists(Throwable error) {
        SQLException sqlException = findSqlException(error);
        if (sqlException != null) {
            int errorCode = sqlException.getErrorCode();
            String sqlState = sqlException.getSQLState();
            if (this == MYSQL && errorCode == 1050) {
                return true;
            }
            if (this == POSTGRESQL && "42P07".equals(sqlState)) {
                return true;
            }
            if (this == ORACLE && errorCode == 955) {
                return true;
            }
            if (this == SQL_SERVER && errorCode == 2714) {
                return true;
            }
        }

        String message = findMessage(error);
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("already exists")
            || normalized.contains("already an object named")
            || normalized.contains("name is already used by an existing object");
    }

    private static SQLException findSqlException(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException) {
                return (SQLException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * 从异常链中提取错误消息。
     */
    private static String findMessage(Throwable error) {
        Throwable current = error;
        Throwable lastWithMessage = null;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().trim().isEmpty()) {
                lastWithMessage = current;
            }
            current = current.getCause();
        }
        return lastWithMessage == null ? null : lastWithMessage.getMessage();
    }
}
