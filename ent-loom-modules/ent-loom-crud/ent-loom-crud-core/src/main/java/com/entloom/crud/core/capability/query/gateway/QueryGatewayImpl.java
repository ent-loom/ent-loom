package com.entloom.crud.core.capability.query.gateway;

import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
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
import java.util.Collections;
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
    /** 实体元数据，用于解析主键属性。 */
    private final EntityMetaRegistry metaRegistry;

    public QueryGatewayImpl(QueryRouter queryRouter, ExecutionPipeline executionPipeline,
                            EntityMetaRegistry metaRegistry, AuthorizedQueryExecutor authorizedQueries) {
        this.queryRouter = Objects.requireNonNull(queryRouter, "queryRouter 不能为空");
        this.executionPipeline = Objects.requireNonNull(executionPipeline, "executionPipeline 不能为空");
        this.metaRegistry = Objects.requireNonNull(metaRegistry, "metaRegistry 不能为空");
        this.authorizedQueries = authorizedQueries;
    }

    @Override
    public <R> PageResult<R> page(QuerySpec<R> spec) {
        if (executionPipeline.requiresBusinessAuthorization(spec)) {
            return requireAuthorizedQueries().page(
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
    public <R> List<R> list(QuerySpec<R> spec) {
        if (executionPipeline.requiresBusinessAuthorization(spec)) {
            return requireAuthorizedQueries().list(
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
    public <R> R findOne(QuerySpec<R> spec) {
        if (executionPipeline.requiresBusinessAuthorization(spec)) {
            return requireAuthorizedQueries().findOne(
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
    public <R> R detail(QuerySpec<R> spec) {
        if (executionPipeline.requiresBusinessAuthorization(spec)) {
            return requireAuthorizedQueries().detail(
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

    @Override
    public <T, ID> T detail(EntityType<T, ID> type, ID id) {
        Objects.requireNonNull(type, "实体类型不能为空");
        if (id == null || !boxedType(type.idClass()).isInstance(id)) {
            throw new ValidationException("主键不能为空且必须匹配声明的主键类型");
        }
        EntityMeta meta = metaRegistry.getEntityMeta(type.entityClass());
        if (meta == null || meta.getIdField() == null || meta.getIdField().trim().isEmpty()) {
            throw new ValidationException("实体未声明主键: " + type.entityClass().getName());
        }
        return detail(QuerySpec.builder(type.entityClass())
            .filters(Collections.singletonList(new QueryFilter(meta.getIdField(), FilterOperator.EQ, id)))
            .build());
    }

    /** 主键参数经 Java 自动装箱后，仍按声明的精确类型校验。 */
    private static Class<?> boxedType(Class<?> type) {
        if (type == long.class) return Long.class;
        if (type == int.class) return Integer.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == boolean.class) return Boolean.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == char.class) return Character.class;
        return type;
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
