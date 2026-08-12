package com.entloom.crud.engine.jdbc.security;

import com.entloom.crud.core.runtime.context.CrudExecutionContext;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.security.SqlSecurityGuard;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.runtime.spec.FilterableSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 默认 SQL 安全守卫实现。
 */
@RequiredArgsConstructor
public class SqlSafetyGuard implements SqlSecurityGuard {
    /** 白名单校验器。 */
    private final SqlIdentifierAllowlistValidator whitelistValidator;
    /** 参数限制器。 */
    private final SqlParameterLimiter paramLimiter;

    /**
     * 在 SQL 编译前执行安全校验。
     */
    @Override
    public void beforeCompile(BaseSpec spec) {
        if (spec instanceof QuerySpec<?>) {
            QuerySpec<?> querySpec = (QuerySpec<?>) spec;
            whitelistValidator.validateQuerySpec(querySpec);
            paramLimiter.validateQuerySpec(querySpec);
        } else if (spec instanceof FilterableSpec) {
            FilterableSpec filterableSpec = (FilterableSpec) spec;
            whitelistValidator.validateFilterableSpec(spec, filterableSpec, true);
            paramLimiter.validateFilterableSpec(filterableSpec);
        }
        if (spec instanceof CommandSpec<?>) {
            CommandSpec<?> commandSpec = (CommandSpec<?>) spec;
            whitelistValidator.validateCommandSpec(commandSpec);
            paramLimiter.validateCommandSpec(commandSpec);
        }
    }

    @Override
    public void beforeExecute(String sql, List<Object> args, CrudExecutionContext context) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new ValidationException("SQL 不能为空");
        }
        if (args != null && !args.isEmpty() && !sql.contains("?")) {
            throw new ValidationException("提供了 SQL 参数但未找到参数占位符");
        }
    }
}
