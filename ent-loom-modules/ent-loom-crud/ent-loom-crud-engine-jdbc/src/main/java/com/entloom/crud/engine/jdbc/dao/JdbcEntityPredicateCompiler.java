package com.entloom.crud.engine.jdbc.dao;

import com.entloom.crud.core.capability.dao.RowConstraint;
import com.entloom.crud.core.capability.dao.RowConstraintOperator;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;
import com.entloom.crud.engine.jdbc.sql.JdbcPredicateBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 编译实体 DAO 的主键、范围和逻辑删除谓词。
 *
 * <p>该编译器只接收已经冻结的元数据和已经规范化的范围，不接受任意 SQL 片段。</p>
 */
final class JdbcEntityPredicateCompiler {
    /** 首期单条 DAO SQL 的保守参数上限。 */
    static final int DEFAULT_MAX_PARAMETERS = 1000;

    private final int maxParameters;
    private final JdbcDialect dialect;

    JdbcEntityPredicateCompiler() {
        this(StandardJdbcDialect.GENERIC, DEFAULT_MAX_PARAMETERS);
    }

    JdbcEntityPredicateCompiler(int maxParameters) {
        this(StandardJdbcDialect.GENERIC, maxParameters);
    }

    JdbcEntityPredicateCompiler(JdbcDialect dialect, int maxParameters) {
        if (maxParameters <= 0) {
            throw new ValidationException("DAO SQL 参数上限必须大于 0");
        }
        this.dialect = dialect == null ? StandardJdbcDialect.GENERIC : dialect;
        this.maxParameters = maxParameters;
    }

    CompiledWhere byId(EntityMeta meta, String idField, Object id, RowConstraint scope) {
        List<String> predicates = new ArrayList<String>();
        List<Object> args = new ArrayList<Object>();
        appendColumnEquals(meta, predicates, args, idField, id);
        appendScope(meta, scope, predicates, args);
        appendNotDeleted(meta, predicates, args);
        validateParameterCount(args.size());
        return new CompiledWhere(String.join(" and ", predicates), args);
    }

    void validateParameterCount(int count) {
        if (count > maxParameters) {
            throw new ValidationException("DAO SQL 参数数量超过上限: " + maxParameters);
        }
    }

    private void appendScope(
        EntityMeta meta,
        RowConstraint constraint,
        List<String> predicates,
        List<Object> args
    ) {
        if (constraint == null) {
            throw new ValidationException("DAO 范围不能为空");
        }
        switch (constraint.getKind()) {
            case UNRESTRICTED:
                return;
            case AND:
                for (RowConstraint child : constraint.getChildren()) {
                    appendScope(meta, child, predicates, args);
                }
                return;
            case PREDICATE:
                appendPredicate(meta, constraint, predicates, args);
                return;
            case OR:
            case NOT:
            default:
                throw new ValidationException(
                    "DAO 范围表达式无法编译: " + constraint.getKind().getDisplayName()
                );
        }
    }

    private void appendPredicate(
        EntityMeta meta,
        RowConstraint constraint,
        List<String> predicates,
        List<Object> args
    ) {
        String field = constraint.getField();
        String column = dialect.quoteIdentifier(meta.resolveColumn(field));
        if (column == null) {
            throw new ValidationException("DAO 范围字段未映射为列: " + field);
        }
        if (constraint.getOperator() == RowConstraintOperator.EQ) {
            if (constraint.getValues().size() != 1 || constraint.getValues().get(0) == null) {
                throw new ValidationException("DAO 等值范围值不能为 NULL: " + field);
            }
            JdbcPredicateBuilder.appendEqualityOrIn(
                predicates,
                args,
                column,
                constraint.getValues().get(0),
                field
            );
            return;
        }
        if (constraint.getOperator() == RowConstraintOperator.IN) {
            if (constraint.getValues().isEmpty()) {
                predicates.add("1 = 0");
                return;
            }
            JdbcPredicateBuilder.appendEqualityOrIn(
                predicates,
                args,
                column,
                constraint.getValues(),
                field
            );
            validateParameterCount(args.size());
            return;
        }
        throw new ValidationException(
            "DAO 范围操作符无法编译: " + constraint.getOperator().getDisplayName()
        );
    }

    private void appendColumnEquals(
        EntityMeta meta,
        List<String> predicates,
        List<Object> args,
        String field,
        Object value
    ) {
        String column = dialect.quoteIdentifier(meta.resolveColumn(field));
        if (column == null) {
            throw new ValidationException("DAO 主键字段未映射为列: " + field);
        }
        predicates.add(column + " = ?");
        args.add(value);
    }

    private void appendNotDeleted(EntityMeta meta, List<String> predicates, List<Object> args) {
        String field = meta.getLogicDeleteField();
        if (field == null || field.trim().isEmpty()) {
            return;
        }
        String column = dialect.quoteIdentifier(meta.resolveColumn(field));
        if (column == null || !meta.hasExplicitLogicDeleteValues()) {
            throw new ValidationException("逻辑删除字段或状态值未完整配置: " + meta.getEntityName());
        }
        predicates.add(column + " = ?");
        args.add(meta.getLogicDeleteNotDeletedValue());
    }

    /** 已编译的参数化 WHERE 片段。 */
    static final class CompiledWhere {
        private final String sql;
        private final List<Object> args;

        private CompiledWhere(String sql, List<Object> args) {
            this.sql = sql;
            this.args = Collections.unmodifiableList(new ArrayList<Object>(args));
        }

        String getSql() {
            return sql;
        }

        List<Object> getArgs() {
            return args;
        }
    }
}
