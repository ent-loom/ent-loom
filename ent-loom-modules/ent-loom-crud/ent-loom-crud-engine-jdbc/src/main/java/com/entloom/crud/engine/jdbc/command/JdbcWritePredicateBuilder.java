package com.entloom.crud.engine.jdbc.command;

import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.exception.DataScopeDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.engine.jdbc.sql.JdbcPredicateBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 命令写入谓词构建器。
 */
class JdbcWritePredicateBuilder {

    <P, R> String buildEffectiveWriteWhere(
        EntityMeta meta,
        CommandSpec<P> spec,
        List<QueryFilter> targetSelector,
        boolean withVersion,
        List<Object> args
    ) {
        List<String> predicates = new ArrayList<String>();
        appendNotDeletedPredicate(meta, predicates, args);
        predicates.addAll(buildGovernancePredicates(meta, spec.getGovernanceScope(), args));
        predicates.addAll(buildTargetSelectorPredicates(meta, targetSelector, args));
        if (withVersion) {
            appendExpectedVersion(meta, spec, predicates, args);
        }
        if (predicates.isEmpty()) {
            throw new DataScopeDeniedException("有效写入谓词不能为空");
        }
        return String.join(" and ", predicates);
    }

    /**
     * 构建治理范围过滤谓词。
     */
    List<String> buildGovernancePredicates(EntityMeta meta, CrudDataScope scope, List<Object> args) {
        List<String> predicates = new ArrayList<String>();
        if (scope == null || scope.isExplicitAll()) {
            return predicates;
        }
        for (Map.Entry<String, Object> entry : scope.getDimensions().entrySet()) {
            String column = meta.resolveColumn(entry.getKey());
            if (column == null) {
                throw new DataScopeDeniedException("不支持的治理范围维度: " + entry.getKey());
            }
            JdbcPredicateBuilder.appendEqualityOrIn(predicates, args, column, entry.getValue(), entry.getKey());
        }
        return predicates;
    }

    void appendNotDeletedPredicate(EntityMeta meta, List<String> predicates, List<Object> args) {
        if (meta.getLogicDeleteField() == null || meta.getLogicDeleteField().trim().isEmpty()) {
            return;
        }
        String logicDeleteColumn = meta.resolveColumn(meta.getLogicDeleteField());
        if (logicDeleteColumn == null) {
            throw new ValidationException("未知逻辑删除字段: " + meta.getLogicDeleteField());
        }
        predicates.add(logicDeleteColumn + " = ?");
        args.add(Integer.valueOf(0));
    }

    /**
     * 构建目标筛选条件谓词。
     */
    List<String> buildTargetSelectorPredicates(EntityMeta meta, List<QueryFilter> targetSelector, List<Object> args) {
        List<String> predicates = new ArrayList<String>();
        for (QueryFilter filter : targetSelector) {
            String column = meta.resolveColumn(filter.getField());
            if (column == null) {
                throw new ValidationException("未知目标选择器字段: " + filter.getField());
            }
            FilterOperator operator = filter.getOperator();
            if (operator != FilterOperator.EQ && operator != FilterOperator.IN) {
                throw new ValidationException("不支持的目标选择器操作符: " + operator);
            }
            JdbcPredicateBuilder.appendEqualityOrIn(predicates, args, column, filter.getValue(), filter.getField());
        }
        return predicates;
    }

    <P, R> void appendExpectedVersion(EntityMeta meta, CommandSpec<P> spec, List<String> predicates, List<Object> args) {
        if (spec.getExpectedVersion() == null) {
            return;
        }
        if (!meta.getAllowedFields().contains("version")) {
            return;
        }
        predicates.add(meta.resolveColumn("version") + " = ?");
        args.add(spec.getExpectedVersion());
    }
}
