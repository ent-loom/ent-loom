package com.entloom.crud.engine.jdbc.command;

import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.runtime.context.DefaultExecutionContext;
import com.entloom.crud.core.exception.DataScopeDeniedException;
import com.entloom.crud.core.exception.RouteNotFoundException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * 写入影响行数为 0 时的原因分类器。
 */
class JdbcWriteMissClassifier {
    /** 受保护的 SQL 执行器。 */
    private final GuardedSqlExecutor guardedSqlExecutor;
    /** 谓词构建器。 */
    private final JdbcWritePredicateBuilder predicateBuilder;

    JdbcWriteMissClassifier(
        GuardedSqlExecutor guardedSqlExecutor,
        JdbcWritePredicateBuilder predicateBuilder
    ) {
        this.guardedSqlExecutor = guardedSqlExecutor;
        this.predicateBuilder = predicateBuilder == null ? new JdbcWritePredicateBuilder() : predicateBuilder;
    }

    <P, R> void classify(
        EntityMeta meta,
        CommandSpec<P> spec,
        List<QueryFilter> targetSelector,
        String opName,
        DefaultExecutionContext context
    ) {
        boolean targetExists = exists(meta, targetSelector, null, context);
        boolean existsInGranted = spec.getGrantedScope() == null
            ? targetExists
            : exists(meta, targetSelector, spec.getGrantedScope(), context);
        boolean existsInGovernance = spec.getGovernanceScope() == null
            ? targetExists
            : exists(meta, targetSelector, spec.getGovernanceScope(), context);

        if (targetExists && !existsInGranted) {
            throw new DataScopeDeniedException(opName + " 目标超出已授权范围");
        }
        if (existsInGranted && !existsInGovernance) {
            throw new DataScopeDeniedException(opName + " 目标超出治理范围");
        }
        if (spec.getExpectedVersion() != null && existsInGovernance) {
            throw new ValidationException("期望版本(expectedVersion)不匹配或目标状态已变化");
        }
//        + meta.getEntityName()
        throw new RouteNotFoundException(opName + " 目标不存在");
    }

    private boolean exists(
        EntityMeta meta,
        List<QueryFilter> targetSelector,
        CrudDataScope scope,
        DefaultExecutionContext context
    ) {
        List<Object> args = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("select count(1) from ").append(meta.getTable());
        List<String> predicates = new ArrayList<String>();
        predicateBuilder.appendNotDeletedPredicate(meta, predicates, args);
        if (scope != null) {
            predicates.addAll(predicateBuilder.buildGovernancePredicates(meta, scope, args));
        }
        predicates.addAll(predicateBuilder.buildTargetSelectorPredicates(meta, targetSelector, args));
        if (!predicates.isEmpty()) {
            sql.append(" where ").append(String.join(" and ", predicates));
        }
        Object count = guardedSqlExecutor.queryForObject(sql.toString(), args, context);
        return count instanceof Number && ((Number) count).longValue() > 0L;
    }
}
