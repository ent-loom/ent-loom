package com.entloom.crud.engine.jdbc.dao;

import com.entloom.crud.annotations.EntCommand;
import com.entloom.crud.annotations.EntQuery;
import com.entloom.crud.api.enums.CountMode;
import com.entloom.crud.api.enums.SortDirection;
import com.entloom.crud.api.enums.SortTarget;
import com.entloom.crud.api.model.PageQuery;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.RowConstraint;
import com.entloom.crud.core.capability.dao.RowConstraintOperator;
import com.entloom.crud.core.exception.NotFoundException;
import com.entloom.crud.core.exception.QueryNotUniqueException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.context.DefaultExecutionContext;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.query.JdbcReflectiveMapper;
import com.entloom.crud.engine.jdbc.sql.JdbcLogicDeleteValues;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * JDBC 实体 DAO 自定义方法执行器。
 *
 * <p>首期只接受能够解析为单表 SELECT、UPDATE 或 DELETE 的静态 SQL；解析后的
 * 治理谓词通过结构化位置追加，不允许调用方关闭范围或逻辑删除约束。</p>
 */
public final class JdbcEntityDaoCustomMethodExecutor {
    /** 默认自定义查询允许的最大页大小，保留旧常量作为兼容入口。 */
    public static final int DEFAULT_MAX_PAGE_SIZE = JdbcPaginationPolicy.DEFAULT_MAX_PAGE_SIZE;
    /** 默认自定义查询允许的最大偏移，保留旧常量作为兼容入口。 */
    public static final int DEFAULT_MAX_OFFSET = (int) JdbcPaginationPolicy.DEFAULT_MAX_OFFSET;
    private static final String RESERVED_PARAMETER_PREFIX = "__ent_";
    private static final Pattern SIMPLE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");
    private static final Pattern NUMBER_LITERAL = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");
    private static final Set<String> STOP_WORDS = new HashSet<String>();
    private static final Set<String> SQL_KEYWORDS = new HashSet<String>();
    private static final Set<String> ALLOWED_FUNCTIONS = new HashSet<String>();

    static {
        Collections.addAll(STOP_WORDS, "where", "group", "having", "order", "limit", "offset", "for");
        Collections.addAll(SQL_KEYWORDS,
            "select", "from", "where", "and", "or", "not", "in", "is", "null", "true", "false",
            "update", "delete", "set", "as", "order", "by", "asc", "desc", "like", "between",
            "distinct", "case", "when", "then", "else", "end"
        );
        Collections.addAll(ALLOWED_FUNCTIONS,
            "abs", "ceil", "ceiling", "coalesce", "concat", "concat_ws", "date", "ifnull",
            "length", "lower", "ltrim", "nullif", "round", "rtrim", "substr", "substring", "trim", "upper"
        );
    }

    private final EntityMeta meta;
    private final RowConstraint scope;
    private final GuardedSqlExecutor executor;
    private final JdbcDialect dialect;
    private final int maxParameters;
    private final JdbcPaginationPolicy paginationPolicy;

    public JdbcEntityDaoCustomMethodExecutor(
        EntityMeta meta,
        RowConstraint scope,
        GuardedSqlExecutor executor,
        JdbcDialect dialect,
        int maxParameters
    ) {
        this(meta, scope, executor, dialect, maxParameters, new JdbcPaginationPolicy());
    }

    public JdbcEntityDaoCustomMethodExecutor(
        EntityMeta meta,
        RowConstraint scope,
        GuardedSqlExecutor executor,
        JdbcDialect dialect,
        int maxParameters,
        JdbcPaginationPolicy paginationPolicy
    ) {
        if (meta == null || scope == null || executor == null) {
            throw new ValidationException("DAO 自定义方法执行上下文不能为空");
        }
        if (maxParameters <= 0) {
            throw new ValidationException("DAO SQL 参数上限必须大于 0");
        }
        this.meta = meta;
        this.scope = scope;
        this.executor = executor;
        this.dialect = dialect == null ? StandardJdbcDialect.GENERIC : dialect;
        this.maxParameters = maxParameters;
        this.paginationPolicy = paginationPolicy == null ? new JdbcPaginationPolicy() : paginationPolicy;
    }

    /** 启动期校验方法的注解、SQL、参数和返回类型。 */
    public static void validate(Method method, EntityMeta meta, JdbcDialect dialect, int maxParameters) {
        validate(method, meta, dialect, maxParameters, new JdbcPaginationPolicy());
    }

    /** 启动期校验方法的注解、SQL、参数和返回类型。 */
    public static void validate(
        Method method,
        EntityMeta meta,
        JdbcDialect dialect,
        int maxParameters,
        JdbcPaginationPolicy paginationPolicy
    ) {
        Annotation annotation = annotation(method);
        if (annotation == null) {
            throw new ValidationException("DAO 自定义方法必须声明 @EntQuery 或 @EntCommand: " + method);
        }
        ParsedSql parsed = parse(
            sql(annotation),
            method,
            meta,
            dialect,
            EntityAccessScope.unrestricted().getRowConstraint()
        );
        QueryReturn queryReturn = validateReturnType(method, isQuery(annotation), parsed);
        String pageParameter = validatePageContract(method, queryReturn, parsed);
        validateParameters(method, parsed.parameterNames, parsed.parameterBindings, false, pageParameter);
        if (parsed.parameterNames.size() > maxParameters) {
            throw new ValidationException("DAO 自定义方法参数数量超过上限: " + maxParameters + ": " + method);
        }
    }

    /** 执行已在启动期校验过的 DAO 自定义方法。 */
    public Object invoke(Method method, Object[] args) {
        Annotation annotation = annotation(method);
        ParsedSql parsed = parse(sql(annotation), method, meta, dialect, scope);
        QueryReturn queryReturn = isQuery(annotation)
            ? validateReturnType(method, true, parsed)
            : null;
        String pageParameter = validatePageContract(method, queryReturn, parsed);
        PageQuery pageQuery = pageParameter == null ? null : pageQueryArgument(method, args, pageParameter);
        if (queryReturn != null && queryReturn.kind == QueryKind.PAGE) {
            validatePageQuery(pageQuery, method);
        }
        validateParameters(method, parsed.parameterNames, parsed.parameterBindings, true, pageParameter);
        BoundSql bound = bind(parsed, method, args, pageParameter);
        if (bound.args.size() > maxParameters) {
            throw new ValidationException("DAO 自定义方法参数数量超过上限: " + maxParameters + ": " + method);
        }
        DefaultExecutionContext context = context(method, "main");
        if (!isQuery(annotation)) {
            int rows = executor.update(bound.sql, bound.args, context);
            if (method.getReturnType() == Long.TYPE || method.getReturnType() == Long.class) {
                return Long.valueOf(rows);
            }
            return Integer.valueOf(rows);
        }

        if (queryReturn != null && queryReturn.kind == QueryKind.PAGE) {
            return executePage(bound, queryReturn, pageQuery, method);
        }
        if (queryReturn != null && queryReturn.kind != QueryKind.LIST) {
            StringBuilder limitedSql = new StringBuilder(bound.sql);
            dialect.appendFindOneClause(limitedSql, bound.args);
            bound = new BoundSql(limitedSql.toString(), bound.args, bound.select);
        }
        List<Map<String, Object>> rows = executor.queryForList(bound.sql, bound.args, context);
        if (queryReturn.kind == QueryKind.LIST) {
            List<Object> result = new ArrayList<Object>();
            for (Map<String, Object> row : rows) {
                result.add(new JdbcReflectiveMapper().mapRow(row, queryReturn.elementType));
            }
            return result;
        }
        if (rows.size() > 1) {
            throw new QueryNotUniqueException("DAO 自定义查询命中多条记录: " + method);
        }
        if (queryReturn.kind == QueryKind.OPTIONAL) {
            return rows.isEmpty()
                ? Optional.empty()
                : Optional.ofNullable(new JdbcReflectiveMapper().mapRow(rows.get(0), queryReturn.elementType));
        }
        if (rows.isEmpty()) {
            throw new NotFoundException("DAO 自定义查询未命中记录: " + method);
        }
        return new JdbcReflectiveMapper().mapRow(rows.get(0), queryReturn.elementType);
    }

