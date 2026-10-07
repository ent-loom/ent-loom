package com.entloom.crud.engine.jdbc.query;

import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.enums.SortTarget;
import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.core.capability.query.CompiledQuery;
import com.entloom.crud.core.capability.query.QueryPlan;
import com.entloom.crud.core.capability.query.engine.AuthorizedQueryExecutor;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.CrudExceptionContext;
import com.entloom.crud.core.governance.adapter.CrudBusinessAuthorizationAdapter;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationResult;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditReasonCode;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.governance.admission.AccessContextScope;
import com.entloom.crud.core.governance.admission.AccessAdmission;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.spec.SpecSnapshotFactory;
import com.entloom.crud.core.runtime.validation.QuerySpecValidator;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.core.security.SqlSecurityGuard;
import com.entloom.crud.core.util.QueryStrategyResolver;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import com.entloom.crud.enums.QueryStrategy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 普通根实体 LIST/PAGE/FIND_ONE/DETAIL 的按次授权执行入口，调用方显式传入可信访问者。
 * 授权结果只在本次调用内消费；不接受外部结果，也不经过可替换的旧路由和查询组件。
 * 输出仅支持 CrudRecord，实体/DTO、场景、关联及时间简写条件须待对应适配验收后开放。
 */
public final class JdbcAuthorizedQueryExecutor implements AuthorizedQueryExecutor {
    private final EntityMetaRegistry entities;
    private final CrudBusinessAuthorizationAdapter authorization;
    private final SqlSecurityGuard sqlGuard;
    private final JdbcQueryCompiler compiler;
    private final JdbcQueryExecutor executor;
    private final CrudGovernanceAuditRecorder audit;
    private final QuerySpecValidator validator = new QuerySpecValidator();
    private final RootFirstQueryPlanner planner = new RootFirstQueryPlanner();

    public JdbcAuthorizedQueryExecutor(EntityMetaRegistry entities, CrudBusinessAuthorizationAdapter authorization,
                                       GuardedSqlExecutor sqlExecutor, SqlSecurityGuard sqlGuard, JdbcDialect dialect,
                                       CrudGovernanceAuditRecorder audit) {
        this.entities = Objects.requireNonNull(entities, "实体元数据注册表不能为空");
        this.authorization = Objects.requireNonNull(authorization, "CRUD 业务授权适配器不能为空");
        this.sqlGuard = Objects.requireNonNull(sqlGuard, "SQL 安全守卫不能为空");
        this.compiler = new JdbcQueryCompiler(dialect);
        this.executor = new JdbcQueryExecutor(Objects.requireNonNull(sqlExecutor, "SQL 执行器不能为空"), entities);
        this.audit = Objects.requireNonNull(audit, "治理审计记录器不能为空");
    }

    @Override
    public List<CrudRecord> list(VisitorContext visitor, QuerySpec<?> spec) {
        return execute(visitor, spec, QueryOperation.LIST, query ->
            project(executor.executeList(query, CrudRecord.class), query.getQueryPlan().getSpec().getSelectFields()));
    }

    @Override
    public PageResult<CrudRecord> page(VisitorContext visitor, QuerySpec<?> spec) {
        return execute(visitor, spec, QueryOperation.PAGE, query -> {
            PageResult<CrudRecord> result = executor.executePage(query, CrudRecord.class);
            result.setItems(project(result.getItems(), query.getQueryPlan().getSpec().getSelectFields()));
            return result;
        });
    }

    @Override
    public CrudRecord findOne(VisitorContext visitor, QuerySpec<?> spec) {
        return execute(visitor, spec, QueryOperation.FIND_ONE, query -> {
            CrudRecord row = executor.executeFindOne(query, CrudRecord.class);
            return row == null ? null : project(java.util.Collections.singletonList(row),
                query.getQueryPlan().getSpec().getSelectFields()).get(0);
        });
    }

    @Override
    public CrudRecord detail(VisitorContext visitor, QuerySpec<?> spec) {
        return execute(visitor, spec, QueryOperation.DETAIL, query ->
            project(java.util.Collections.singletonList(executor.executeOne(query, CrudRecord.class)),
                query.getQueryPlan().getSpec().getSelectFields()).get(0));
    }

