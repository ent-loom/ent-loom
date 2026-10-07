package com.entloom.crud.core.capability.query.gateway;

import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.core.exception.CrudExceptionContext;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.capability.query.engine.AuthorizedQueryExecutor;
import com.entloom.crud.core.governance.admission.AccessContextScope;
import com.entloom.crud.core.execution.ExecutionPipeline;
import com.entloom.crud.core.governance.service.CrudGovernanceResult;
import com.entloom.crud.core.runtime.router.QueryRoute;
import com.entloom.crud.core.runtime.router.QueryRouter;
import com.entloom.crud.core.capability.query.spec.QueryExecutionSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.runtime.spec.SpecSnapshotFactory;
import com.entloom.crud.core.util.RouteKeyFactory;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 默认查询网关实现。
 */
public class QueryGatewayImpl implements QueryGateway {
    /** 查询路由器。 */
    private final QueryRouter queryRouter;
    /** 统一执行管线。 */
    private final ExecutionPipeline executionPipeline;
    private final AuthorizedQueryExecutor authorizedQueries;

    public QueryGatewayImpl(QueryRouter queryRouter, ExecutionPipeline executionPipeline) {
        this(queryRouter, executionPipeline, null);
    }

    public QueryGatewayImpl(QueryRouter queryRouter, ExecutionPipeline executionPipeline,
                            AuthorizedQueryExecutor authorizedQueries) {
        this.queryRouter = Objects.requireNonNull(queryRouter, "queryRouter 不能为空");
        this.executionPipeline = Objects.requireNonNull(executionPipeline, "executionPipeline 不能为空");
        this.authorizedQueries = authorizedQueries;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> PageResult<R> page(QuerySpec<R> spec) {
        if (executionPipeline.requiresBusinessAuthorization(spec)) {
            return (PageResult<R>) (PageResult<?>) requireAuthorizedQueries().page(
                AccessContextScope.currentVisitor().orElse(null), spec);
        }
        return executionPipeline.execute(
            () -> prepareRequestSpec(spec, QueryOperation.PAGE),
            governQueryStep(),
            (requestSpec, governedSpec, governance) -> {
                QueryRoute<R> route = route(governedSpec);
                QueryExecutionContext<R> context = QueryExecutionContext.create(governedSpec, route);
                return context.route().handler().page(context.executionSpec());
            }
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> List<R> list(QuerySpec<R> spec) {
        if (executionPipeline.requiresBusinessAuthorization(spec)) {
            return (List<R>) (List<?>) requireAuthorizedQueries().list(
                AccessContextScope.currentVisitor().orElse(null), spec);
        }
        return executionPipeline.execute(
            () -> prepareRequestSpec(spec, QueryOperation.LIST),
            governQueryStep(),
            (requestSpec, governedSpec, governance) -> {
                QueryRoute<R> route = route(governedSpec);
                QueryExecutionContext<R> context = QueryExecutionContext.create(governedSpec, route);
                return context.route().handler().list(context.executionSpec());
            }
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> R findOne(QuerySpec<R> spec) {
        if (executionPipeline.requiresBusinessAuthorization(spec)) {
            return (R) requireAuthorizedQueries().findOne(
                AccessContextScope.currentVisitor().orElse(null), spec);
        }
        return executionPipeline.execute(
            () -> prepareRequestSpec(spec, QueryOperation.FIND_ONE),
            governQueryStep(),
            (requestSpec, governedSpec, governance) -> {
                QueryRoute<R> route = route(governedSpec);
                QueryExecutionContext<R> context = QueryExecutionContext.create(governedSpec, route);
                return context.route().handler().findOne(context.executionSpec());
            }
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> R detail(QuerySpec<R> spec) {
        if (executionPipeline.requiresBusinessAuthorization(spec)) {
            return (R) requireAuthorizedQueries().detail(
                AccessContextScope.currentVisitor().orElse(null), spec);
        }
        return executionPipeline.execute(
            () -> prepareRequestSpec(spec, QueryOperation.DETAIL),
            governQueryStep(),
            (requestSpec, governedSpec, governance) -> {
                QueryRoute<R> route = route(governedSpec);
                QueryExecutionContext<R> context = QueryExecutionContext.create(governedSpec, route);
                return context.route().handler().detail(context.executionSpec());
            }
        );
    }

    private AuthorizedQueryExecutor requireAuthorizedQueries() {
        if (authorizedQueries == null) {
            throw new PermissionDeniedException("缺少受授权查询执行适配器");
        }
        return authorizedQueries;
    }

    private <R> QuerySpec<R> prepareRequestSpec(QuerySpec<R> spec, QueryOperation op) {
        QuerySpec<R> requestSpec = SpecSnapshotFactory.copy(Objects.requireNonNull(spec, "spec 不能为空"));
        if (requestSpec.getOp() == null) {
            return requestSpec.toBuilder().op(op).build();
        }
        if (requestSpec.getOp() != op) {
            throw new ValidationException("网关方法与 spec.op 的查询操作不一致");
        }
        return requestSpec;
    }

    private <R> Function<QuerySpec<R>, CrudGovernanceResult<QueryExecutionSpec<R>>> governQueryStep() {
        return requestSpec -> executionPipeline.governQuery(requestSpec);
    }

    private <R> QueryRoute<R> route(QueryExecutionSpec<R> spec) {
        try {
            return queryRouter.route(spec);
        } catch (RuntimeException ex) {
            throw CrudExceptionContext.enrich(ex, CrudErrorStage.ROUTE, safeRouteKey(spec));
        }
    }

    private String safeRouteKey(QuerySpec<?> spec) {
        try {
            return RouteKeyFactory.buildQueryRouteKey(spec);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