    private PageResult<?> executePage(
        BoundSql bound,
        QueryReturn queryReturn,
        PageQuery pageQuery,
        Method method
    ) {
        if (bound.select == null) {
            throw new ValidationException("分页查询缺少结构化 SELECT 片段: " + method);
        }
        String dataSql = pageSql(bound.select, pageQuery, method);
        List<Object> dataArgs = new ArrayList<Object>(bound.select.dataArgs);
        if (pageQuery.getSorts().isEmpty()) {
            dataArgs.addAll(bound.select.existingOrderArgs);
        }
        StringBuilder paged = new StringBuilder(dataSql);
        dialect.appendPageClause(
            paged,
            pageQuery.getPageSize() + (pageQuery.getCountMode() == CountMode.NONE ? 1 : 0),
            pageOffset(pageQuery, method),
            dataArgs
        );
        DefaultExecutionContext dataContext = context(method, "main");
        List<Map<String, Object>> rows = executor.queryForList(paged.toString(), dataArgs, dataContext);
        boolean hasNext = rows.size() > pageQuery.getPageSize();
        if (hasNext) {
            rows = new ArrayList<Map<String, Object>>(rows.subList(0, pageQuery.getPageSize()));
        }
        List<Object> items = new ArrayList<Object>(rows.size());
        JdbcReflectiveMapper mapper = new JdbcReflectiveMapper();
        for (Map<String, Object> row : rows) {
            items.add(mapper.mapRow(row, queryReturn.elementType));
        }
        Long total = null;
        if (pageQuery.getCountMode() == CountMode.ALWAYS) {
            Object count = executor.queryForObject(
                bound.select.countSql,
                new ArrayList<Object>(bound.select.countArgs),
                context(method, "count")
            );
            total = Long.valueOf(count == null ? 0L : ((Number) count).longValue());
            hasNext = total.longValue() > (long) pageOffset(pageQuery, method) + pageQuery.getPageSize();
        }
        if (total == null) {
            return PageResult.withoutTotal(
                items, pageQuery.getPageNumber(), pageQuery.getPageSize(), hasNext
            );
        }
        PageResult<Object> result = new PageResult<Object>(
            items, total.longValue(), pageQuery.getPageNumber(), pageQuery.getPageSize()
        );
        result.setHasNext(Boolean.valueOf(hasNext));
        return result;
    }

    private DefaultExecutionContext context(Method method, String phase) {
        DefaultExecutionContext context = new DefaultExecutionContext(
            meta.getEntityName() + "|DAO|" + method.getName(), null
        );
        context.getAttributes().put("operationDomain", "ENTITY_DAO");
        context.getAttributes().put("operation", method.getName());
        context.getAttributes().put("phase", phase);
        return context;
    }

    /** 生成分页数据 SQL：请求排序覆盖声明排序，并始终补充主键稳定排序。 */
    private String pageSql(BoundSelectSql boundSelect, PageQuery pageQuery, Method method) {
        String orderBy = renderPageOrder(boundSelect, pageQuery, method);
        return boundSelect.dataSql + " order by " + orderBy;
    }

    private String renderPageOrder(
        BoundSelectSql boundSelect,
        PageQuery pageQuery,
        Method method
    ) {
        SelectSqlStructure structure = boundSelect.structure;
        List<String> expressions = new ArrayList<String>();
        Set<String> sortedFields = new HashSet<String>();
        List<QuerySort> requested = pageQuery.getSorts();
        if (requested != null && !requested.isEmpty()) {
            for (QuerySort sort : requested) {
                if (sort == null || sort.getField() == null || !SIMPLE_IDENTIFIER.matcher(sort.getField()).matches()) {
                    throw new ValidationException("分页排序字段不合法: " + method);
                }
                if (sort.getTarget() != null && sort.getTarget() != SortTarget.AUTO
                    && sort.getTarget() != SortTarget.FIELD) {
                    throw new ValidationException("分页首轮只支持实体字段排序: " + method);
                }
                EntityFieldMeta field = meta.resolveFieldMeta(sort.getField());
                if (field == null || field.isRelation() || !field.isSortable()) {
                    throw new ValidationException("分页排序字段未列入实体白名单: " + sort.getField() + ": " + method);
                }
                SortDirection direction = sort.getDirection();
                if (direction == null) {
                    throw new ValidationException("分页排序方向不能为空: " + method);
                }
                expressions.add(qualifiedColumn(structure.tableAlias, field.getColumnName()) + " "
                    + direction.name().toLowerCase(java.util.Locale.ROOT));
                sortedFields.add(field.getFieldName().toLowerCase());
            }
        } else if (!boundSelect.existingOrderSql.isEmpty()) {
            expressions.add(boundSelect.existingOrderSql);
            if (structure.existingOrderContainsId) {
                sortedFields.add(meta.getIdField().toLowerCase());
            }
        }
        if (!sortedFields.contains(meta.getIdField().toLowerCase())) {
            String idColumn = meta.resolveColumn(meta.getIdField());
            expressions.add(qualifiedColumn(structure.tableAlias, idColumn) + " asc");
        }
        return String.join(", ", expressions);
    }

    private String qualifiedColumn(String alias, String column) {
        return (alias == null ? "" : alias + ".") + dialect.quoteIdentifier(column);
    }

    private static Annotation annotation(Method method) {
        EntQuery query = method.getAnnotation(EntQuery.class);
        EntCommand command = method.getAnnotation(EntCommand.class);
        if (query != null && command != null) {
            throw new ValidationException("DAO 自定义方法不能同时声明 @EntQuery 和 @EntCommand: " + method);
        }
        return query == null ? command : query;
    }

    private static boolean isQuery(Annotation annotation) {
        return annotation instanceof EntQuery;
    }

    private static String sql(Annotation annotation) {
        if (annotation instanceof EntQuery) {
            return ((EntQuery) annotation).value();
        }
        if (annotation instanceof EntCommand) {
            return ((EntCommand) annotation).value();
        }
        throw new ValidationException("DAO 自定义方法必须声明 @EntQuery 或 @EntCommand");
    }

    private static ParsedSql parse(
        String source,
        Method method,
        EntityMeta meta,
        JdbcDialect dialect,
        RowConstraint scope
    ) {
        String sql = source == null ? "" : source.trim();
        if (sql.isEmpty()) {
            throw new ValidationException("DAO 自定义 SQL 不能为空: " + method);
        }
        rejectUnsafeLexemes(sql, method);
        List<Token> tokens = tokens(sql);
        if (tokens.isEmpty()) {
            throw new ValidationException("DAO 自定义 SQL 不能为空: " + method);
        }
        for (Token token : tokens) {
            if ("union".equals(token.lower) || "with".equals(token.lower)) {
                throw unsupportedSql(method, "UNION/CTE");
            }
        }
        int first = 0;
        String command = tokens.get(first).lower;
        boolean query = method.getAnnotation(EntQuery.class) != null;
        if (query && !"select".equals(command)) {
            throw new ValidationException("@EntQuery 只允许 SELECT SQL: " + method);
        }
        if (!query && !("update".equals(command) || "delete".equals(command))) {
            throw new ValidationException("@EntCommand 只允许 UPDATE 或 DELETE SQL: " + method);
        }
        rejectNestedStatements(tokens, method);
        if (query) {
            rejectUnsupportedQueryConstructs(tokens, method);
            if (keyword(tokens, "limit", 0) != null || keyword(tokens, "offset", 0) != null) {
                throw unsupportedSql(method, "自定义分页");
            }
        }
        TablePart table = query ? parseSelectTable(tokens, sql, meta, dialect, method)
            : parseWriteTable(tokens, sql, meta, dialect, method);
        rejectAdditionalTables(tokens, table, method);
        if (!query) {
            rejectUnsupportedWriteClauses(tokens, method);
        }
        validateIdentifiers(tokens, table, meta, method);
        if (!query && "update".equals(command)) {
            validateUpdateAssignments(tokens, table, meta, method);
        }
        ClausePosition where = keyword(tokens, "where", 0);
        if (where != null && where.depth != 0) {
            throw unsupportedSql(method, "嵌套 WHERE");
        }
        ClausePosition insertion = query ? firstClause(tokens, "group", "having", "order", "limit", "offset", "for") : null;
        int whereEnd = insertion == null ? sql.length() : insertion.start;
        String governance = governancePredicate(meta, table.alias, scope, method, dialect);
        boolean logicalDelete = !query && "delete".equals(command)
            && meta.getLogicDeleteField() != null && !meta.getLogicDeleteField().trim().isEmpty();
        String rewritten;
        if (logicalDelete) {
            String tablePrefix = "update " + table.tableName
                + (table.alias == null ? "" : " " + table.alias)
                + " set " + dialect.quoteIdentifier(meta.resolveColumn(meta.getLogicDeleteField()))
                + " = " + namedFrameworkParameter("logic_deleted");
            String originalWhere = where == null
                ? ""
                : sql.substring(where.start + "where".length(), whereEnd).trim();
            if (where != null && originalWhere.isEmpty()) {
                throw new ValidationException("DAO 自定义 SQL WHERE 条件不能为空: " + method);
            }
            rewritten = tablePrefix + " where "
                + (originalWhere.isEmpty()
                    ? SqlExpression.raw(governance).render()
                    : SqlExpression.and(SqlExpression.parse(originalWhere, method),
                        SqlExpression.raw(governance)).render());
        } else if (where == null) {
            rewritten = sql.substring(0, whereEnd) + " where "
                + SqlExpression.raw(governance).render() + sql.substring(whereEnd);
        } else {
            String businessWhere = sql.substring(where.start + "where".length(), whereEnd).trim();
            if (businessWhere.isEmpty()) {
                throw new ValidationException("DAO 自定义 SQL WHERE 条件不能为空: " + method);
            }
            rewritten = sql.substring(0, where.start) + "where "
                + SqlExpression.and(SqlExpression.parse(businessWhere, method),
                    SqlExpression.raw(governance)).render() + sql.substring(whereEnd);
        }
        List<String> parameterNames = namedParameterNames(rewritten, method);
        Map<String, ParameterBinding> parameterBindings = parameterBindings(tokens, table, meta, method);
        SelectSqlStructure select = query
            ? SelectSqlStructure.parse(rewritten, table, meta, method)
            : null;
        return new ParsedSql(
            rewritten,
            parameterNames,
            parameterBindings,
            query,
            table,
            keyword(tokens, "distinct", 0) != null,
            select
        );
    }

