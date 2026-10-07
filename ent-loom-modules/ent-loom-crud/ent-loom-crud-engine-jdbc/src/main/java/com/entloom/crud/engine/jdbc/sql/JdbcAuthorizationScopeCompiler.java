package com.entloom.crud.engine.jdbc.sql;

import com.entloom.crud.core.exception.DataScopeDeniedException;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/** 将根实体授权范围编译为参数化 JDBC 谓词，保留关系元组的布尔结构。 */
public final class JdbcAuthorizationScopeCompiler {
    private static final int MAX_DEPTH = 64;
    private static final int MAX_NODES = 4096;

    public Predicate compile(AuthorizationScope scope, EntityMeta meta, String alias, JdbcDialect dialect) {
        Objects.requireNonNull(scope, "授权范围不能为空");
        Objects.requireNonNull(meta, "根实体元数据不能为空");
        Objects.requireNonNull(dialect, "JDBC 方言不能为空");
        if (alias == null || !alias.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new DataScopeDeniedException("授权范围表别名不合法");
        }
        List<Object> args = new ArrayList<Object>();
        String sql = compile(scope, meta, alias, dialect, args, new int[]{0}, 0);
        return new Predicate(sql, args);
    }

    /** 编译写入语句使用的非限定列谓词。 */
    public Predicate compile(AuthorizationScope scope, EntityMeta meta, JdbcDialect dialect) {
        Objects.requireNonNull(scope, "授权范围不能为空");
        Objects.requireNonNull(meta, "根实体元数据不能为空");
        Objects.requireNonNull(dialect, "JDBC 方言不能为空");
        List<Object> args = new ArrayList<Object>();
        String sql = compile(scope, meta, null, dialect, args, new int[]{0}, 0);
        return new Predicate(sql, args);
    }

    private String compile(AuthorizationScope scope, EntityMeta meta, String alias, JdbcDialect dialect,
                           List<Object> args, int[] nodes, int depth) {
        if (depth > MAX_DEPTH || ++nodes[0] > MAX_NODES) {
            throw new DataScopeDeniedException("授权范围组合过深或条件过多");
        }
        switch (scope.getKind()) {
            case ALL:
                return "1=1";
            case NONE:
                return "1=0";
            case EQUAL:
                    String equalColumn = column(scope, meta, alias, dialect);
                args.add(scope.getValues().get(0));
                return equalColumn + " = ?";
            case IN:
                String inColumn = column(scope, meta, alias, dialect);
                StringJoiner placeholders = new StringJoiner(",");
                for (Object value : scope.getValues()) {
                    placeholders.add("?");
                    args.add(value);
                }
                return inColumn + " in (" + placeholders + ")";
            case AND:
            case OR:
                StringJoiner parts = new StringJoiner(scope.getKind() == AuthorizationScope.Kind.AND
                    ? " and " : " or ", "(", ")");
                for (AuthorizationScope child : scope.getChildren()) {
                    parts.add(compile(child, meta, alias, dialect, args, nodes, depth + 1));
                }
                return parts.toString();
            default:
                throw new DataScopeDeniedException("不支持的授权范围节点: " + scope.getKind());
        }
    }

    private String column(AuthorizationScope scope, EntityMeta meta, String alias, JdbcDialect dialect) {
        EntityFieldMeta field = meta.resolveFieldMeta(scope.getField());
        String name = meta.resolveColumn(scope.getField());
        if (field == null || field.isRelation() || name == null || name.trim().isEmpty()) {
            throw new DataScopeDeniedException("实体不支持授权范围字段: " + meta.getEntityName() + "." + scope.getField());
        }
        return alias == null ? dialect.quoteIdentifier(name) : alias + "." + dialect.quoteIdentifier(name);
    }

    public static final class Predicate {
        private final String sql;
        private final List<Object> args;

        private Predicate(String sql, List<Object> args) {
            this.sql = sql;
            this.args = Collections.unmodifiableList(new ArrayList<Object>(args));
        }

        public String getSql() { return sql; }
        public List<Object> getArgs() { return args; }
    }
}