    private <T> T execute(VisitorContext visitor, QuerySpec<?> source, QueryOperation operation,
                          Function<CompiledQuery, T> action) {
        long started = System.nanoTime();
        Invocation invocation = new Invocation(source);
        T value;
        try {
            if (visitor == null) {
                throw new PermissionDeniedException("新策略查询缺少可信访问者；异步执行必须重新准入并打开作用域");
            }
            CompiledQuery query = compile(visitor, source, operation, invocation);
            invocation.stage = CrudErrorStage.EXECUTE;
            value = action.apply(query);
        } catch (RuntimeException | Error failure) {
            CrudGovernanceAuditOutcome outcome = invocation.stage == CrudErrorStage.EXECUTE
                ? CrudGovernanceAuditOutcome.EXECUTION_FAILED : CrudGovernanceAuditOutcome.GOVERNANCE_DENIED;
            CrudGovernanceAuditReasonCode reason = failure instanceof CrudException
                ? CrudGovernanceAuditReasonCode.fromCrudErrorCode(((CrudException) failure).getErrorCode())
                : CrudGovernanceAuditReasonCode.EXECUTION_FAILED;
            try {
                record(visitor, invocation, operation, outcome, reason, started);
            } catch (RuntimeException | Error auditFailure) {
                if (auditFailure != failure) failure.addSuppressed(auditFailure);
            }
            if (failure instanceof RuntimeException) {
                throw CrudExceptionContext.enrich((RuntimeException) failure, invocation.stage, null);
            }
            throw failure;
        }
        record(visitor, invocation, operation, CrudGovernanceAuditOutcome.SUCCESS,
            CrudGovernanceAuditReasonCode.NONE, started);
        return value;
    }

    private CompiledQuery compile(VisitorContext visitor, QuerySpec<?> source, QueryOperation operation,
                                   Invocation invocation) {
        QuerySpec<?> spec = prepare(source, operation);
        invocation.spec = spec;
        invocation.stage = CrudErrorStage.GOVERNANCE;
        EntityMeta meta = entities.getEntityMeta(spec.getRootType());
        String targetId = targetId(spec, meta, operation);
        AuthorizationResult result = authorization.authorize(visitor, spec, targetId);
        invocation.result = result;
        if (result.getDecision() != AccessDecision.ALLOW || result.getRequest().getVisitor() != visitor
            || !Objects.equals(result.getRequest().getTargetId(), targetId) || meta == null || meta.getEntityType() != spec.getRootType()
            || !result.getRequest().getOperation().getResourceCode().equals(meta.getResourceDescriptor().getResourceCode())) {
            throw new PermissionDeniedException("授权结果不适用于本次根实体查询");
        }
        List<String> selected = validateFields(spec, meta, result.getFields(), operation);
        QuerySpec<?> effective = spec.toBuilder().selectFields(selected).build();
        sqlGuard.beforeCompile(effective);
        QueryPlan plan = planner.plan(effective, meta, RelationGraph.empty());
        return compiler.compileAuthorized(plan, result.getScope());
    }

    private void record(VisitorContext visitor, Invocation invocation, QueryOperation operation,
                        CrudGovernanceAuditOutcome outcome, CrudGovernanceAuditReasonCode reason, long started) {
        EntityMeta meta = invocation.spec == null || invocation.spec.getRootType() == null
            ? null : entities.getEntityMeta(invocation.spec.getRootType());
        String resource = meta == null ? null : meta.getResourceDescriptor().getResourceCode();
        CrudResourceAction action = invocation.result == null
            ? (resource == null ? null : new CrudResourceAction(resource, operation.name(), null))
            : new CrudResourceAction(invocation.result.getRequest().getOperation().getResourceCode(),
                invocation.result.getRequest().getOperation().getOperationCode(), null);
        // 公共分支的实际认证主体仅写审计，不重新合入有效授权访问者。
        AccessSubject auditSubject = visitor == null ? null : AccessContextScope.currentAdmission()
            .filter(admission -> admission.getVisitorContext() == visitor)
            .flatMap(AccessAdmission::getAuthenticatedSubject).orElse(visitor.getSubject().orElse(null));
        CrudGovernanceAuditEvent event = CrudGovernanceAuditEvent.forBusinessQuery(visitor, auditSubject, invocation.result, action,
            outcome, reason, (System.nanoTime() - started) / 1_000_000L,
            outcome == CrudGovernanceAuditOutcome.SUCCESS ? null : invocation.stage,
            resource == null ? null : resource + "." + operation.name());
        audit.record(event);
    }

    private static final class Invocation {
        private QuerySpec<?> spec;
        private AuthorizationResult result;
        private CrudErrorStage stage = CrudErrorStage.NORMALIZE;

        private Invocation(QuerySpec<?> spec) { this.spec = spec; }
    }

