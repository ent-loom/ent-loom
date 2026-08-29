package com.entloom.crud.engine.jdbc.query;

import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.PageCountMode;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.core.exception.DataScopeDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.capability.query.CompiledQuery;
import com.entloom.crud.core.capability.query.QueryCompiler;
import com.entloom.crud.core.capability.query.QueryPlan;
import com.entloom.crud.core.foundation.read.relation.ResolvedExistsRelationFilter;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;
import com.entloom.crud.engine.jdbc.sql.JdbcPredicateBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 简单 JDBC 查询编译器。
 */
public class JdbcQueryCompiler implements QueryCompiler {
    /** 数据库方言。 */
    private final JdbcDialect dialect;

    public JdbcQueryCompiler() {
        this(StandardJdbcDialect.GENERIC);
    }

    public JdbcQueryCompiler(JdbcDialect dialect) {
        this.dialect = dialect == null ? StandardJdbcDialect.GENERIC : dialect;
    }

    /**
     * 编译查询计划生成可执行查询对象。
     */
    @Override
    public CompiledQuery compile(QueryPlan plan) {
        EntityMeta rootMeta = plan.getRootMeta();
        CompiledQuery compiled = new CompiledQuery();
        compiled.setQueryPlan(plan);

        WhereClause whereClause = buildWhereClause(plan, rootMeta);
        String where = whereClause.sql();
        List<Object> dataArgs = new ArrayList<Object>(whereClause.args());

        StringBuilder dataSql = new StringBuilder("select ").append(buildSelectClause(plan, rootMeta))
            .append(" from ").append(rootMeta.getTable()).append(" t");
        if (!where.trim().isEmpty()) {
            dataSql.append(" where ").append(where);
        }

        dataSql.append(buildOrderBy(plan, rootMeta));

        if (plan.getOp() == QueryOperation.PAGE) {
            int requestedLimit = plan.getSpec().getPage().getLimit();
            int limit = requestedLimit;
            if (plan.getSpec().getCountMode() == PageCountMode.NONE) {
                limit = requestedLimit + 1;
            }
            int offset = (plan.getSpec().getPage().getPage() - 1) * requestedLimit;
            dialect.appendPageClause(dataSql, limit, offset, dataArgs);
        } else if (plan.getOp() == QueryOperation.LIST) {
            dialect.appendListClause(dataSql, plan.getSpec().getLimit(), dataArgs);
        } else if (plan.getOp() == QueryOperation.FIND_ONE) {
            dialect.appendFindOneClause(dataSql, dataArgs);
        } else if (plan.getOp() == QueryOperation.DETAIL) {
            dialect.appendDetailClause(dataSql);
        }

        compiled.setDataSql(dataSql.toString());
        compiled.setDataArgs(dataArgs);

        StringBuilder countSql = new StringBuilder("select count(1) from ").append(rootMeta.getTable()).append(" t");
        if (!where.trim().isEmpty()) {
            countSql.append(" where ").append(where);
        }
        compiled.setCountSql(countSql.toString());
        compiled.setCountArgs(new ArrayList<Object>(whereClause.args()));

        compiled.setExpandEdges(plan.getExpandEdges());

        return compiled;
    }

    private WhereClause buildWhereClause(QueryPlan plan, EntityMeta rootMeta) {
        List<Object> args = new ArrayList<Object>();
        List<String> predicates = new ArrayList<>();
        predicates.addAll(buildBasePredicates(rootMeta, "t"));
        predicates.addAll(buildGovernancePredicates(plan.getGovernanceScope(), rootMeta, args, "t"));
        predicates.addAll(buildCallerFilterPredicates(plan.getFilters(), rootMeta, args));
        String existsPredicate = buildExistsRelationPredicate(plan, rootMeta, args);
        if (existsPredicate != null) {
            predicates.add(existsPredicate);
        }
        return new WhereClause(String.join(" and ", predicates), args);
    }

