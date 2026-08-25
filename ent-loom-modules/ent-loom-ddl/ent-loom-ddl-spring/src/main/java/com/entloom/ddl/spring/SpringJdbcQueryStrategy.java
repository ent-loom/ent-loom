package com.entloom.ddl.spring;

import com.entloom.ddl.api.DdlColumnMetadata;
import com.entloom.ddl.api.DdlIndexMetadata;
import com.entloom.ddl.api.QueryStrategy;
import com.entloom.ddl.api.DdlTableSnapshot;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;

/**
 * 基于 Spring JDBC 的 MySQL 表存在性查询策略。
 *
 * <p>查询参数使用 JDBC 占位符绑定；连接、Statement 和 ResultSet 的释放由
 * {@link JdbcTemplate} 负责。</p>
 */
public final class SpringJdbcQueryStrategy implements QueryStrategy {
    private static final String TABLE_EXISTS_SQL =
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?";
    private static final String CURRENT_SCHEMA_TABLE_EXISTS_SQL =
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";

    private final JdbcTemplate jdbcTemplate;

    public SpringJdbcQueryStrategy(DataSource dataSource) {
        this(new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null")));
    }

    public SpringJdbcQueryStrategy(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public boolean tableExists(String schema, String tableName) {
        String normalizedTableName = requireText(tableName, "tableName");
        String normalizedSchema = trim(schema);
        Long count = normalizedSchema.isEmpty()
                ? jdbcTemplate.queryForObject(CURRENT_SCHEMA_TABLE_EXISTS_SQL,
                        Long.class, normalizedTableName)
                : jdbcTemplate.queryForObject(TABLE_EXISTS_SQL,
                        Long.class, normalizedSchema, normalizedTableName);
        return count != null && count > 0L;
    }

    @Override
    public DdlTableSnapshot readTable(String schema, String tableName) {
        String normalizedTableName = requireText(tableName, "tableName");
        return jdbcTemplate.execute((ConnectionCallback<DdlTableSnapshot>) connection ->
                readTable(connection, trim(schema), normalizedTableName));
    }

    private static DdlTableSnapshot readTable(Connection connection,
                                              String schema,
                                              String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        TableInfo table = findTable(metaData, connection, schema, tableName);
        if (table == null) {
            return DdlTableSnapshot.missing(schema, tableName);
        }

        List<DdlColumnMetadata> columns = readColumns(metaData, table);
        List<String> primaryKeys = readPrimaryKeys(metaData, table);
        List<DdlIndexMetadata> indexes = readIndexes(metaData, table, columns, primaryKeys);
        if (isMysql(connection)) {
            indexes = mergeMysqlExpressionIndexes(connection, table, indexes);
        }
        return new DdlTableSnapshot(true, schema, tableName, table.comment,
                columns, primaryKeys, indexes);
    }

    private static TableInfo findTable(DatabaseMetaData metaData,
                                       Connection connection,
                                       String schema,
                                       String tableName) throws SQLException {
        String catalog = schema.isEmpty() ? trim(connection.getCatalog()) : schema;
        String schemaName = schema.isEmpty() ? trim(connection.getSchema()) : schema;
        List<MetadataScope> scopes = new ArrayList<MetadataScope>();
        addScope(scopes, catalog, schemaName);
        addScope(scopes, catalog, "");
        addScope(scopes, "", schemaName);
        addScope(scopes, "", "");
        for (MetadataScope scope : scopes) {
            for (String candidate : candidateNames(tableName)) {
                try (ResultSet resultSet = metaData.getTables(emptyToNull(scope.catalog),
                        emptyToNull(scope.schema), candidate, new String[] {"TABLE"})) {
                    if (resultSet.next()) {
                        return new TableInfo(scope.catalog, scope.schema,
                                resultSet.getString("TABLE_NAME"), trim(resultSet.getString("REMARKS")));
                    }
                }
            }
        }
        return null;
    }

    private static List<DdlColumnMetadata> readColumns(DatabaseMetaData metaData,
                                                        TableInfo table) throws SQLException {
        List<DdlColumnMetadata> columns = new ArrayList<DdlColumnMetadata>();
        try (ResultSet resultSet = metaData.getColumns(emptyToNull(table.catalog),
                emptyToNull(table.schema), table.tableName, null)) {
            while (resultSet.next()) {
                String name = resultSet.getString("COLUMN_NAME");
                if (name == null || name.trim().isEmpty()) {
                    continue;
                }
                columns.add(new DdlColumnMetadata(name,
                        sqlType(resultSet),
                        resultSet.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                        resultSet.getString("COLUMN_DEF"),
                        resultSet.getString("REMARKS"),
                        isYes(resultSet.getString("IS_AUTOINCREMENT"))));
            }
        }
        return columns;
    }

    private static List<String> readPrimaryKeys(DatabaseMetaData metaData,
                                                 TableInfo table) throws SQLException {
        Map<Short, String> ordered = new TreeMap<Short, String>();
        try (ResultSet resultSet = metaData.getPrimaryKeys(emptyToNull(table.catalog),
                emptyToNull(table.schema), table.tableName)) {
            while (resultSet.next()) {
                String column = resultSet.getString("COLUMN_NAME");
                if (column != null && !column.trim().isEmpty()) {
                    ordered.put(resultSet.getShort("KEY_SEQ"), column);
                }
            }
        }
        return new ArrayList<String>(ordered.values());
    }

    private static List<DdlIndexMetadata> readIndexes(DatabaseMetaData metaData,
                                                       TableInfo table,
                                                       List<DdlColumnMetadata> columns,
                                                       List<String> primaryKeys) throws SQLException {
        Map<String, IndexInfo> indexes = new LinkedHashMap<String, IndexInfo>();
        try (ResultSet resultSet = metaData.getIndexInfo(emptyToNull(table.catalog),
                emptyToNull(table.schema), table.tableName, false, false)) {
            while (resultSet.next()) {
                String name = resultSet.getString("INDEX_NAME");
                String column = resultSet.getString("COLUMN_NAME");
                String expression = optionalString(resultSet, "EXPRESSION");
                if (name == null || name.trim().isEmpty()
                        || ((column == null || column.trim().isEmpty()) && expression.isEmpty())) {
                    continue;
                }
                IndexInfo index = indexes.get(name);
                if (index == null) {
                    index = new IndexInfo(!resultSet.getBoolean("NON_UNIQUE"));
                    indexes.put(name, index);
                }
                short position = resultSet.getShort("ORDINAL_POSITION");
                if (column != null && !column.trim().isEmpty()) {
                    index.columns.put(position, column);
                }
                if (!expression.isEmpty()) {
                    index.expressions.put(position, expression);
                }
            }
        }

        List<String> columnNames = new ArrayList<String>();
        for (DdlColumnMetadata column : columns) {
            columnNames.add(column.columnName());
        }
        List<DdlIndexMetadata> result = new ArrayList<DdlIndexMetadata>();
        for (Map.Entry<String, IndexInfo> entry : indexes.entrySet()) {
            List<String> fields = new ArrayList<String>(entry.getValue().columns.values());
            String expression = join(entry.getValue().expressions.values());
            if ((fields.isEmpty() && expression.isEmpty())
                    || (!fields.isEmpty() && !columnNames.containsAll(fields))
                    || (entry.getValue().unique && primaryKeys.equals(fields))) {
                continue;
            }
            result.add(new DdlIndexMetadata(entry.getKey(), expression.isEmpty() ? fields : Collections.<String>emptyList(),
                    entry.getValue().unique, expression));
        }
        result.sort(Comparator.comparing(DdlIndexMetadata::name));
        return result;
    }

    private static List<DdlIndexMetadata> mergeMysqlExpressionIndexes(Connection connection,
                                                                        TableInfo table,
                                                                        List<DdlIndexMetadata> indexes)
            throws SQLException {
        String schema = !trim(table.schema).isEmpty() ? table.schema : table.catalog;
        if (schema.isEmpty()) {
            return indexes;
        }
        Map<String, IndexInfo> expressionIndexes = new LinkedHashMap<String, IndexInfo>();
        String sql = "SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, EXPRESSION"
                + " FROM information_schema.statistics"
                + " WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?"
                + " AND EXPRESSION IS NOT NULL"
                + " ORDER BY INDEX_NAME, SEQ_IN_INDEX";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table.tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String name = trim(resultSet.getString("INDEX_NAME"));
                    String expression = trim(resultSet.getString("EXPRESSION"));
                    if (name.isEmpty() || expression.isEmpty()) {
                        continue;
                    }
                    IndexInfo index = expressionIndexes.get(name);
                    if (index == null) {
                        index = new IndexInfo(!resultSet.getBoolean("NON_UNIQUE"));
                        expressionIndexes.put(name, index);
                    }
                    index.expressions.put(resultSet.getShort("SEQ_IN_INDEX"), expression);
                }
            }
        }
        if (expressionIndexes.isEmpty()) {
            return indexes;
        }

