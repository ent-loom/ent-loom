package com.entloom.crud.core.execution;

import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.core.exception.CrudExceptionContext;
import com.entloom.crud.core.governance.service.CrudGovernanceResult;
import com.entloom.crud.core.governance.service.CrudGovernanceService;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.capability.command.spec.CommandExecutionSpec;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.runtime.spec.GovernableSpec;
import com.entloom.crud.core.capability.query.spec.QueryExecutionSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.util.RouteKeyFactory;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * CRUD 统一执行管线。
 *
 * 该类只负责通用阶段编排：normalize -> govern -> execute -> audit。
 * 操作类型相关的 normalize、route、engine 执行逻辑由调用方提供。
 */
public class ExecutionPipeline {
    /** 治理服务，用于执行治理与审计闭环。 */
    private final CrudGovernanceService governanceService;

    public ExecutionPipeline(CrudGovernanceService governanceService) {
        this.governanceService = Objects.requireNonNull(governanceService, "governanceService 不能为空");
    }

    public <R> CrudGovernanceResult<QueryExecutionSpec<R>> governQuery(QuerySpec<R> spec) {
        return governanceService.governQuery(spec);
    }

    public <P> CrudGovernanceResult<CommandExecutionSpec<P>> governCommand(CommandSpec<P> spec) {
        return governanceService.governCommand(spec);
    }

    public <S extends BaseSpec & GovernableSpec<S>> CrudGovernanceResult<S> governStats(S spec) {
        return governanceService.governStats(spec);
    }

    public CrudGovernanceResult<ImportSpec> governImport(ImportSpec spec) {
        return governanceService.governImport(spec);
    }

    public CrudGovernanceResult<ExportSpec> governExport(ExportSpec spec) {
        return governanceService.governExport(spec);
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