    private String buildSelectClause(QueryPlan plan, EntityMeta rootMeta) {
        List<String> selectFields = plan.getSpec().getSelectFields();
        if (selectFields == null || selectFields.isEmpty()) {
            return "*";
        }
        List<String> columns = new ArrayList<String>();
        for (String field : selectFields) {
            if (field == null || field.trim().isEmpty()) {
                throw new ValidationException("selectFields 不允许包含空字段");
            }
            String normalized = field.trim();
            if (normalized.contains(".")) {
                throw new ValidationException("默认编译器不支持关联字段投影: " + normalized);
            }
            String column = rootMeta.resolveColumn(normalized);
            if (column == null) {
                throw new ValidationException("未知投影字段: " + normalized);
            }
            columns.add("t." + column + " as " + normalized);
        }
        return String.join(",", columns);
    }

    private List<String> buildBasePredicates(EntityMeta entityMeta, String alias) {
        List<String> predicates = new ArrayList<String>();
        if (entityMeta.getLogicDeleteField() != null && !entityMeta.getLogicDeleteField().trim().isEmpty()) {
            String logicDeleteCol = entityMeta.resolveColumn(entityMeta.getLogicDeleteField());
            predicates.add(alias + "." + logicDeleteCol + " = 0");
        }
        return predicates;
    }

    /**
     * 构建治理范围过滤谓词。
     */
    private List<String> buildGovernancePredicates(
        CrudDataScope scope,
        EntityMeta entityMeta,
        List<Object> args,
        String alias
    ) {
        List<String> predicates = new ArrayList<String>();
        if (scope == null || scope.isExplicitAll()) {
            return predicates;
        }
        for (java.util.Map.Entry<String, Object> entry : scope.getDimensions().entrySet()) {
            String column = entityMeta.resolveColumn(entry.getKey());
            if (column == null) {
                throw new DataScopeDeniedException("不支持的治理范围维度: " + entityMeta.getEntityName() + "." + entry.getKey());
            }
            JdbcPredicateBuilder.appendEqualityOrIn(predicates, args, alias + "." + column, entry.getValue(), "governance scope");
        }
        return predicates;
    }

    /**
     * 构建调用方过滤条件谓词。
     */
    private List<String> buildCallerFilterPredicates(List<QueryFilter> filters, EntityMeta rootMeta, List<Object> args) {
        return buildFilterPredicates(filters, rootMeta, args, "t", false);
    }

    /**
     * 构建实体字段过滤谓词。
     */
    private List<String> buildFilterPredicates(
        List<QueryFilter> filters,
        EntityMeta entityMeta,
        List<Object> args,
        String alias,
        boolean existsRelation
    ) {
        List<String> predicates = new ArrayList<String>();
        for (QueryFilter filter : filters) {
            if (filter == null) {
                throw new ValidationException("过滤条件不能为空");
            }
            if (filter.getField().contains(".")) {
                throw new ValidationException(existsRelation
                    ? "EXISTS 仅支持目标实体直接字段过滤"
                    : "MVP-1 默认编译器不支持关联过滤");
            }

            String column = entityMeta.resolveColumn(filter.getField());
            if (column == null) {
                throw new ValidationException("未知过滤字段: " + filter.getField());
            }
            if (entityMeta.resolveFieldMeta(filter.getField()) == null
                || !entityMeta.resolveFieldMeta(filter.getField()).isFilterable()) {
                throw new ValidationException("字段不允许过滤: " + filter.getField());
            }

            String qualified = alias + "." + column;
            FilterOperator op = filter.getOperator();
            Object value = filter.getValue();

            switch (op) {
                case EQ:
                    JdbcPredicateBuilder.appendEqualityOrIn(predicates, args, qualified, value, filter.getField());
                    break;
                case NE:
                    predicates.add(qualified + " <> ?");
                    args.add(value);
                    break;
                case GT:
                    predicates.add(qualified + " > ?");
                    args.add(value);
                    break;
                case GE:
                    predicates.add(qualified + " >= ?");
                    args.add(value);
                    break;
                case LT:
                    predicates.add(qualified + " < ?");
                    args.add(value);
                    break;
                case LE:
                    predicates.add(qualified + " <= ?");
                    args.add(value);
                    break;
                case LIKE:
                    predicates.add(qualified + " like ?");
                    args.add(value);
                    break;
                case IS_NULL:
                    predicates.add(qualified + " is null");
                    break;
                case IS_NOT_NULL:
                    predicates.add(qualified + " is not null");
                    break;
                case BETWEEN:
                    if (!(value instanceof List<?>)) {
                        throw new ValidationException("BETWEEN 操作需要两个值: " + filter.getField());
                    }
                    List<?> betweenValues = (List<?>) value;
                    if (betweenValues.size() != 2) {
                        throw new ValidationException("BETWEEN 操作需要两个值: " + filter.getField());
                    }
                    predicates.add(qualified + " between ? and ?");
                    args.add(betweenValues.get(0));
                    args.add(betweenValues.get(1));
                    break;
                case IN:
                case NOT_IN:
                    if (!(value instanceof Collection<?>)) {
                        throw new ValidationException(op + " 需要非空集合: " + filter.getField());
                    }
                    Collection<?> values = (Collection<?>) value;
                    if (values.isEmpty()) {
                        throw new ValidationException(op + " 需要非空集合: " + filter.getField());
                    }
                    String placeholders = values.stream().map(v -> "?").collect(Collectors.joining(","));
                    predicates.add(qualified + (op == FilterOperator.IN ? " in (" : " not in (") + placeholders + ")");
                    args.addAll(values);
                    break;
                default:
                    throw new ValidationException("不支持的过滤操作符: " + op);
            }
        }
        return predicates;
    }

