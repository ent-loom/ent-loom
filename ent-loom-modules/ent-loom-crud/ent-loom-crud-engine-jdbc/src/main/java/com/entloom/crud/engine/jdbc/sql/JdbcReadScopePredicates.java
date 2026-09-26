package com.entloom.crud.engine.jdbc.sql;

import com.entloom.crud.core.exception.DataScopeDeniedException;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.governance.scope.CrudReadScope;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import java.util.List;
import java.util.Map;

/** 主查询、关联查询和统计共用的读取治理谓词。 */
public final class JdbcReadScopePredicates {
    private JdbcReadScopePredicates() { }

    public static void append(CrudDataScope scope, EntityMeta meta, String alias, JdbcDialect dialect,
        List<String> predicates, List<Object> args) {
        if (scope == null) return;
        if (!scope.isExplicitAll()) appendConditions(scope.getDimensions(), meta, alias, dialect, predicates, args);
        CrudReadScope visibility = scope.readScope(meta.getEntityType());
        if (visibility == null) return;
        if (!visibility.isAllowed()) throw new DataScopeDeniedException("当前业务入口不允许读取实体: " + meta.getEntityName());
        appendConditions(visibility.getConditions(), meta, alias, dialect, predicates, args);
    }

    private static void appendConditions(Map<String, Object> conditions, EntityMeta meta, String alias,
        JdbcDialect dialect, List<String> predicates, List<Object> args) {
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            String column = meta.resolveColumn(entry.getKey());
            if (column == null) throw new DataScopeDeniedException("实体不支持治理范围字段: " + meta.getEntityName() + "." + entry.getKey());
            JdbcPredicateBuilder.appendEqualityOrIn(predicates, args, alias + "." + dialect.quoteIdentifier(column),
                entry.getValue(), "读取治理范围");
        }
    }
}
