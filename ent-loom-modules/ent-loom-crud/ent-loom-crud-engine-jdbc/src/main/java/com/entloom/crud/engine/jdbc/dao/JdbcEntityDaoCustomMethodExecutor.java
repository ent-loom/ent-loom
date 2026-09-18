package com.entloom.crud.engine.jdbc.dao;

import com.entloom.crud.annotations.EntCommand;
import com.entloom.crud.annotations.EntQuery;
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
    private static final String RESERVED_PARAMETER_PREFIX = "__ent_";
    private static final Pattern SIMPLE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");
    private static final Pattern NUMBER_LITERAL = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");
    private static final Set<String> STOP_WORDS = new HashSet<String>();
    private static final Set<String> SQL_KEYWORDS = new HashSet<String>();

    static {
        Collections.addAll(STOP_WORDS, "where", "group", "having", "order", "limit", "offset", "for");
        Collections.addAll(SQL_KEYWORDS,
            "select", "from", "where", "and", "or", "not", "in", "is", "null", "true", "false",
            "update", "delete", "set", "as", "order", "by", "asc", "desc", "like", "between",
            "distinct", "case", "when", "then", "else", "end"
        );
    }

    private final EntityMeta meta;
    private final RowConstraint scope;
    private final GuardedSqlExecutor executor;
    private final JdbcDialect dialect;
    private final int maxParameters;

    public JdbcEntityDaoCustomMethodExecutor(
        EntityMeta meta,
        RowConstraint scope,
        GuardedSqlExecutor executor,
        JdbcDialect dialect,
        int maxParameters
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
    }

    /** 启动期校验方法的注解、SQL、参数和返回类型。 */
    public static void validate(Method method, EntityMeta meta, JdbcDialect dialect, int maxParameters) {
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
        validateReturnType(method, isQuery(annotation), parsed);
        validateParameters(method, parsed.parameterNames, false);
        if (parsed.parameterNames.size() > maxParameters) {
            throw new ValidationException("DAO 自定义方法参数数量超过上限: " + maxParameters + ": " + method);
        }
    }

    /** 执行已在启动期校验过的 DAO 自定义方法。 */
    public Object invoke(Method method, Object[] args) {
        Annotation annotation = annotation(method);
        ParsedSql parsed = parse(sql(annotation), method, meta, dialect, scope);
        validateReturnType(method, isQuery(annotation), parsed);
        validateParameters(method, parsed.parameterNames, true);
        BoundSql bound = bind(parsed.sql, method, args);
        if (bound.args.size() > maxParameters) {
            throw new ValidationException("DAO 自定义方法参数数量超过上限: " + maxParameters + ": " + method);
        }
        DefaultExecutionContext context = new DefaultExecutionContext(
            meta.getEntityName() + "|DAO|" + method.getName(),
            null
        );
        context.getAttributes().put("operationDomain", "ENTITY_DAO");
        context.getAttributes().put("operation", method.getName());
        context.getAttributes().put("phase", "main");
        if (!isQuery(annotation)) {
            int rows = executor.update(bound.sql, bound.args, context);
            if (method.getReturnType() == Long.TYPE || method.getReturnType() == Long.class) {
                return Long.valueOf(rows);
            }
            return Integer.valueOf(rows);
        }

        QueryReturn queryReturn = QueryReturn.resolve(method.getGenericReturnType(), method);
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
        if (sql.endsWith(";")) {
            throw new ValidationException("DAO 自定义 SQL 不允许分号: " + method);
        }
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
        }
        TablePart table = query ? parseSelectTable(tokens, sql, meta, dialect, method)
            : parseWriteTable(tokens, sql, meta, dialect, method);
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
            rewritten = tablePrefix + " where "
                + (originalWhere.isEmpty() ? governance : "(" + originalWhere + ") and (" + governance + ")");
        } else if (where == null) {
            rewritten = sql.substring(0, whereEnd) + " where " + governance + sql.substring(whereEnd);
        } else {
            rewritten = sql.substring(0, whereEnd) + " and (" + governance + ")" + sql.substring(whereEnd);
        }
        List<String> parameterNames = namedParameterNames(rewritten, method);
        return new ParsedSql(rewritten, parameterNames, query, table);
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
        return new TablePart(table.text, alias);
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
        for (int i = set.index + 1; i < end; i++) {
            Token token = tokens.get(i);
            if (token.depth != 0) {
                continue;
            }
            if (expectField) {
                String fieldName = resolveField(meta, table.alias, token.text);
                if (fieldName == null) {
                    throw new ValidationException("DAO UPDATE 写入字段未注册: " + token.text + ": " + method);
                }
                EntityFieldMeta field = meta.resolveFieldMeta(fieldName);
                if (fieldName.equals(meta.getIdField()) || field.isScopeField()
                    || fieldName.equals(meta.getLogicDeleteField())) {
                    throw new ValidationException("DAO UPDATE 不允许修改主键、范围或逻辑删除字段: " + token.text);
                }
                expectField = false;
            } else if (",".equals(token.text)) {
                expectField = true;
            }
        }
        if (expectField) {
            throw new ValidationException("DAO UPDATE SET 赋值不完整: " + method);
        }
    }

    private static String resolveField(EntityMeta meta, String alias, String expression) {
        String[] parts = expression.split("\\.");
        String field = parts.length == 1 ? parts[0] : parts.length == 2 ? parts[1] : null;
        if (field == null || (parts.length == 2 && (alias == null || !alias.equalsIgnoreCase(parts[0])))) {
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

    private static void validateReturnType(Method method, boolean query, ParsedSql parsed) {
        if (!query) {
            Class<?> returnType = method.getReturnType();
            if (!(returnType == Integer.TYPE || returnType == Integer.class
                || returnType == Long.TYPE || returnType == Long.class)) {
                throw new ValidationException("@EntCommand 返回类型必须是 int 或 long: " + method);
            }
            return;
        }
        QueryReturn.resolve(method.getGenericReturnType(), method);
    }

    private static void validateParameters(Method method, List<String> names, boolean allowScopeFramework) {
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
        }
        for (Parameter parameter : method.getParameters()) {
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
            if ("group".equals(token.lower) || "having".equals(token.lower) || "over".equals(token.lower)) {
                throw unsupportedSql(method, "复杂聚合或窗口函数");
            }
            if ("count".equals(token.lower) || "sum".equals(token.lower) || "avg".equals(token.lower)
                || "min".equals(token.lower) || "max".equals(token.lower)) {
                throw unsupportedSql(method, "复杂聚合或窗口函数");
            }
        }
    }

    private BoundSql bind(String sql, Method method, Object[] args) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        Parameter[] parameters = method.getParameters();
        Object[] actual = args == null ? new Object[0] : args;
        if (parameters.length != actual.length) {
            throw new ValidationException("DAO 方法参数数量不匹配: " + method);
        }
        for (int i = 0; i < parameters.length; i++) {
            values.put(parameters[i].getName(), actual[i]);
        }
        appendScopeValues(scope, values, new int[] {0});
        values.put(RESERVED_PARAMETER_PREFIX + "logic_delete", JdbcLogicDeleteValues.notDeleted(meta));
        values.put(RESERVED_PARAMETER_PREFIX + "logic_deleted", JdbcLogicDeleteValues.deleted(meta));
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
        return new BoundSql(result.toString(), boundArgs);
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

        private TablePart(String tableName, String alias) {
            this.tableName = tableName;
            this.alias = alias;
        }
    }

    private static final class ParsedSql {
        private final String sql;
        private final List<String> parameterNames;
        private final boolean query;
        private final TablePart table;

        private ParsedSql(String sql, List<String> parameterNames, boolean query, TablePart table) {
            this.sql = sql;
            this.parameterNames = parameterNames;
            this.query = query;
            this.table = table;
        }
    }

    private static final class BoundSql {
        private final String sql;
        private final List<Object> args;

        private BoundSql(String sql, List<Object> args) {
            this.sql = sql;
            this.args = args;
        }
    }

    private enum QueryKind {
        SINGLE,
        OPTIONAL,
        LIST
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
            throw new ValidationException("@EntQuery 只支持实体、Optional<T> 或 List<T>: " + method);
        }
    }
}
