package com.entloom.crud.core.execution;

import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.core.capability.command.spec.CommandExecutionSpec;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.capability.query.spec.QueryExecutionSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.CrudExceptionContext;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.adapter.CrudBusinessAuthorizationAdapter;
import com.entloom.crud.core.governance.authorization.AuthorizationResult;
import com.entloom.crud.core.governance.admission.AccessContextScope;
import com.entloom.crud.core.governance.service.CrudGovernanceResult;
import com.entloom.crud.core.governance.service.CrudGovernanceService;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.runtime.spec.GovernableSpec;
import com.entloom.crud.core.util.RouteKeyFactory;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * CRUD 统一执行管线。
 *
 * 该类负责通用阶段编排：normalize -> govern -> execute -> audit，并在旧治理前预检可信访问者。
 * 操作类型相关的 normalize、route、engine 执行逻辑由调用方提供。
 */
public class ExecutionPipeline {
    /** 治理服务，用于执行治理与审计闭环。 */
    private final CrudGovernanceService governanceService;
    /** 业务授权适配器；旧执行形状只能在授权能力已消费时继续执行。 */
    private final CrudBusinessAuthorizationAdapter authorizationAdapter;

    public ExecutionPipeline(CrudGovernanceService governanceService) {
        this(governanceService, null);
    }

    public ExecutionPipeline(CrudGovernanceService governanceService, CrudBusinessAuthorizationAdapter authorizationAdapter) {
        this.governanceService = Objects.requireNonNull(governanceService, "governanceService 不能为空");
        this.authorizationAdapter = authorizationAdapter;
    }

    public <R> CrudGovernanceResult<QueryExecutionSpec<R>> governQuery(QuerySpec<R> spec) {
        requireAuthorized(spec);
        return governanceService.governQuery(spec);
    }

    public <P> CrudGovernanceResult<CommandExecutionSpec<P>> governCommand(CommandSpec<P> spec) {
        requireAuthorized(spec);
        return governanceService.governCommand(spec);
    }

    public <S extends BaseSpec & GovernableSpec<S>> CrudGovernanceResult<S> governStats(S spec) {
        requireAuthorized(spec);
        return governanceService.governStats(spec);
    }

    public CrudGovernanceResult<ImportSpec> governImport(ImportSpec spec) {
        requireAuthorized(spec);
        return governanceService.governImport(spec);
    }

    public CrudGovernanceResult<ExportSpec> governExport(ExportSpec spec) {
        requireAuthorized(spec);
        return governanceService.governExport(spec);
    }

    /** 已绑定新策略的资源始终使用业务授权；声明式作用域内的其他资源也不能降级。 */
    public boolean requiresBusinessAuthorization(BaseSpec spec) {
        return AccessContextScope.isAuthorizationRequired()
            || (authorizationAdapter != null && authorizationAdapter.governs(spec));
    }

    public VisitorContext requireBusinessVisitor() {
        return AccessContextScope.currentVisitor().orElseThrow(() ->
            new PermissionDeniedException("新策略资源缺少可信访问者；异步执行必须重新准入并打开作用域"));
    }

    private void requireAuthorized(BaseSpec spec) {
        if (!requiresBusinessAuthorization(spec)) return;
        VisitorContext visitor = requireBusinessVisitor();
        if (authorizationAdapter == null) {
            throw new PermissionDeniedException("可信访问者缺少 CRUD 业务授权适配器");
        }
        AuthorizationResult authorization = authorizationAdapter.authorize(visitor, spec, null);
        if (authorization == null || authorization.getRequest().getVisitor() != visitor
            || authorization.getDecision() != AccessDecision.ALLOW) {
            throw new PermissionDeniedException("业务操作授权拒绝");
        }
        throw new PermissionDeniedException("当前 CRUD 执行形状尚未接入业务授权范围与字段约束");
    }

    /**
     * 运行一次完整的 CRUD 执行管线。
     */
    public <S extends BaseSpec, E extends BaseSpec, R> R execute(
        Supplier<S> normalizer,
        Function<S, CrudGovernanceResult<E>> governanceStep,
        GovernedExecutor<S, E, R> executor
    ) {
        S requestSpec;
        try {
            requestSpec = Objects.requireNonNull(normalizer, "normalizer 不能为空").get();
        } catch (RuntimeException ex) {
            throw CrudExceptionContext.enrich(ex, CrudErrorStage.NORMALIZE, null);
        }
        CrudGovernanceResult<E> governance;
        try {
            governance = Objects.requireNonNull(governanceStep, "governanceStep 不能为空").apply(requestSpec);
        } catch (RuntimeException ex) {
            throw CrudExceptionContext.enrich(ex, CrudErrorStage.GOVERNANCE, safeRouteKey(requestSpec));
        }
        return executeGoverned(requestSpec, governance, executor);
    }

    /**
     * 执行已完成治理的请求，并记录成功/失败审计。
     */
    public <S extends BaseSpec, E extends BaseSpec, R> R executeGoverned(
        S requestSpec,
        CrudGovernanceResult<E> governance,
        GovernedExecutor<S, E, R> executor
    ) {
        CrudGovernanceResult<E> current = Objects.requireNonNull(governance, "governance 不能为空");
        GovernedExecutor<S, E, R> currentExecutor = Objects.requireNonNull(executor, "executor 不能为空");
        try {
            R result = currentExecutor.execute(requestSpec, current.getEffectiveSpec(), current);
            governanceService.recordAllow(current);
            return result;
        } catch (RuntimeException ex) {
            RuntimeException enriched = CrudExceptionContext.enrich(
                ex,
                CrudErrorStage.EXECUTE,
                safeRouteKey(current.getEffectiveSpec())
            );
            governanceService.recordExecutionFailure(current, enriched);
            throw enriched;
        } catch (Error err) {
            governanceService.recordExecutionFailure(current, err);
            throw err;
        }
    }

    private String safeRouteKey(BaseSpec spec) {
        if (spec == null) {
            return null;
        }
        try {
            if (spec instanceof CommandSpec<?>) {
                return RouteKeyFactory.buildCommandRouteKey((CommandSpec<?>) spec);
            }
            if (spec instanceof QuerySpec<?>) {
                return RouteKeyFactory.buildQueryRouteKey((QuerySpec<?>) spec);
            }
            if (spec instanceof ImportSpec) {
                return RouteKeyFactory.buildImportRouteKey((ImportSpec) spec);
            }
            if (spec instanceof ExportSpec) {
                return RouteKeyFactory.buildExportRouteKey((ExportSpec) spec);
            }
            return RouteKeyFactory.buildStatsRouteKey(spec);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 治理后的执行阶段。
     */
    public interface GovernedExecutor<S extends BaseSpec, E extends BaseSpec, R> {
        R execute(S requestSpec, E effectiveSpec, CrudGovernanceResult<E> governance);
    }
}
