package com.entloom.crud.engine.jdbc.stats.query;

import java.util.ArrayList;
import java.util.List;

/**
 * 统计 JDBC 查询过程中使用的内部 SQL 模型。
 */
final class JdbcStatsSqlModel {
    private JdbcStatsSqlModel() {
    }

    /**
     * where 子句及参数。
     */
    static final class WhereClause {
        private final String sql;
        private final List<Object> args;

        WhereClause(String sql, List<Object> args) {
            this.sql = sql == null ? "" : sql;
            this.args = args == null ? new ArrayList<Object>() : args;
        }

        String getSql() {
            return sql;
        }

        List<Object> getArgs() {
            return new ArrayList<Object>(args);
        }
    }

    /**
     * having 子句及参数。
     */
    static final class HavingClause {
        private final String sql;
        private final List<Object> args;

        HavingClause(String sql, List<Object> args) {
            this.sql = sql == null ? "" : sql;
            this.args = args == null ? new ArrayList<Object>() : args;
        }

        String getSql() {
            return sql;
        }

        List<Object> getArgs() {
            return new ArrayList<Object>(args);
        }
    }

    /**
     * 编译完成后的统计 SQL 语句集合。
     */
    static final class CompiledStatsSql {
        private final String rowsSql;
        private final List<Object> rowsArgs;
        private final String totalGroupsSql;
        private final List<Object> totalGroupsArgs;
        private final String summarySql;
        private final List<Object> summaryArgs;
        private final List<DimensionDescriptor> dimensions;
        private final List<MetricDescriptor> metrics;

        CompiledStatsSql(
            String rowsSql,
            List<Object> rowsArgs,
            String totalGroupsSql,
            List<Object> totalGroupsArgs,
            String summarySql,
            List<Object> summaryArgs,
            List<DimensionDescriptor> dimensions,
            List<MetricDescriptor> metrics
        ) {
            this.rowsSql = rowsSql;
            this.rowsArgs = rowsArgs;
            this.totalGroupsSql = totalGroupsSql;
            this.totalGroupsArgs = totalGroupsArgs;
            this.summarySql = summarySql;
            this.summaryArgs = summaryArgs;
            this.dimensions = dimensions;
            this.metrics = metrics;
        }

        String getRowsSql() {
            return rowsSql;
        }

        List<Object> getRowsArgs() {
            return new ArrayList<Object>(rowsArgs);
        }

        String getTotalGroupsSql() {
            return totalGroupsSql;
        }

        List<Object> getTotalGroupsArgs() {
            return new ArrayList<Object>(totalGroupsArgs);
        }

        String getSummarySql() {
            return summarySql;
        }

        List<Object> getSummaryArgs() {
            return new ArrayList<Object>(summaryArgs);
        }

        List<DimensionDescriptor> getDimensions() {
            return dimensions;
        }

        List<MetricDescriptor> getMetrics() {
            return metrics;
        }
    }

    /**
     * 维度字段描述。
     */
    static final class DimensionDescriptor {
        private final String field;
        private final String alias;
        private final String selectExpr;
        private final String groupExpr;

        DimensionDescriptor(String field, String alias, String selectExpr, String groupExpr) {
            this.field = field;
            this.alias = alias;
            this.selectExpr = selectExpr;
            this.groupExpr = groupExpr;
        }

        String getField() {
            return field;
        }

        String getAlias() {
            return alias;
        }

        String getSelectExpr() {
            return selectExpr;
        }

        String getGroupExpr() {
            return groupExpr;
        }
    }

    /**
     * 指标字段描述。
     */
    static final class MetricDescriptor {
        private final String alias;
        private final String expr;

        MetricDescriptor(String alias, String expr) {
            this.alias = alias;
            this.expr = expr;
        }

        String getAlias() {
            return alias;
        }

        String getExpr() {
            return expr;
        }
    }
}
