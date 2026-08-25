package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlIndexMetadata;

/**
 * MySQL E3 ALTER TABLE SQL 生成器。
 */
public final class MysqlAlterTableSqlBuilder {
    private final MysqlCreateTableSqlBuilder createTableSqlBuilder;

    public MysqlAlterTableSqlBuilder() {
        this(new MysqlCreateTableSqlBuilder());
    }

    public MysqlAlterTableSqlBuilder(MysqlCreateTableSqlBuilder createTableSqlBuilder) {
        this.createTableSqlBuilder = createTableSqlBuilder == null
                ? new MysqlCreateTableSqlBuilder() : createTableSqlBuilder;
    }

    public String buildAddColumn(DdlEntityMetadata entity, String schema, DdlFieldMetadata field) {
        return prefix(entity, schema) + " ADD COLUMN " + createTableSqlBuilder.buildColumnSql(field);
    }

    public String buildModifyColumn(DdlEntityMetadata entity, String schema, DdlFieldChange change) {
        return prefix(entity, schema) + " MODIFY COLUMN "
                + MysqlCreateTableSqlBuilder.quote(change.desiredField().columnName()) + " "
                + createTableSqlBuilder.buildColumnDefinition(change.desiredField(), change.existingAutoIncrement());
    }

    public String buildChangeColumn(DdlEntityMetadata entity, String schema, DdlFieldChange change) {
        return prefix(entity, schema) + " CHANGE COLUMN "
                + MysqlCreateTableSqlBuilder.quote(change.existingColumnName()) + " "
                + MysqlCreateTableSqlBuilder.quote(change.desiredField().columnName()) + " "
                + createTableSqlBuilder.buildColumnDefinition(change.desiredField(), change.existingAutoIncrement());
    }

    public String buildAddIndex(DdlEntityMetadata entity, String schema, DdlIndexMetadata index) {
        return prefix(entity, schema) + " ADD " + createTableSqlBuilder.buildIndexSql(index);
    }

    public String buildComment(DdlEntityMetadata entity, String schema) {
        return prefix(entity, schema) + " COMMENT='" + escapeQuote(entity.comment()) + "'";
    }

    private static String prefix(DdlEntityMetadata entity, String schema) {
        return "ALTER TABLE " + MysqlCreateTableSqlBuilder.fullTableName(schema, entity.tableName());
    }

    private static String escapeQuote(String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}
