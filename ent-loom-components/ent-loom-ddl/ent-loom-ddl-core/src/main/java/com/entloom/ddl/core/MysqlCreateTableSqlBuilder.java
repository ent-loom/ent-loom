package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlIndexMetadata;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL 建表 SQL 生成器。
 */
public final class MysqlCreateTableSqlBuilder {
    private final MysqlTypeMapper typeMapper;

    public MysqlCreateTableSqlBuilder() {
        this(new MysqlTypeMapper());
    }

    public MysqlCreateTableSqlBuilder(MysqlTypeMapper typeMapper) {
        this.typeMapper = typeMapper == null ? new MysqlTypeMapper() : typeMapper;
    }

    public String build(DdlEntityMetadata entity, String schema) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        List<String> parts = new ArrayList<String>();
        List<String> primaryKeys = new ArrayList<String>();

        for (DdlFieldMetadata field : entity.fields()) {
            if (field == null || !field.persisted()) {
                continue;
            }
            parts.add(buildColumnSql(field));
            if (field.primaryKey()) {
                primaryKeys.add(quote(field.columnName()));
            }
            if (field.unique()) {
                parts.add(buildFieldUniqueSql(entity.tableName(), field.columnName()));
            }
        }

        if (!primaryKeys.isEmpty()) {
            parts.add("PRIMARY KEY (" + join(primaryKeys, ", ") + ")");
        }
        for (DdlIndexMetadata index : entity.indexes()) {
            if (index == null) {
                continue;
            }
            String indexSql = buildIndexSql(index);
            if (!indexSql.isEmpty()) {
                parts.add(indexSql);
            }
        }

        String fullTable = fullTableName(schema, entity.tableName());
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS ").append(fullTable).append(" (\n  ");
        sql.append(join(parts, ",\n  "));
        sql.append("\n)");
        if (!trim(entity.comment()).isEmpty()) {
            sql.append(" COMMENT='").append(escapeQuote(entity.comment())).append("'");
        }
        return sql.toString();
    }

    private String buildColumnSql(DdlFieldMetadata field) {
        StringBuilder sb = new StringBuilder();
        sb.append(quote(field.columnName())).append(" ");
        if (!trim(field.columnDefinition()).isEmpty()) {
            sb.append(field.columnDefinition());
        } else {
            sb.append(typeMapper.toSqlType(field));
            sb.append(field.nullable() ? " NULL" : " NOT NULL");
            if (!trim(field.defaultValue()).isEmpty()) {
                sb.append(" DEFAULT ").append(defaultLiteral(field.defaultValue()));
            }
            if (!trim(field.comment()).isEmpty()) {
                sb.append(" COMMENT '").append(escapeQuote(field.comment())).append("'");
            }
        }
        return sb.toString();
    }

    private String buildFieldUniqueSql(String tableName, String columnName) {
        String indexName = "uk_" + normalize(tableName) + "_" + normalize(columnName);
        return "UNIQUE KEY " + quote(indexName) + " (" + quote(columnName) + ")";
    }

    private String buildIndexSql(DdlIndexMetadata index) {
        String name = trim(index.name());
        String resolvedName = name.isEmpty() ? "idx_auto_" + Integer.toHexString(index.hashCode()) : name;
        if (!trim(index.expression()).isEmpty()) {
            return (index.unique() ? "UNIQUE KEY " : "KEY ")
                    + quote(resolvedName)
                    + " (("
                    + index.expression()
                    + "))";
        }
        if (index.fields().isEmpty()) {
            return "";
        }
        List<String> quoted = new ArrayList<String>();
        for (String field : index.fields()) {
            if (trim(field).isEmpty()) {
                continue;
            }
            quoted.add(quote(field));
        }
        if (quoted.isEmpty()) {
            return "";
        }
        return (index.unique() ? "UNIQUE KEY " : "KEY ")
                + quote(resolvedName)
                + " ("
                + join(quoted, ", ")
                + ")";
    }

    private static String defaultLiteral(String value) {
        String text = trim(value);
        if (text.isEmpty()) {
            return "NULL";
        }
        if ("NULL".equalsIgnoreCase(text)) {
            return "NULL";
        }
        if (text.matches("^-?\\d+(\\.\\d+)?$")) {
            return text;
        }
        if ("CURRENT_TIMESTAMP".equalsIgnoreCase(text) || text.toUpperCase().startsWith("CURRENT_TIMESTAMP(")) {
            return text;
        }
        return "'" + escapeQuote(text) + "'";
    }

    private static String fullTableName(String schema, String tableName) {
        String cleanSchema = trim(schema);
        if (cleanSchema.isEmpty()) {
            return quote(tableName);
        }
        return quote(cleanSchema) + "." + quote(tableName);
    }

    private static String quote(String value) {
        return "`" + value.replace("`", "``") + "`";
    }

    private static String escapeQuote(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private static String normalize(String value) {
        String text = trim(value).replaceAll("[^a-zA-Z0-9_]+", "_");
        return text.isEmpty() ? "auto" : text;
    }

    private static String join(List<String> values, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(sep);
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