        Map<String, DdlIndexMetadata> merged = new LinkedHashMap<String, DdlIndexMetadata>();
        for (DdlIndexMetadata index : indexes) {
            merged.put(index.name(), index);
        }
        for (Map.Entry<String, IndexInfo> entry : expressionIndexes.entrySet()) {
            if (merged.containsKey(entry.getKey())) {
                continue;
            }
            String expression = join(entry.getValue().expressions.values());
            if (!expression.isEmpty()) {
                merged.put(entry.getKey(), new DdlIndexMetadata(entry.getKey(),
                        Collections.<String>emptyList(), entry.getValue().unique, expression));
            }
        }
        List<DdlIndexMetadata> result = new ArrayList<DdlIndexMetadata>(merged.values());
        result.sort(Comparator.comparing(DdlIndexMetadata::name));
        return result;
    }

    private static String sqlType(ResultSet resultSet) throws SQLException {
        String typeName = trim(resultSet.getString("TYPE_NAME")).toLowerCase();
        if (typeName.contains("(")) {
            return typeName;
        }
        if ("character varying".equals(typeName) || "varchar".equals(typeName)
                || "char".equals(typeName) || "varbinary".equals(typeName)
                || "binary".equals(typeName)) {
            int size = resultSet.getInt("COLUMN_SIZE");
            return size > 0 ? typeName.replace("character varying", "varchar") + "(" + size + ")" : typeName;
        }
        if ("decimal".equals(typeName) || "numeric".equals(typeName)) {
            int precision = resultSet.getInt("COLUMN_SIZE");
            int scale = resultSet.getInt("DECIMAL_DIGITS");
            return precision > 0 ? "decimal(" + precision + "," + Math.max(scale, 0) + ")" : "decimal";
        }
        return typeName;
    }

    private static List<String> candidateNames(String tableName) {
        List<String> candidates = new ArrayList<String>();
        candidates.add(tableName);
        if (!tableName.equals(tableName.toUpperCase())) {
            candidates.add(tableName.toUpperCase());
        }
        if (!tableName.equals(tableName.toLowerCase())) {
            candidates.add(tableName.toLowerCase());
        }
        return candidates;
    }

    private static void addScope(List<MetadataScope> scopes, String catalog, String schema) {
        MetadataScope scope = new MetadataScope(catalog, schema);
        if (!scopes.contains(scope)) {
            scopes.add(scope);
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static boolean isMysql(Connection connection) throws SQLException {
        return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
    }

    private static boolean isYes(String value) {
        return "YES".equalsIgnoreCase(trim(value)) || "TRUE".equalsIgnoreCase(trim(value));
    }

    private static String optionalString(ResultSet resultSet, String column) {
        try {
            return trim(resultSet.getString(column));
        } catch (SQLException ignored) {
            return "";
        }
    }

    private static String join(Iterable<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(value);
        }
        return result.toString();
    }

    private static String requireText(String value, String fieldName) {
        String normalized = trim(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class MetadataScope {
        private final String catalog;
        private final String schema;

        private MetadataScope(String catalog, String schema) {
            this.catalog = catalog;
            this.schema = schema;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof MetadataScope)) {
                return false;
            }
            MetadataScope that = (MetadataScope) other;
            return catalog.equals(that.catalog) && schema.equals(that.schema);
        }

        @Override
        public int hashCode() {
            return Objects.hash(catalog, schema);
        }
    }

    private static final class TableInfo {
        private final String catalog;
        private final String schema;
        private final String tableName;
        private final String comment;

        private TableInfo(String catalog, String schema, String tableName, String comment) {
            this.catalog = catalog;
            this.schema = schema;
            this.tableName = tableName;
            this.comment = comment;
        }
    }

    private static final class IndexInfo {
        private final boolean unique;
        private final Map<Short, String> columns = new TreeMap<Short, String>();
        private final Map<Short, String> expressions = new TreeMap<Short, String>();

        private IndexInfo(boolean unique) {
            this.unique = unique;
        }
    }
}