    private QuerySpec<?> prepare(QuerySpec<?> source, QueryOperation operation) {
        QuerySpec<?> spec = SpecSnapshotFactory.copy(Objects.requireNonNull(source, "查询 Spec 不能为空"));
        if (spec.getOp() != null && spec.getOp() != operation) {
            throw new ValidationException("执行方法与 spec.op 的查询操作不一致");
        }
        if (spec.getResultType() != CrudRecord.class || (spec.getScene() != null && !spec.getScene().trim().isEmpty())
            || spec.getAccessDecision() != AccessDecision.ALLOW
            || spec.getEntityClasses().size() > 1 || !spec.getExpandRelations().isEmpty()
            || spec.getExistsRelationFilter() != null || spec.getTime() != null || spec.getGrantedScope() != null
            || QueryStrategyResolver.resolveEffectiveStrategy(spec.getHandlerDefaultStrategy(), spec.getStrategy(),
                QueryStrategy.ROOT_FIRST) != QueryStrategy.ROOT_FIRST) {
            throw new PermissionDeniedException("受授权 JDBC 查询仅支持普通根实体 CrudRecord 查询");
        }
        // 不把调用方可写的旧主体传给编译与执行阶段。
        return validator.validateStructure(spec.toBuilder().op(operation).subject(null).build());
    }

    private String targetId(QuerySpec<?> spec, EntityMeta meta, QueryOperation operation) {
        if (operation == QueryOperation.LIST || operation == QueryOperation.PAGE) {
            return null;
        }
        QueryFilter target = null;
        for (QueryFilter filter : spec.getFilters()) {
            if (filter != null && meta.getIdField().equals(filter.getField())) {
                if (target != null || filter.getOperator() != com.entloom.crud.api.enums.FilterOperator.EQ
                    || filter.getValue() == null || filter.getValue() instanceof java.util.Collection<?>) {
                    throw new PermissionDeniedException("详情查询必须使用一个主键等值目标");
                }
                target = filter;
            }
        }
        if (target == null) {
            throw new PermissionDeniedException("详情查询缺少主键目标");
        }
        return String.valueOf(target.getValue());
    }

    private List<String> validateFields(QuerySpec<?> spec, EntityMeta meta, AuthorizationFields fields,
                                        QueryOperation operation) {
        for (QueryFilter filter : spec.getFilters()) {
            if (filter == null || filter.getOperator() == null) {
                throw new ValidationException("过滤条件及操作符不能为空");
            }
            if ((operation == QueryOperation.FIND_ONE || operation == QueryOperation.DETAIL)
                && meta.getIdField().equals(filter.getField())) {
                continue;
            }
            requireField(meta, fields, AuthorizationFields.Use.FILTER, filter.getField());
        }
        for (QuerySort sort : spec.getSorts()) {
            if (sort == null || sort.getDirection() == null
                || (sort.getTarget() != SortTarget.AUTO && sort.getTarget() != SortTarget.FIELD)) {
                throw new ValidationException("根实体排序仅支持字段及明确方向");
            }
            requireField(meta, fields, AuthorizationFields.Use.SORT, sort.getField());
        }
        List<String> selected = spec.getSelectFields();
        if (selected.isEmpty()) {
            for (String field : meta.getAllowedFields()) {
                if (!meta.resolveFieldMeta(field).isRelation() && fields.allows(AuthorizationFields.Use.RETURN, field)) {
                    selected.add(field);
                }
            }
        }
        if (selected.isEmpty()) {
            throw new PermissionDeniedException("本次查询没有可返回的普通字段");
        }
        for (String field : selected) {
            requireField(meta, fields, AuthorizationFields.Use.RETURN, field);
        }
        return selected;
    }

    private void requireField(EntityMeta meta, AuthorizationFields fields, AuthorizationFields.Use use, String field) {
        EntityFieldMeta fieldMeta = meta.resolveFieldMeta(field);
        if (fieldMeta == null || fieldMeta.isRelation() || !fields.allows(use, field)
            || (use == AuthorizationFields.Use.FILTER && !fieldMeta.isFilterable())
            || (use == AuthorizationFields.Use.SORT && !fieldMeta.isSortable())) {
            throw new PermissionDeniedException("本次查询不允许字段用途 " + use + ": " + field);
        }
    }

    /** 按投影白名单构建输出，并适配 JDBC 驱动的列别名大小写；不暴露额外列。 */
    private List<CrudRecord> project(List<CrudRecord> rows, List<String> selected) {
        List<CrudRecord> result = new ArrayList<CrudRecord>(rows.size());
        for (CrudRecord row : rows) {
            Map<String, Object> projected = new LinkedHashMap<String, Object>();
            for (String field : selected) {
                String key = null;
                for (String candidate : row.keys()) {
                    if (candidate.equalsIgnoreCase(field)) {
                        if (key != null) throw new ValidationException("查询结果列别名存在歧义: " + field);
                        key = candidate;
                    }
                }
                if (key == null) throw new ValidationException("查询结果缺少授权投影字段: " + field);
                projected.put(field, row.raw(key));
            }
            result.add(CrudRecord.copyOf(projected));
        }
        return result;
    }
}