    /**
     * 构建单个本地一跳 EXISTS 子查询谓词。
     */
    private String buildExistsRelationPredicate(QueryPlan plan, EntityMeta rootMeta, List<Object> args) {
        ResolvedExistsRelationFilter existsRelationFilter = plan.getExistsRelationFilter();
        if (existsRelationFilter == null) {
            return null;
        }
        RelationEdge edge = existsRelationFilter.getRelationEdge();
        EntityMeta targetMeta = existsRelationFilter.getTargetMeta();
        String rootColumn = rootMeta.resolveColumn(edge.getFromField());
        String targetColumn = targetMeta.resolveColumn(edge.getToField());
        List<String> predicates = new ArrayList<String>();
        predicates.add("r." + targetColumn + " = t." + rootColumn);
        predicates.addAll(buildBasePredicates(targetMeta, "r"));
        predicates.addAll(buildGovernancePredicates(plan.getGovernanceScope(), targetMeta, args, "r"));
        predicates.addAll(buildFilterPredicates(existsRelationFilter.getFilters(), targetMeta, args, "r", true));
        return "exists (select 1 from " + targetMeta.getTable() + " r where " + String.join(" and ", predicates) + ")";
    }

    /**
     * 构建排序子句。
     */
    private String buildOrderBy(QueryPlan plan, EntityMeta rootMeta) {
        List<QuerySort> sorts = plan.getSpec().getSorts();
        if (sorts == null || sorts.isEmpty()) {
            String pkCol = rootMeta.resolveColumn(rootMeta.getIdField());
            return " order by t." + pkCol + " asc";
        }

        List<String> clauses = new ArrayList<>();
        for (QuerySort sort : sorts) {
            if (sort.getField().contains(".")) {
                throw new ValidationException("MVP-1 默认编译器不支持关联排序");
            }
            String col = rootMeta.resolveColumn(sort.getField());
            if (col == null) {
                throw new ValidationException("未知排序字段: " + sort.getField());
            }
            if (rootMeta.resolveFieldMeta(sort.getField()) == null
                || !rootMeta.resolveFieldMeta(sort.getField()).isSortable()) {
                throw new ValidationException("字段不允许排序: " + sort.getField());
            }
            clauses.add("t." + col + " " + sort.getDirection().name());
        }
        return " order by " + String.join(",", clauses);
    }

    private static final class WhereClause {
        /** SQL 语句。 */
        private final String sql;
        /** 参数列表。 */
        private final List<Object> args;

        private WhereClause(String sql, List<Object> args) {
            this.sql = sql;
            this.args = args;
        }

        private String sql() {
            return sql;
        }

        private List<Object> args() {
            return args;
        }
    }
}