    private static TablePart parseSelectTable(
        List<Token> tokens, String sql, EntityMeta meta, JdbcDialect dialect, Method method
    ) {
        ClausePosition from = requiredKeyword(tokens, "from", method);
        if (from.depth != 0) {
            throw unsupportedSql(method, "嵌套 FROM");
        }
        return parseTable(tokens, from.index + 1, meta, method);
    }

    private static TablePart parseWriteTable(
        List<Token> tokens, String sql, EntityMeta meta, JdbcDialect dialect, Method method
    ) {
        int tableIndex;
        if ("update".equals(tokens.get(0).lower)) {
            tableIndex = 1;
            requiredKeyword(tokens, "set", method);
        } else {
            if (tokens.size() < 2 || !"from".equals(tokens.get(1).lower)) {
                throw new ValidationException("DELETE SQL 必须包含 FROM: " + method);
            }
            tableIndex = 2;
        }
        return parseTable(tokens, tableIndex, meta, method);
    }

    private static TablePart parseTable(List<Token> tokens, int index, EntityMeta meta, Method method) {
        if (index >= tokens.size()) {
            throw new ValidationException("DAO 自定义 SQL 缺少目标表: " + method);
        }
        Token table = tokens.get(index);
        if (!SIMPLE_IDENTIFIER.matcher(table.text).matches() || !sameIdentifier(table.text, meta.getTable())) {
            throw new ValidationException("DAO 自定义 SQL 目标表与实体不一致: " + table.text + ", " + method);
        }
        int next = index + 1;
        String alias = null;
        if (next < tokens.size() && "as".equals(tokens.get(next).lower)) {
            next++;
            alias = aliasToken(tokens, next, method);
            next++;
        } else if (next < tokens.size() && !STOP_WORDS.contains(tokens.get(next).lower)
            && !"set".equals(tokens.get(next).lower)) {
            alias = aliasToken(tokens, next, method);
            next++;
        }
        return new TablePart(table.text, alias, next);
    }

    private static void rejectAdditionalTables(List<Token> tokens, TablePart table, Method method) {
        boolean clauseStarted = false;
        for (int i = table.afterIndex; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.depth == 0 && isAllowedTableClause(token.lower)) {
                clauseStarted = true;
            }
            if (!clauseStarted && token.depth == 0 && ",".equals(token.text)) {
                throw unsupportedSql(method, "顶层逗号多表");
            }
            if (i == table.afterIndex && token.depth == 0 && !isAllowedTableClause(token.lower)) {
                throw unsupportedSql(method, "表名或别名之后的语句");
            }
        }
    }

    private static void rejectUnsupportedWriteClauses(List<Token> tokens, Method method) {
        for (Token token : tokens) {
            if (token.depth != 0) {
                continue;
            }
            if ("order".equals(token.lower) || "limit".equals(token.lower)
                || "offset".equals(token.lower) || "for".equals(token.lower)
                || "group".equals(token.lower) || "having".equals(token.lower)) {
                throw unsupportedSql(method, "UPDATE/DELETE 的排序或分页子句");
            }
        }
    }

    private static boolean isAllowedTableClause(String token) {
        return "where".equals(token) || "group".equals(token) || "having".equals(token)
            || "order".equals(token) || "limit".equals(token) || "offset".equals(token)
            || "for".equals(token) || "set".equals(token);
    }

    private static String aliasToken(List<Token> tokens, int index, Method method) {
        if (index >= tokens.size() || !SIMPLE_IDENTIFIER.matcher(tokens.get(index).text).matches()) {
            throw new ValidationException("DAO 自定义 SQL 表别名不合法: " + method);
        }
        return tokens.get(index).text;
    }

    private static void validateIdentifiers(
        List<Token> tokens, TablePart table, EntityMeta meta, Method method
    ) {
        boolean skipAlias = false;
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            String value = token.text;
            String lower = token.lower;
            if (skipAlias) {
                skipAlias = false;
                continue;
            }
            if ("as".equals(lower)) {
                skipAlias = true;
                continue;
            }
            if (!SQL_KEYWORDS.contains(lower) && isFunctionCall(tokens, i)) {
                if (!ALLOWED_FUNCTIONS.contains(lower)) {
                    throw unsupportedSql(method, "未列入白名单的函数: " + value);
                }
                continue;
            }
            if (SQL_KEYWORDS.contains(lower) || ",".equals(value) || isOperator(value)
                || value.startsWith(":") || NUMBER_LITERAL.matcher(value).matches()
                || isQuotedLiteral(value) || "*".equals(value)) {
                continue;
            }
            if (value.endsWith(".") && (value.substring(0, value.length() - 1).equalsIgnoreCase(table.tableName)
                || (table.alias != null
                    && value.substring(0, value.length() - 1).equalsIgnoreCase(table.alias)))) {
                continue;
            }
            if (table.tableName.equalsIgnoreCase(value)
                || (table.alias != null && table.alias.equalsIgnoreCase(value))) {
                continue;
            }
            if (i + 1 < tokens.size() && tokens.get(i + 1).depth > token.depth) {
                // 函数名；函数内部字段仍会在后续 token 中校验。
                continue;
            }
            if (resolveField(meta, table.alias, value) == null) {
                throw new ValidationException("DAO 自定义 SQL 字段未注册: " + value + ": " + method);
            }
        }
    }

    private static void validateUpdateAssignments(
        List<Token> tokens, TablePart table, EntityMeta meta, Method method
    ) {
        ClausePosition set = requiredKeyword(tokens, "set", method);
        ClausePosition where = keyword(tokens, "where", 0);
        int end = where == null ? tokens.size() : where.index;
        boolean expectField = true;
        boolean expectEquals = false;
        boolean hasValue = false;
        for (int i = set.index + 1; i < end; i++) {
            Token token = tokens.get(i);
            if (token.depth != 0) {
                if (!expectField && !expectEquals) {
                    hasValue = true;
                }
                continue;
            }
            if (expectField) {
                String fieldName = resolveField(meta, table.alias, token.text);
                if (fieldName == null) {
                    throw new ValidationException("DAO UPDATE 写入字段未注册: " + token.text + ": " + method);
                }
                EntityFieldMeta field = meta.resolveFieldMeta(fieldName);
                if (fieldName.equals(meta.getIdField()) || field.isScopeField()
                    || fieldName.equals(meta.getLogicDeleteField()) || field.isRelation()
                    || !field.isWritable() || field.isImmutable()) {
                    throw new ValidationException("DAO UPDATE 不允许修改主键、范围、逻辑删除或不可写字段: " + token.text);
                }
                expectField = false;
                expectEquals = true;
            } else if (expectEquals) {
                if (!"=".equals(token.text)) {
                    throw new ValidationException("DAO UPDATE SET 赋值缺少等号: " + method);
                }
                expectEquals = false;
                hasValue = false;
            } else if (",".equals(token.text)) {
                if (!hasValue) {
                    throw new ValidationException("DAO UPDATE SET 赋值不能为空: " + method);
                }
                expectField = true;
                hasValue = false;
            } else {
                hasValue = true;
            }
        }
        if (expectField || expectEquals || !hasValue) {
            throw new ValidationException("DAO UPDATE SET 赋值不完整: " + method);
        }
    }

    private static String resolveField(EntityMeta meta, String alias, String expression) {
        String[] parts = expression.split("\\.");
        String field = parts.length == 1 ? parts[0] : parts.length == 2 ? parts[1] : null;
        if (field == null || (parts.length == 2
            && !parts[0].equalsIgnoreCase(meta.getTable())
            && (alias == null || !alias.equalsIgnoreCase(parts[0])))) {
            return null;
        }
        if (meta.resolveFieldMeta(field) != null) {
            return field;
        }
        for (Map.Entry<String, EntityFieldMeta> entry : meta.getFieldMetas().entrySet()) {
            if (entry.getValue().getColumnName().equalsIgnoreCase(field)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static boolean isQuotedLiteral(String value) {
        return value.length() >= 2 && ((value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'')
            || (value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"'));
    }

    private static boolean isOperator(String value) {
        return "=".equals(value) || "<".equals(value) || ">".equals(value)
            || "<=".equals(value) || ">=".equals(value) || "<>".equals(value)
            || "!=".equals(value) || "+".equals(value) || "-".equals(value)
            || "*".equals(value) || "/".equals(value) || "%".equals(value);
    }

    private static boolean isFunctionCall(List<Token> tokens, int index) {
        return index >= 0 && index + 1 < tokens.size()
            && tokens.get(index + 1).depth > tokens.get(index).depth;
    }

    private static String governancePredicate(
        EntityMeta meta,
        String alias,
        RowConstraint scope,
        Method method,
        JdbcDialect dialect
    ) {
        List<String> predicates = new ArrayList<String>();
        appendScope(meta, alias, scope, predicates, method, dialect, new int[] {0});
        appendNotDeleted(meta, alias, predicates, method, dialect);
        if (predicates.isEmpty()) {
            return "1 = 1";
        }
        return String.join(" and ", predicates);
    }

    private static void appendScope(
        EntityMeta meta,
        String alias,
        RowConstraint constraint,
        List<String> predicates,
        Method method,
        JdbcDialect dialect,
        int[] parameterIndex
    ) {
        if (constraint == null) {
            throw new ValidationException("DAO 范围不能为空: " + method);
        }
        switch (constraint.getKind()) {
            case UNRESTRICTED:
                return;
            case AND:
                for (RowConstraint child : constraint.getChildren()) {
                    appendScope(meta, alias, child, predicates, method, dialect, parameterIndex);
                }
                return;
            case PREDICATE:
                EntityFieldMeta field = meta.resolveFieldMeta(constraint.getField());
                if (field == null || !field.isScopeField()) {
                    throw new ValidationException("DAO 范围字段必须声明为 scopeField: " + constraint.getField());
                }
                String qualified = column(meta, alias, constraint.getField(), dialect);
                if (constraint.getOperator() == RowConstraintOperator.EQ) {
                    if (constraint.getValues().size() != 1 || constraint.getValues().get(0) == null) {
                        throw new ValidationException("DAO 等值范围值不能为 NULL: " + constraint.getField());
                    }
                    predicates.add(qualified + " = :" + scopeParameter(parameterIndex[0]++));
                    return;
                }
                if (constraint.getOperator() == RowConstraintOperator.IN) {
                    if (constraint.getValues().isEmpty()) {
                        predicates.add("1 = 0");
                        return;
                    }
                    List<String> placeholders = new ArrayList<String>();
                    for (Object value : constraint.getValues()) {
                        if (value == null) {
                            throw new ValidationException("DAO IN 范围值不能为 NULL: " + constraint.getField());
                        }
                        placeholders.add(":" + scopeParameter(parameterIndex[0]++));
                    }
                    predicates.add(qualified + " in (" + String.join(",", placeholders) + ")");
                    return;
                }
                throw new ValidationException("DAO 范围操作符无法编译: " + constraint.getOperator());
            case OR:
            case NOT:
            default:
                throw new ValidationException("DAO 范围表达式无法编译: " + constraint.getKind());
        }
    }

    private static String scopeParameter(int index) {
        return RESERVED_PARAMETER_PREFIX + "scope_" + index;
    }

    private static void appendNotDeleted(
        EntityMeta meta, String alias, List<String> predicates, Method method, JdbcDialect dialect
    ) {
        if (meta.getLogicDeleteField() == null || meta.getLogicDeleteField().trim().isEmpty()) {
            return;
        }
        predicates.add(column(meta, alias, meta.getLogicDeleteField(), dialect) + " = "
            + namedFrameworkParameter("logic_delete"));
    }

    private static String namedFrameworkParameter(String name) {
        return ":" + RESERVED_PARAMETER_PREFIX + name;
    }

    private static String column(EntityMeta meta, String alias, String field, JdbcDialect dialect) {
        String column = meta.resolveColumn(field);
        if (column == null) {
            throw new ValidationException("DAO 治理字段未映射为列: " + field);
        }
        return (alias == null ? "" : alias + ".") + dialect.quoteIdentifier(column);
    }

    private static QueryReturn validateReturnType(Method method, boolean query, ParsedSql parsed) {
        if (!query) {
            Class<?> returnType = method.getReturnType();
            if (!(returnType == Integer.TYPE || returnType == Integer.class
                || returnType == Long.TYPE || returnType == Long.class)) {
                throw new ValidationException("@EntCommand 返回类型必须是 int 或 long: " + method);
            }
            return null;
        }
        return QueryReturn.resolve(method.getGenericReturnType(), method);
    }

    /** 校验分页方法必须有且仅有一个 PageQuery 参数。 */
    private static String validatePageContract(Method method, QueryReturn queryReturn, ParsedSql parsed) {
        int pageCount = 0;
        String pageParameter = null;
        for (Parameter parameter : method.getParameters()) {
            if (PageQuery.class.equals(parameter.getType())) {
                pageCount++;
                pageParameter = parameter.getName();
            }
        }
        if (queryReturn != null && queryReturn.kind == QueryKind.PAGE) {
            if (parsed.distinct) {
                throw unsupportedSql(method, "分页查询中的 DISTINCT");
            }
            if (pageCount != 1) {
                throw new ValidationException("@EntQuery PageResult<T> 必须声明唯一的 PageQuery 参数: " + method);
            }
            if (parsed.parameterNames.contains(pageParameter)) {
                throw new ValidationException("PageQuery 参数不能作为 SQL 命名参数绑定: " + method);
            }
            return pageParameter;
        }
        if (pageCount > 0) {
            throw new ValidationException("只有返回 PageResult<T> 的 @EntQuery 才能声明 PageQuery 参数: " + method);
        }
        return null;
    }

    private static PageQuery pageQueryArgument(Method method, Object[] args, String parameterName) {
        Parameter[] parameters = method.getParameters();
        Object[] actual = args == null ? new Object[0] : args;
        if (parameters.length != actual.length) {
            throw new ValidationException("DAO 方法参数数量不匹配: " + method);
        }
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(parameterName)) {
                if (!(actual[i] instanceof PageQuery)) {
                    throw new ValidationException("PageQuery 参数不能为空且类型必须正确: " + method);
                }
                return (PageQuery) actual[i];
            }
        }
        throw new ValidationException("DAO 方法缺少 PageQuery 参数: " + method);
    }

    private void validatePageQuery(PageQuery pageQuery, Method method) {
        if (pageQuery == null) {
            throw new ValidationException("PageQuery 参数不能为空: " + method);
        }
        if (pageQuery.getPageNumber() < 1 || pageQuery.getPageSize() < 1) {
            throw new ValidationException("分页页码必须从 1 开始且页大小必须大于 0: " + method);
        }
        if (pageQuery.getPageSize() > paginationPolicy.getMaxPageSize()) {
            throw new ValidationException("分页页大小超过上限 " + paginationPolicy.getMaxPageSize() + ": " + method);
        }
        long offset = pageOffset(pageQuery, method);
        if (offset > paginationPolicy.getMaxOffset()) {
            throw new ValidationException("分页偏移超过上限 " + paginationPolicy.getMaxOffset() + ": " + method);
        }
        if (pageQuery.getSorts() == null) {
            throw new ValidationException("分页排序参数不能为空: " + method);
        }
        if (pageQuery.getCountMode() == null) {
            throw new ValidationException("分页计数模式不能为空: " + method);
        }
    }

    private static int pageOffset(PageQuery pageQuery, Method method) {
        long offset = (pageQuery.getPageNumber() - 1L) * pageQuery.getPageSize();
        if (offset > Integer.MAX_VALUE) {
            throw new ValidationException("分页偏移超出数据库方言支持范围: " + method);
        }
        return (int) offset;
    }

    private static void validateParameters(
        Method method,
        List<String> names,
        Map<String, ParameterBinding> parameterBindings,
        boolean allowScopeFramework,
        String ignoredParameter
    ) {
        Set<String> declared = new HashSet<String>();
        for (Parameter parameter : method.getParameters()) {
            if (!parameter.isNamePresent()) {
                throw new ValidationException("DAO 自定义方法参数缺少编译期名称，请启用 -parameters: " + method);
            }
            if (parameter.getName().startsWith(RESERVED_PARAMETER_PREFIX)) {
                throw new ValidationException(
                    "DAO 方法参数不能使用保留前缀 " + RESERVED_PARAMETER_PREFIX + ": " + parameter.getName()
                );
            }
            declared.add(parameter.getName());
        }
        for (String name : names) {
            if ((RESERVED_PARAMETER_PREFIX + "logic_delete").equals(name)
                || (RESERVED_PARAMETER_PREFIX + "logic_deleted").equals(name)
                || (allowScopeFramework && isGeneratedScopeParameter(name))) {
                continue;
            }
            if (name.startsWith(RESERVED_PARAMETER_PREFIX)) {
                throw new ValidationException("DAO 方法参数不能使用保留前缀 " + RESERVED_PARAMETER_PREFIX + ": " + name);
            }
            if (!declared.contains(name)) {
                throw new ValidationException("DAO SQL 命名参数未声明: " + name + ": " + method);
            }
            if (!parameterBindings.containsKey(name)) {
                throw new ValidationException("DAO SQL 参数无法推断实体字段类型: " + name + ": " + method);
            }
        }
        for (Parameter parameter : method.getParameters()) {
            if (parameter.getName().equals(ignoredParameter)) {
                continue;
            }
            if (!names.contains(parameter.getName())) {
                throw new ValidationException("DAO 方法参数未在 SQL 中使用: " + parameter.getName() + ": " + method);
            }
        }
    }

    private static boolean isGeneratedScopeParameter(String name) {
        String prefix = RESERVED_PARAMETER_PREFIX + "scope_";
        if (!name.startsWith(prefix) || name.length() == prefix.length()) {
            return false;
        }
        for (int i = prefix.length(); i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static void rejectUnsupportedQueryConstructs(List<Token> tokens, Method method) {
        for (Token token : tokens) {
            if ("group".equals(token.lower) || "having".equals(token.lower) || "over".equals(token.lower)
                || "for".equals(token.lower)) {
                throw unsupportedSql(method, "复杂聚合、去重或窗口函数");
            }
            if ("count".equals(token.lower) || "sum".equals(token.lower) || "avg".equals(token.lower)
                || "min".equals(token.lower) || "max".equals(token.lower)) {
                throw unsupportedSql(method, "复杂聚合或窗口函数");
            }
        }
    }

    private BoundSql bind(ParsedSql parsed, Method method, Object[] args, String ignoredParameter) {
        String sql = parsed.sql;
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        Parameter[] parameters = method.getParameters();
        Object[] actual = args == null ? new Object[0] : args;
        if (parameters.length != actual.length) {
            throw new ValidationException("DAO 方法参数数量不匹配: " + method);
        }
        for (int i = 0; i < parameters.length; i++) {
            String name = parameters[i].getName();
            if (name.equals(ignoredParameter)) {
                continue;
            }
            ParameterBinding binding = parsed.parameterBindings.get(name);
            if (binding == null) {
                throw new ValidationException("DAO 方法参数无法绑定到实体字段: " + name + ": " + method);
            }
            values.put(name, normalizeParameterValue(binding, actual[i], method));
        }
        appendScopeValues(scope, values, new int[] {0});
        EntityFieldMeta logicDeleteField = meta.getLogicDeleteField() == null
            ? null : meta.resolveFieldMeta(meta.getLogicDeleteField());
        values.put(RESERVED_PARAMETER_PREFIX + "logic_delete",
            JdbcEntityValueBinder.normalize(logicDeleteField, JdbcLogicDeleteValues.notDeleted(meta)));
        values.put(RESERVED_PARAMETER_PREFIX + "logic_deleted",
            JdbcEntityValueBinder.normalize(logicDeleteField, JdbcLogicDeleteValues.deleted(meta)));
        BoundTemplate whole = bindTemplate(parsed.sql, values, method);
        BoundSelectSql select = null;
        if (parsed.select != null) {
            BoundTemplate data = bindTemplate(parsed.select.dataBaseSql, values, method);
            BoundTemplate count = bindTemplate(parsed.select.countBaseSql, values, method);
            BoundTemplate order = bindTemplate(parsed.select.existingOrderSql, values, method);
            select = new BoundSelectSql(
                parsed.select,
                data.sql,
                data.args,
                count.sql,
                count.args,
                order.sql,
                order.args
            );
        }
        return new BoundSql(whole.sql, whole.args, select);
    }

    private static BoundTemplate bindTemplate(
        String sql,
        Map<String, Object> values,
        Method method
    ) {
        StringBuilder result = new StringBuilder();
        List<Object> boundArgs = new ArrayList<Object>();
        int[] last = new int[] {0};
        scanNamedParameters(sql, (name, start, end) -> {
            result.append(sql, last[0], start);
            Object value = values.get(name);
            if (!values.containsKey(name)) {
                throw new ValidationException("DAO SQL 命名参数未绑定: " + name + ": " + method);
            }
            if (value instanceof Collection<?>) {
                Collection<?> collection = (Collection<?>) value;
                if (collection.isEmpty()) {
                    result.append("null");
                } else {
                    int index = 0;
                    for (Object item : collection) {
                        if (index++ > 0) {
                            result.append(",");
                        }
                        result.append("?");
                        boundArgs.add(item);
                    }
                }
            } else {
                result.append("?");
                boundArgs.add(value);
            }
            last[0] = end;
        });
        result.append(sql, last[0], sql.length());
        return new BoundTemplate(result.toString(), boundArgs);
    }

    private static Object normalizeParameterValue(ParameterBinding binding, Object value, Method method) {
        if (binding.collectionParameter && value == null) {
            throw new ValidationException("DAO 集合参数不能为 NULL: " + binding.name + ": " + method);
        }
        if (value instanceof Collection<?>) {
            if (!binding.collectionAllowed) {
                throw new ValidationException("DAO 集合参数只允许出现在 IN 条件中: " + method);
            }
            List<Object> normalized = new ArrayList<Object>();
            for (Object item : (Collection<?>) value) {
                if (item == null) {
                    throw new ValidationException("DAO IN 参数不能包含 NULL: " + binding.name + ": " + method);
                }
                normalized.add(JdbcEntityValueBinder.normalize(binding.field, item));
            }
            return normalized;
        }
        if (binding.collectionParameter) {
            throw new ValidationException("DAO 集合参数必须传入 Collection: " + binding.name + ": " + method);
        }
        return JdbcEntityValueBinder.normalize(binding.field, value);
    }

    private void appendScopeValues(RowConstraint constraint, Map<String, Object> values, int[] parameterIndex) {
        switch (constraint.getKind()) {
            case UNRESTRICTED:
                return;
            case AND:
                for (RowConstraint child : constraint.getChildren()) {
                    appendScopeValues(child, values, parameterIndex);
                }
                return;
            case PREDICATE:
                for (Object value : constraint.getValues()) {
                    values.put(
                        scopeParameter(parameterIndex[0]++),
                        JdbcEntityValueBinder.normalize(meta.resolveFieldMeta(constraint.getField()), value)
                    );
                }
                return;
            case OR:
            case NOT:
            default:
                throw new ValidationException("DAO 范围表达式无法绑定: " + constraint.getKind());
        }
    }

    private static List<String> namedParameterNames(String sql, Method method) {
        List<String> names = new ArrayList<String>();
        scanNamedParameters(sql, (name, start, end) -> {
            if (!names.contains(name)) {
                names.add(name);
            }
        });
        return names;
    }

    private static Map<String, ParameterBinding> parameterBindings(
        List<Token> tokens,
        TablePart table,
        EntityMeta meta,
        Method method
    ) {
        Map<String, ParameterBinding> result = new LinkedHashMap<String, ParameterBinding>();
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (!token.text.startsWith(":")) {
                continue;
            }
            String name = token.text.substring(1);
            if (!SIMPLE_IDENTIFIER.matcher(name).matches()) {
                throw new ValidationException("DAO SQL 命名参数不合法: " + token.text + ": " + method);
            }
            if (name.startsWith(RESERVED_PARAMETER_PREFIX)) {
                throw new ValidationException("DAO SQL 参数不能使用保留前缀 " + RESERVED_PARAMETER_PREFIX + ": " + method);
            }
            EntityFieldMeta field = parameterField(tokens, i, table, meta);
            if (field == null || field.getJavaType() == null) {
                throw new ValidationException("DAO SQL 参数无法推断实体字段类型: " + name + ": " + method);
            }
            boolean collectionAllowed = isInParameter(tokens, i);
            Parameter parameter = parameter(method, name);
            boolean collectionParameter = parameter != null
                && Collection.class.isAssignableFrom(parameter.getType());
            if (collectionParameter && !collectionAllowed) {
                throw new ValidationException("DAO 集合参数只允许出现在 IN 条件中: " + name + ": " + method);
            }
            ParameterBinding previous = result.get(name);
            if (previous != null && (!previous.field.getFieldName().equals(field.getFieldName())
                || previous.collectionAllowed != collectionAllowed)) {
                throw new ValidationException("DAO SQL 参数不能绑定多个字段或混用集合条件: " + name + ": " + method);
            }
            if (previous == null) {
                result.put(name, new ParameterBinding(name, field, collectionAllowed, collectionParameter));
            }
        }
        return result;
    }

    private static Parameter parameter(Method method, String name) {
        for (Parameter parameter : method.getParameters()) {
            if (parameter.isNamePresent() && parameter.getName().equals(name)) {
                return parameter;
            }
        }
        return null;
    }

    private static EntityFieldMeta parameterField(
        List<Token> tokens,
        int parameterIndex,
        TablePart table,
        EntityMeta meta
    ) {
        int left = parameterIndex - 1;
        if (left >= 0 && isFunctionCall(tokens, left)) {
            EntityFieldMeta functionField = fieldExpression(tokens, left, table, meta);
            if (functionField != null) {
                return functionField;
            }
            int functionDepth = tokens.get(left).depth;
            for (int i = left - 1; i >= 0; i--) {
                if (tokens.get(i).depth != functionDepth) {
                    continue;
                }
                if (isParameterOperator(tokens.get(i).lower, tokens.get(i).text)) {
                    EntityFieldMeta field = fieldExpression(tokens, i - 1, table, meta);
                    if (field != null) {
                        return field;
                    }
                    return fieldExpression(tokens, i + 1, table, meta);
                }
            }
        }
        if (left >= 0 && "(".equals(tokens.get(left).text)) {
            left--;
            if (left >= 0 && "in".equals(tokens.get(left).lower)) {
                left--;
            }
        }
        if (left >= 0 && isParameterOperator(tokens.get(left).lower, tokens.get(left).text)) {
            EntityFieldMeta field = fieldExpression(tokens, left - 1, table, meta);
            if (field != null) {
                return field;
            }
        }
        if (left >= 0 && "and".equals(tokens.get(left).lower)) {
            for (int i = left - 1; i >= 0; i--) {
                if (tokens.get(i).depth != tokens.get(left).depth) {
                    continue;
                }
                if ("between".equals(tokens.get(i).lower)) {
                    return fieldToken(tokens, i - 1, table, meta);
                }
                if ("where".equals(tokens.get(i).lower) || "or".equals(tokens.get(i).lower)) {
                    break;
                }
            }
        }
        int right = parameterIndex + 1;
        if (right < tokens.size() && isParameterOperator(tokens.get(right).lower, tokens.get(right).text)) {
            return fieldExpression(tokens, right + 1, table, meta);
        }
        return null;
    }

    private static EntityFieldMeta fieldExpression(
        List<Token> tokens,
        int index,
        TablePart table,
        EntityMeta meta
    ) {
        EntityFieldMeta direct = fieldToken(tokens, index, table, meta);
        if (direct != null || !isFunctionCall(tokens, index)) {
            return direct;
        }
        int functionDepth = tokens.get(index).depth;
        for (int i = index + 1; i < tokens.size() && tokens.get(i).depth > functionDepth; i++) {
            EntityFieldMeta nested = fieldToken(tokens, i, table, meta);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static boolean isInParameter(List<Token> tokens, int parameterIndex) {
        int left = parameterIndex - 1;
        if (left >= 0 && "(".equals(tokens.get(left).text)) {
            left--;
        }
        return left >= 0 && "in".equals(tokens.get(left).lower);
    }

    private static boolean isParameterOperator(String lower, String text) {
        return "=".equals(text) || "<".equals(text) || ">".equals(text)
            || "<=".equals(text) || ">=".equals(text) || "<>".equals(text)
            || "!=".equals(text) || "like".equals(lower) || "in".equals(lower)
            || "between".equals(lower);
    }

    private static EntityFieldMeta fieldToken(
        List<Token> tokens,
        int index,
        TablePart table,
        EntityMeta meta
    ) {
        if (index < 0 || index >= tokens.size()) {
            return null;
        }
        String fieldName = resolveField(meta, table.alias, tokens.get(index).text);
        return fieldName == null ? null : meta.resolveFieldMeta(fieldName);
    }

    private static void rejectUnsafeLexemes(String sql, Method method) {
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean backtick = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (singleQuote) {
                if (ch == '\\' && i + 1 < sql.length()) {
                    i++;
                } else if (ch == '\'' && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    i++;
                } else if (ch == '\'') {
                    singleQuote = false;
                }
                continue;
            }
            if (doubleQuote) {
                if (ch == '\\' && i + 1 < sql.length()) {
                    i++;
                } else if (ch == '"' && i + 1 < sql.length() && sql.charAt(i + 1) == '"') {
                    i++;
                } else if (ch == '"') {
                    doubleQuote = false;
                }
                continue;
            }
            if (backtick) {
                if (ch == '`' && i + 1 < sql.length() && sql.charAt(i + 1) == '`') {
                    i++;
                } else if (ch == '`') {
                    backtick = false;
                }
                continue;
            }
            if (ch == '\'') {
                singleQuote = true;
            } else if (ch == '"') {
                doubleQuote = true;
            } else if (ch == '`') {
                backtick = true;
            } else if (ch == ';' || ch == '#'
                || (ch == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-')
                || (ch == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*')
                || (ch == '*' && i + 1 < sql.length() && sql.charAt(i + 1) == '/')) {
                throw unsupportedSql(method, "SQL 注释或多语句分隔符");
            }
        }
        if (singleQuote || doubleQuote || backtick) {
            throw new ValidationException("DAO 自定义 SQL 引号未闭合: " + method);
        }
    }

    private static void scanNamedParameters(String sql, NamedParameterConsumer consumer) {
        int last = 0;
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean backtick = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '\'' && !doubleQuote && !backtick) {
                if (singleQuote && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    i++;
                } else {
                    singleQuote = !singleQuote;
                }
                continue;
            }
            if (ch == '"' && !singleQuote && !backtick) {
                doubleQuote = !doubleQuote;
                continue;
            }
            if (ch == '`' && !singleQuote && !doubleQuote) {
                backtick = !backtick;
                continue;
            }
            if (ch != ':' || singleQuote || doubleQuote || backtick || (i + 1 >= sql.length())) {
                continue;
            }
            int end = i + 1;
            while (end < sql.length()
                && (Character.isLetterOrDigit(sql.charAt(end)) || sql.charAt(end) == '_')) {
                end++;
            }
            if (end == i + 1) {
                continue;
            }
            consumer.accept(sql.substring(i + 1, end), i, end);
            i = end - 1;
            last = end;
        }
    }

    private static void rejectNestedStatements(List<Token> tokens, Method method) {
        for (Token token : tokens) {
            if (token.depth > 0 && ("select".equals(token.lower) || "update".equals(token.lower)
                || "delete".equals(token.lower) || "insert".equals(token.lower))) {
                throw unsupportedSql(method, "子查询");
            }
        }
        for (Token token : tokens) {
            if ("join".equals(token.lower)) {
                throw unsupportedSql(method, "JOIN");
            }
        }
    }

    private static ClausePosition requiredKeyword(List<Token> tokens, String keyword, Method method) {
        ClausePosition position = keyword(tokens, keyword, 0);
        if (position == null) {
            throw new ValidationException("DAO 自定义 SQL 缺少 " + keyword.toUpperCase() + ": " + method);
        }
        return position;
    }

    private static ClausePosition keyword(List<Token> tokens, String keyword, int from) {
        for (Token token : tokens) {
            if (token.index >= from && token.depth == 0 && keyword.equals(token.lower)) {
                return new ClausePosition(token.start, token.index, token.depth);
            }
        }
        return null;
    }

    private static ClausePosition firstClause(List<Token> tokens, String... keywords) {
        ClausePosition result = null;
        for (String keyword : keywords) {
            ClausePosition position = keyword(tokens, keyword, 0);
            if (position != null && (result == null || position.start < result.start)) {
                result = position;
            }
        }
        return result;
    }

    private static List<Token> tokens(String sql) {
        List<Token> result = new ArrayList<Token>();
        int depth = 0;
        int index = 0;
        int start = -1;
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean backtick = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '\'' && !doubleQuote && !backtick) {
                singleQuote = !singleQuote;
            } else if (ch == '"' && !singleQuote && !backtick) {
                doubleQuote = !doubleQuote;
            } else if (ch == '`' && !singleQuote && !doubleQuote) {
                backtick = !backtick;
            }
            if (singleQuote || doubleQuote || backtick) {
                continue;
            }
            if (ch == '(') {
                if (start >= 0) {
                    result.add(new Token(sql.substring(start, i), start, index++, depth));
                    start = -1;
                }
                depth++;
                continue;
            }
            if (ch == ')') {
                if (start >= 0) {
                    result.add(new Token(sql.substring(start, i), start, index++, depth));
                    start = -1;
                }
                depth--;
                if (depth < 0) {
                    throw new ValidationException("DAO 自定义 SQL 括号不匹配");
                }
                continue;
            }
            if (Character.isWhitespace(ch)) {
                if (start >= 0) {
                    result.add(new Token(sql.substring(start, i), start, index++, depth));
                    start = -1;
                }
                continue;
            }
            if (ch == ',') {
                if (start >= 0) {
                    result.add(new Token(sql.substring(start, i), start, index++, depth));
                    start = -1;
                }
                result.add(new Token(",", i, index++, depth));
                continue;
            }
            if (isOperatorCharacter(ch)) {
                if (start >= 0) {
                    result.add(new Token(sql.substring(start, i), start, index++, depth));
                    start = -1;
                }
                int end = i + 1;
                if (end < sql.length() && (sql.charAt(end) == '=' || sql.charAt(end) == '>')) {
                    end++;
                }
                result.add(new Token(sql.substring(i, end), i, index++, depth));
                i = end - 1;
                continue;
            }
            if (start < 0) {
                start = i;
            }
        }
        if (start >= 0) {
            result.add(new Token(sql.substring(start), start, index, depth));
        }
        if (depth != 0 || singleQuote || doubleQuote || backtick) {
            throw new ValidationException("DAO 自定义 SQL 语法未闭合");
        }
        for (Token token : result) {
            token.lower = token.text.toLowerCase(java.util.Locale.ROOT);
        }
        return result;
    }

    private static boolean isOperatorCharacter(char value) {
        return value == '=' || value == '<' || value == '>' || value == '+' || value == '-'
            || value == '*' || value == '/' || value == '%';
    }

    private static boolean sameIdentifier(String actual, String expected) {
        return actual.equalsIgnoreCase(expected);
    }

    private static ValidationException unsupportedSql(Method method, String detail) {
        return new ValidationException("DAO 自定义 SQL 暂不支持 " + detail + "，请改用专用 Repository: " + method);
    }

    /** 只解析 WHERE 的布尔组合，原子 SQL 保留为不可变片段。 */
    private static final class SqlExpression {
        private final String raw;
        private final String operator;
        private final List<SqlExpression> children;

        private SqlExpression(String raw, String operator, List<SqlExpression> children) {
            this.raw = raw;
            this.operator = operator;
            this.children = children;
        }

        private static SqlExpression raw(String value) {
            return new SqlExpression(value.trim(), null, Collections.emptyList());
        }

        private static SqlExpression and(SqlExpression left, SqlExpression right) {
            return combine("and", left, right);
        }

        private static SqlExpression combine(String operator, SqlExpression left, SqlExpression right) {
            List<SqlExpression> children = new ArrayList<SqlExpression>();
            children.add(left);
            children.add(right);
            return new SqlExpression(null, operator, children);
        }

        private static SqlExpression parse(String source, Method method) {
            String value = stripOuterParentheses(source.trim());
            if (value.isEmpty()) {
                throw new ValidationException("DAO 自定义 SQL WHERE 条件不能为空: " + method);
            }
            List<String> parts = splitTopLevel(value, "or");
            if (parts.size() > 1) {
                return combine("or", parse(parts.get(0), method), parseRemaining(parts, 1, "or", method));
            }
            parts = splitTopLevel(value, "and");
            if (parts.size() > 1) {
                return combine("and", parse(parts.get(0), method), parseRemaining(parts, 1, "and", method));
            }
            return raw(value);
        }

        private static SqlExpression parseRemaining(
            List<String> parts, int start, String operator, Method method
        ) {
            SqlExpression result = parse(parts.get(start), method);
            for (int i = start + 1; i < parts.size(); i++) {
                result = combine(operator, result, parse(parts.get(i), method));
            }
            return result;
        }

        private static List<String> splitTopLevel(String source, String operator) {
            List<String> result = new ArrayList<String>();
            int depth = 0;
            int start = 0;
            boolean betweenPending = false;
            boolean singleQuote = false;
            boolean doubleQuote = false;
            boolean backtick = false;
            for (int i = 0; i < source.length(); i++) {
                char ch = source.charAt(i);
                if (singleQuote) {
                    if (ch == '\'' && i + 1 < source.length() && source.charAt(i + 1) == '\'') {
                        i++;
                    } else if (ch == '\'') {
                        singleQuote = false;
                    }
                    continue;
                }
                if (doubleQuote) {
                    if (ch == '"' && i + 1 < source.length() && source.charAt(i + 1) == '"') {
                        i++;
                    } else if (ch == '"') {
                        doubleQuote = false;
                    }
                    continue;
                }
                if (backtick) {
                    if (ch == '`' && i + 1 < source.length() && source.charAt(i + 1) == '`') {
                        i++;
                    } else if (ch == '`') {
                        backtick = false;
                    }
                    continue;
                }
                if (ch == '\'') {
                    singleQuote = true;
                } else if (ch == '"') {
                    doubleQuote = true;
                } else if (ch == '`') {
                    backtick = true;
                } else if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth--;
                } else if (depth == 0 && keywordAt(source, i, "between")) {
                    betweenPending = true;
                    i += "between".length() - 1;
                } else if (depth == 0 && keywordAt(source, i, operator)
                    && !("and".equals(operator) && betweenPending)) {
                    result.add(source.substring(start, i).trim());
                    start = i + operator.length();
                    i += operator.length() - 1;
                } else if (depth == 0 && "and".equals(operator)
                    && betweenPending && keywordAt(source, i, "and")) {
                    betweenPending = false;
                    i += "and".length() - 1;
                }
            }
            if (!result.isEmpty()) {
                result.add(source.substring(start).trim());
            }
            return result;
        }

        private static boolean keywordAt(String source, int index, String keyword) {
            int end = index + keyword.length();
            if (end > source.length() || !source.regionMatches(true, index, keyword, 0, keyword.length())) {
                return false;
            }
            return (index == 0 || !Character.isJavaIdentifierPart(source.charAt(index - 1)))
                && (end == source.length() || !Character.isJavaIdentifierPart(source.charAt(end)));
        }

        private static String stripOuterParentheses(String source) {
            String value = source;
            while (value.startsWith("(") && value.endsWith(")") && enclosesWholeExpression(value)) {
                value = value.substring(1, value.length() - 1).trim();
            }
            return value;
        }

        private static boolean enclosesWholeExpression(String source) {
            int depth = 0;
            boolean singleQuote = false;
            boolean doubleQuote = false;
            for (int i = 0; i < source.length(); i++) {
                char ch = source.charAt(i);
                if (singleQuote) {
                    if (ch == '\'' && i + 1 < source.length() && source.charAt(i + 1) == '\'') {
                        i++;
                    } else if (ch == '\'') {
                        singleQuote = false;
                    }
                    continue;
                }
                if (doubleQuote) {
                    if (ch == '"' && i + 1 < source.length() && source.charAt(i + 1) == '"') {
                        i++;
                    } else if (ch == '"') {
                        doubleQuote = false;
                    }
                    continue;
                }
                if (ch == '\'') {
                    singleQuote = true;
                } else if (ch == '"') {
                    doubleQuote = true;
                } else if (ch == '(') {
                    depth++;
                } else if (ch == ')' && --depth == 0 && i != source.length() - 1) {
                    return false;
                }
            }
            return depth == 0;
        }

        private String render() {
            if (operator == null) {
                return raw;
            }
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) {
                    result.append(' ').append(operator).append(' ');
                }
                result.append('(').append(children.get(i).render()).append(')');
            }
            return result.toString();
        }
    }

    private interface NamedParameterConsumer {
        void accept(String name, int start, int end);
    }

    private static final class Token {
        private final String text;
        private final int start;
        private final int index;
        private final int depth;
        private String lower;

        private Token(String text, int start, int index, int depth) {
            this.text = text;
            this.start = start;
            this.index = index;
            this.depth = depth;
        }
    }

    private static final class ClausePosition {
        private final int start;
        private final int index;
        private final int depth;

        private ClausePosition(int start, int index, int depth) {
            this.start = start;
            this.index = index;
            this.depth = depth;
        }
    }

    private static final class TablePart {
        private final String tableName;
        private final String alias;
        private final int afterIndex;

        private TablePart(String tableName, String alias, int afterIndex) {
            this.tableName = tableName;
            this.alias = alias;
            this.afterIndex = afterIndex;
        }
    }

    /**
     * 受限单表 SELECT 的结构化片段。
     *
     * <p>解析阶段一次确定 SELECT/FROM/WHERE 和 ORDER BY 的边界，执行阶段只组合已确认的片段，
     * 不再从已绑定 SQL 中重新猜测子句位置。</p>
     */
    private static final class SelectSqlStructure {
        private final String dataBaseSql;
        private final String countBaseSql;
        private final String existingOrderSql;
        private final String tableAlias;
        private final boolean existingOrderContainsId;

        private SelectSqlStructure(
            String dataBaseSql,
            String countBaseSql,
            String existingOrderSql,
            String tableAlias,
            boolean existingOrderContainsId
        ) {
            this.dataBaseSql = dataBaseSql;
            this.countBaseSql = countBaseSql;
            this.existingOrderSql = existingOrderSql;
            this.tableAlias = tableAlias;
            this.existingOrderContainsId = existingOrderContainsId;
        }

        private static SelectSqlStructure parse(
            String source,
            TablePart table,
            EntityMeta meta,
            Method method
        ) {
            List<Token> sqlTokens = tokens(source);
            ClausePosition from = requiredKeyword(sqlTokens, "from", method);
            ClausePosition order = keyword(sqlTokens, "order", 0);
            int end = order == null ? source.length() : order.start;
            String dataBaseSql = source.substring(0, end).trim();
            String countBaseSql = "select count(1) " + source.substring(from.start, end).trim();
            String existingOrderSql = "";
            boolean existingOrderContainsId = false;
            if (order != null) {
                if (order.index + 1 >= sqlTokens.size()
                    || !"by".equals(sqlTokens.get(order.index + 1).lower)) {
                    throw new ValidationException("ORDER BY 语法不完整: " + method);
                }
                int orderStart = sqlTokens.get(order.index + 1).start + 2;
                existingOrderSql = source.substring(orderStart).trim();
                String idColumn = meta.resolveColumn(meta.getIdField());
                for (int i = order.index + 2; i < sqlTokens.size(); i++) {
                    String token = sqlTokens.get(i).text;
                    if (meta.getIdField().equalsIgnoreCase(token)
                        || (idColumn != null && idColumn.equalsIgnoreCase(token))) {
                        existingOrderContainsId = true;
                        break;
                    }
                }
            }
            return new SelectSqlStructure(
                dataBaseSql,
                countBaseSql,
                existingOrderSql,
                table.alias,
                existingOrderContainsId
            );
        }
    }

    private static final class ParsedSql {
        private final String sql;
        private final List<String> parameterNames;
        private final Map<String, ParameterBinding> parameterBindings;
        private final boolean query;
        private final TablePart table;
        private final boolean distinct;
        private final SelectSqlStructure select;

        private ParsedSql(
            String sql,
            List<String> parameterNames,
            Map<String, ParameterBinding> parameterBindings,
            boolean query,
            TablePart table,
            boolean distinct,
            SelectSqlStructure select
        ) {
            this.sql = sql;
            this.parameterNames = parameterNames;
            this.parameterBindings = parameterBindings;
            this.query = query;
            this.table = table;
            this.distinct = distinct;
            this.select = select;
        }
    }

    private static final class ParameterBinding {
        private final String name;
        private final EntityFieldMeta field;
        private final boolean collectionAllowed;
        private final boolean collectionParameter;

        private ParameterBinding(
            String name,
            EntityFieldMeta field,
            boolean collectionAllowed,
            boolean collectionParameter
        ) {
            this.name = name;
            this.field = field;
            this.collectionAllowed = collectionAllowed;
            this.collectionParameter = collectionParameter;
        }
    }

    private static final class BoundSql {
        private final String sql;
        private final List<Object> args;
        private final BoundSelectSql select;

        private BoundSql(String sql, List<Object> args, BoundSelectSql select) {
            this.sql = sql;
            this.args = args;
            this.select = select;
        }
    }

    private static final class BoundSelectSql {
        private final SelectSqlStructure structure;
        private final String dataSql;
        private final List<Object> dataArgs;
        private final String countSql;
        private final List<Object> countArgs;
        private final String existingOrderSql;
        private final List<Object> existingOrderArgs;

        private BoundSelectSql(
            SelectSqlStructure structure,
            String dataSql,
            List<Object> dataArgs,
            String countSql,
            List<Object> countArgs,
            String existingOrderSql,
            List<Object> existingOrderArgs
        ) {
            this.structure = structure;
            this.dataSql = dataSql;
            this.dataArgs = dataArgs;
            this.countSql = countSql;
            this.countArgs = countArgs;
            this.existingOrderSql = existingOrderSql;
            this.existingOrderArgs = existingOrderArgs;
        }
    }

    private static final class BoundTemplate {
        private final String sql;
        private final List<Object> args;

        private BoundTemplate(String sql, List<Object> args) {
            this.sql = sql;
            this.args = args;
        }
    }

    private enum QueryKind {
        SINGLE,
        OPTIONAL,
        LIST,
        PAGE
    }

    private static final class QueryReturn {
        private final QueryKind kind;
        private final Class<?> elementType;

        private QueryReturn(QueryKind kind, Class<?> elementType) {
            this.kind = kind;
            this.elementType = elementType;
        }

        private static QueryReturn resolve(Type type, Method method) {
            if (type instanceof Class<?>) {
                Class<?> result = (Class<?>) type;
                if (result == void.class || result.isPrimitive() || result == Object.class
                    || result == PageResult.class
                    || result.isArray() || result.isInterface()) {
                    throw new ValidationException("@EntQuery 返回类型必须是实体或 DTO: " + method);
                }
                return new QueryReturn(QueryKind.SINGLE, result);
            }
            if (!(type instanceof ParameterizedType)) {
                throw new ValidationException("@EntQuery 不支持动态泛型返回值: " + method);
            }
            ParameterizedType parameterized = (ParameterizedType) type;
            if (!(parameterized.getRawType() instanceof Class<?>)) {
                throw new ValidationException("@EntQuery 返回类型不合法: " + method);
            }
            Class<?> raw = (Class<?>) parameterized.getRawType();
            Type[] arguments = parameterized.getActualTypeArguments();
            if (arguments.length != 1 || !(arguments[0] instanceof Class<?>)) {
                throw new ValidationException("@EntQuery 只支持明确的单层泛型返回值: " + method);
            }
            Class<?> element = (Class<?>) arguments[0];
            if (element.isPrimitive() || element.isInterface() || element == Object.class
                || element.isArray()) {
                throw new ValidationException("@EntQuery 元素类型必须是实体或 DTO: " + method);
            }
            if (raw == Optional.class) {
                return new QueryReturn(QueryKind.OPTIONAL, element);
            }
            if (raw == List.class) {
                return new QueryReturn(QueryKind.LIST, element);
            }
            if (raw == PageResult.class) {
                return new QueryReturn(QueryKind.PAGE, element);
            }
            throw new ValidationException("@EntQuery 只支持实体、Optional<T>、List<T> 或 PageResult<T>: " + method);
        }
    }
}
