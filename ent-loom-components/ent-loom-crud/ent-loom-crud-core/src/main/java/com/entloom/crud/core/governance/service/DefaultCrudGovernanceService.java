package com.entloom.crud.core.governance.service;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.CrudExceptionContext;
import com.entloom.crud.core.exception.DataScopeDeniedException;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditReasonCode;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.audit.LoggingCrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.governance.permission.CrudPermissionService;
import com.entloom.crud.core.governance.permission.RuleBasedCrudPermissionService;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.governance.scope.CrudDataScopeContributor;
import com.entloom.crud.core.governance.scope.CrudDataScopeResolver;
import com.entloom.crud.core.governance.scope.DefaultCrudDataScopeResolver;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.crud.core.governance.subject.FailClosedCrudSubjectResolver;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.capability.command.spec.CommandExecutionSpec;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.runtime.spec.CrudSpecAttributeResolver;
import com.entloom.crud.core.runtime.spec.DefaultCrudSpecAttributeResolver;
import com.entloom.crud.core.runtime.spec.GovernableSpec;
import com.entloom.crud.core.runtime.spec.OperationKeySpec;
import com.entloom.crud.core.capability.query.spec.QueryExecutionSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.capability.stats.StatsSpec;
import com.entloom.crud.core.util.RouteKeyFactory;
import com.entloom.crud.core.runtime.validation.SpecValidator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认治理主链实现。
 *
 * 说明：该类负责治理流程编排，具体的数据范围交集与主体规范化由专用组件处理。
 */
public class DefaultCrudGovernanceService implements CrudGovernanceService {
    /** 日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(DefaultCrudGovernanceService.class);

    /** 规格校验器。 */
    private final SpecValidator specValidator;
    /** 实体元数据注册表。 */
    private final EntityMetaRegistry entityMetaRegistry;
    /** 主体解析器。 */
    private final CrudSubjectResolver subjectResolver;
    /** 权限服务。 */
    private final CrudPermissionService permissionService;
    /** 数据范围解析器。 */
    private final CrudDataScopeResolver dataScopeResolver;
    /** 数据范围贡献器列表。 */
    private final List<CrudDataScopeContributor> dataScopeContributors;
    /** 审计记录器。 */
    private final CrudGovernanceAuditRecorder auditRecorder;
    /** 主体规范化器。 */
    private final CrudSubjectNormalizer subjectNormalizer;
    /** 范围交集计算器。 */
    private final CrudScopeIntersectionService scopeIntersectionService;
    /** Spec 属性解析器。 */
    private final CrudSpecAttributeResolver specAttributeResolver;

    public DefaultCrudGovernanceService(
        EntityMetaRegistry entityMetaRegistry,
        SpecValidator specValidator,
        CrudSubjectResolver subjectResolver,
        CrudPermissionService permissionService,
        CrudDataScopeResolver dataScopeResolver,
        Collection<CrudDataScopeContributor> dataScopeContributors,
        CrudGovernanceAuditRecorder auditRecorder
    ) {
        this(
            entityMetaRegistry,
            specValidator,
            subjectResolver,
            permissionService,
            dataScopeResolver,
            dataScopeContributors,
            auditRecorder,
            new DefaultCrudSpecAttributeResolver()
        );
    }

    public DefaultCrudGovernanceService(
        EntityMetaRegistry entityMetaRegistry,
        SpecValidator specValidator,
        CrudSubjectResolver subjectResolver,
        CrudPermissionService permissionService,
        CrudDataScopeResolver dataScopeResolver,
        Collection<CrudDataScopeContributor> dataScopeContributors,
        CrudGovernanceAuditRecorder auditRecorder,
        CrudSpecAttributeResolver specAttributeResolver
    ) {
        if (entityMetaRegistry == null) {
            throw new ValidationException("entityMetaRegistry 不能为空");
        }
        this.entityMetaRegistry = entityMetaRegistry;
        this.specValidator = specValidator == null ? new SpecValidator() : specValidator;
        this.subjectResolver = subjectResolver == null ? new FailClosedCrudSubjectResolver() : subjectResolver;
        this.permissionService = permissionService == null ? new RuleBasedCrudPermissionService() : permissionService;
        this.dataScopeResolver = dataScopeResolver == null ? new DefaultCrudDataScopeResolver() : dataScopeResolver;
        this.dataScopeContributors = dataScopeContributors == null
            ? new ArrayList<CrudDataScopeContributor>()
            : new ArrayList<CrudDataScopeContributor>(dataScopeContributors);
        this.auditRecorder = auditRecorder == null ? new LoggingCrudGovernanceAuditRecorder() : auditRecorder;
        this.subjectNormalizer = new CrudSubjectNormalizer();
        this.scopeIntersectionService = new CrudScopeIntersectionService();
        this.specAttributeResolver = specAttributeResolver == null
            ? new DefaultCrudSpecAttributeResolver()
            : specAttributeResolver;
    }

    @Override
    public <R> CrudGovernanceResult<QueryExecutionSpec<R>> governQuery(QuerySpec<R> spec) {
        GovernanceRun run = new GovernanceRun(spec);
        try {
            run.enter(GovernanceStage.SUBJECT);
            QuerySpec<R> withSubject = ensureSubject(spec);
            run.currentSpec = withSubject;
            run.enter(GovernanceStage.ATTRIBUTES);
            QuerySpec<R> withAttributes = withSubject.toBuilder()
                .attributes(resolveAttributes(withSubject))
                .build();
            run.currentSpec = withAttributes;
            run.enter(GovernanceStage.VALIDATE);
            QuerySpec<R> validated = specValidator.validateQuerySpec(withAttributes);
            run.currentSpec = validated;
            run.enter(GovernanceStage.RESOURCE);
            run.subject = validated.getSubject();
            run.action = resourceAction(validated.getRootType(), validated.getOperationKey(), validated.getScene(), capability(validated));
            run.enter(GovernanceStage.PERMISSION);
            run.accessDecision = requireGrantedDecision(permissionService.decide(run.action, run.subject, validated));
            run.enter(GovernanceStage.SCOPE);
            run.grantedScope = requireScope(dataScopeResolver.resolveQueryScope(run.action, run.subject, validated), "granted");
            run.governanceScope = applyBusinessScopeConstraints(run.action, run.subject, validated, run.grantedScope);
            run.enter(GovernanceStage.ENRICH);
            QueryExecutionSpec<R> effectiveSpec = QueryExecutionSpec.<R>executionBuilder()
                .from(validated)
                .subject(run.subject)
                .accessDecision(run.accessDecision)
                .grantedScope(run.grantedScope)
                .governanceScope(run.governanceScope)
                .build();
            return new CrudGovernanceResult<QueryExecutionSpec<R>>(
                run.subject,
                run.action,
                run.accessDecision,
                run.grantedScope,
                run.governanceScope,
                run.startTimeMs,
                effectiveSpec
            );
        } catch (CrudException ex) {
            throw recordAndReturnGovernanceFailure(run, ex);
        }
    }

    @Override
    public <P> CrudGovernanceResult<CommandExecutionSpec<P>> governCommand(CommandSpec<P> spec) {
        GovernanceRun run = new GovernanceRun(spec);
        try {
            run.enter(GovernanceStage.SUBJECT);
            CommandSpec<P> withSubject = ensureSubject(spec);
            run.currentSpec = withSubject;
            run.enter(GovernanceStage.ATTRIBUTES);
            CommandSpec<P> withAttributes = withSubject.toBuilder()
                .attributes(resolveAttributes(withSubject))
                .build();
            run.currentSpec = withAttributes;
            run.enter(GovernanceStage.VALIDATE);
            CommandSpec<P> validated = specValidator.validateCommandSpec(withAttributes);
            run.currentSpec = validated;
            run.enter(GovernanceStage.RESOURCE);
            run.subject = validated.getSubject();
            run.action = resourceAction(validated.getRootType(), validated.getOperationKey(), validated.getScene(), capability(validated));
            run.enter(GovernanceStage.PERMISSION);
            run.accessDecision = requireGrantedDecision(permissionService.decide(run.action, run.subject, validated));
            run.enter(GovernanceStage.SCOPE);
            run.grantedScope = requireScope(dataScopeResolver.resolveCommandScope(run.action, run.subject, validated), "granted");
            run.governanceScope = applyBusinessScopeConstraints(run.action, run.subject, validated, run.grantedScope);
            run.enter(GovernanceStage.ENRICH);
            CommandExecutionSpec<P> effectiveSpec = CommandExecutionSpec.<P>executionBuilder()
                .from(validated)
                .subject(run.subject)
                .accessDecision(run.accessDecision)
                .grantedScope(run.grantedScope)
                .governanceScope(run.governanceScope)
                .build();
            return new CrudGovernanceResult<CommandExecutionSpec<P>>(
                run.subject,
                run.action,
                run.accessDecision,
                run.grantedScope,
                run.governanceScope,
                run.startTimeMs,
                effectiveSpec
            );
        } catch (CrudException ex) {
            throw recordAndReturnGovernanceFailure(run, ex);
        }
    }

    @Override
    public <S extends BaseSpec & GovernableSpec<S>> CrudGovernanceResult<S> governStats(S spec) {
        GovernanceRun run = new GovernanceRun(spec);
        try {
            run.enter(GovernanceStage.SUBJECT);
            S withSubject = ensureGovernableSubject(spec);
            run.currentSpec = withSubject;
            run.enter(GovernanceStage.ATTRIBUTES);
            S withAttributes = withSubject.withAttributes(resolveAttributes(withSubject));
            run.currentSpec = withAttributes;
            run.enter(GovernanceStage.VALIDATE);
            S validated = validateStatsSpec(withAttributes);
            run.currentSpec = validated;
            run.enter(GovernanceStage.RESOURCE);
            run.subject = validated.getSubject();
            run.action = resourceAction(validated.getRootType(), statsOperationKey(validated), validated.getScene(), capability(validated));
            run.enter(GovernanceStage.PERMISSION);
            run.accessDecision = requireGrantedDecision(permissionService.decide(run.action, run.subject, validated));
            run.enter(GovernanceStage.SCOPE);
            run.grantedScope = requireScope(dataScopeResolver.resolveStatsScope(run.action, run.subject, validated), "granted");
            run.governanceScope = applyBusinessScopeConstraints(run.action, run.subject, validated, run.grantedScope);
            run.enter(GovernanceStage.ENRICH);
            S effectiveSpec = validated.withGovernance(run.accessDecision, run.grantedScope, run.governanceScope);
            return new CrudGovernanceResult<S>(
                run.subject,
                run.action,
                run.accessDecision,
                run.grantedScope,
                run.governanceScope,
                run.startTimeMs,
                effectiveSpec
            );
        } catch (CrudException ex) {
            throw recordAndReturnGovernanceFailure(run, ex);
        }
    }

    @Override
    public CrudGovernanceResult<ImportSpec> governImport(ImportSpec spec) {
        GovernanceRun run = new GovernanceRun(spec);
        try {
            run.enter(GovernanceStage.SUBJECT);
            ImportSpec withSubject = ensureGovernableSubject(spec);
            run.currentSpec = withSubject;
            run.enter(GovernanceStage.ATTRIBUTES);
            ImportSpec withAttributes = withSubject.withAttributes(resolveAttributes(withSubject));
            run.currentSpec = withAttributes;
            run.enter(GovernanceStage.VALIDATE);
            ImportSpec validated = specValidator.validateImportSpec(withAttributes);
            run.currentSpec = validated;
            run.enter(GovernanceStage.RESOURCE);
            run.subject = validated.getSubject();
            run.action = resourceAction(validated.getRootType(), validated.getOperationKey(), validated.getScene(), capability(validated));
            run.enter(GovernanceStage.PERMISSION);
            run.accessDecision = requireGrantedDecision(permissionService.decide(run.action, run.subject, validated));
            run.enter(GovernanceStage.SCOPE);
            run.grantedScope = requireScope(dataScopeResolver.resolveImportScope(run.action, run.subject, validated), "granted");
            run.governanceScope = applyBusinessScopeConstraints(run.action, run.subject, validated, run.grantedScope);
            run.enter(GovernanceStage.ENRICH);
            ImportSpec effectiveSpec = validated.withGovernance(run.accessDecision, run.grantedScope, run.governanceScope);
            return new CrudGovernanceResult<ImportSpec>(
                run.subject,
                run.action,
                run.accessDecision,
                run.grantedScope,
                run.governanceScope,
                run.startTimeMs,
                effectiveSpec
            );
        } catch (CrudException ex) {
            throw recordAndReturnGovernanceFailure(run, ex);
        }
    }

    @Override
    public CrudGovernanceResult<ExportSpec> governExport(ExportSpec spec) {
        GovernanceRun run = new GovernanceRun(spec);
        try {
            run.enter(GovernanceStage.SUBJECT);
            ExportSpec withSubject = ensureGovernableSubject(spec);
            run.currentSpec = withSubject;
            run.enter(GovernanceStage.ATTRIBUTES);
            ExportSpec withAttributes = withSubject.withAttributes(resolveAttributes(withSubject));
            run.currentSpec = withAttributes;
            run.enter(GovernanceStage.VALIDATE);
            ExportSpec validated = specValidator.validateExportSpec(withAttributes);
            run.currentSpec = validated;
            run.enter(GovernanceStage.RESOURCE);
            run.subject = validated.getSubject();
            run.action = resourceAction(validated.getRootType(), validated.getOperationKey(), validated.getScene(), capability(validated));
            run.enter(GovernanceStage.PERMISSION);
            run.accessDecision = requireGrantedDecision(permissionService.decide(run.action, run.subject, validated));
            run.enter(GovernanceStage.SCOPE);
            run.grantedScope = requireScope(dataScopeResolver.resolveExportScope(run.action, run.subject, validated), "granted");
            run.governanceScope = applyBusinessScopeConstraints(run.action, run.subject, validated, run.grantedScope);
            run.enter(GovernanceStage.ENRICH);
            ExportSpec effectiveSpec = validated.withGovernance(run.accessDecision, run.grantedScope, run.governanceScope);
            return new CrudGovernanceResult<ExportSpec>(
                run.subject,
                run.action,
                run.accessDecision,
                run.grantedScope,
                run.governanceScope,
                run.startTimeMs,
                effectiveSpec
            );
        } catch (CrudException ex) {
            throw recordAndReturnGovernanceFailure(run, ex);
        }
    }

    private AccessDecision requireGrantedDecision(AccessDecision accessDecision) {
        if (accessDecision == null) {
            throw new PermissionDeniedException("访问权限判定结果不能为空");
        }
        if (accessDecision == AccessDecision.DENY) {
            throw new PermissionDeniedException("访问权限拒绝");
        }
        return accessDecision;
    }

    private java.util.Map<String, Object> resolveAttributes(BaseSpec spec) {
        try {
            return specAttributeResolver.resolve(spec);
        } catch (CrudException ex) {
            if (ex.getErrorCode() == CrudErrorCode.ATTRIBUTE_CONTRIBUTION_FAILED) {
                throw ex;
            }
            throw new CrudException(CrudErrorCode.ATTRIBUTE_CONTRIBUTION_FAILED, ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new CrudException(CrudErrorCode.ATTRIBUTE_CONTRIBUTION_FAILED, "Spec 属性贡献失败: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void recordAllow(CrudGovernanceResult<?> result) {
        if (result == null) {
            return;
        }
        safeRecord(auditEvent(
            result.getSubject(),
            result.getAction(),
            result.getAccessDecision(),
            result.getGrantedScope(),
            result.getGovernanceScope(),
            CrudGovernanceAuditOutcome.SUCCESS,
            CrudGovernanceAuditReasonCode.NONE,
            System.currentTimeMillis() - result.getStartTimeMs(),
            null,
            routeKey(result.getEffectiveSpec()),
            requestId(result.getEffectiveSpec()),
            traceId(result.getEffectiveSpec())
        ));
    }

    @Override
    public void recordExecutionFailure(CrudGovernanceResult<?> result, Throwable throwable) {
        if (result == null) {
            return;
        }
        safeRecord(auditEvent(
            result.getSubject(),
            result.getAction(),
            result.getAccessDecision(),
            result.getGrantedScope(),
            result.getGovernanceScope(),
            CrudGovernanceAuditOutcome.EXECUTION_FAILED,
            resolveExecutionFailureReason(throwable),
            System.currentTimeMillis() - result.getStartTimeMs(),
            stage(throwable, CrudErrorStage.EXECUTE),
            routeKey(throwable, result.getEffectiveSpec()),
            requestId(result.getEffectiveSpec()),
            traceId(result.getEffectiveSpec())
        ));
    }

    /**
     * 保证 spec 中始终携带规范化后的主体信息。
     */
    private <R> QuerySpec<R> ensureSubject(QuerySpec<R> spec) {
        if (spec == null) {
            throw new ValidationException("请求规范(spec)不能为空");
        }
        SubjectContext current = spec.getSubject() == null ? subjectResolver.resolveOrThrow() : spec.getSubject();
        return spec.toBuilder().subject(subjectNormalizer.normalizeCopy(current)).build();
    }

    /**
     * 保证 command spec 中始终携带规范化后的主体信息。
     */
    private <P> CommandSpec<P> ensureSubject(CommandSpec<P> spec) {
        if (spec == null) {
            throw new ValidationException("请求规范(spec)不能为空");
        }
        SubjectContext current = spec.getSubject() == null ? subjectResolver.resolveOrThrow() : spec.getSubject();
        return spec.toBuilder().subject(subjectNormalizer.normalizeCopy(current)).build();
    }

    private <S extends BaseSpec & GovernableSpec<S>> S ensureGovernableSubject(S spec) {
        if (spec == null) {
            throw new ValidationException("请求规范(spec)不能为空");
        }
        SubjectContext current = spec.getSubject() == null ? subjectResolver.resolveOrThrow() : spec.getSubject();
        return spec.withSubject(subjectNormalizer.normalizeCopy(current));
    }

    private CrudResourceAction resourceAction(Class<?> rootType, CrudOperationKey operationKey, String scene, String capability) {
        if (rootType == null) {
            throw new ValidationException("根类型(rootType)不能为空");
        }
        ResourceDescriptor resourceDescriptor = entityMetaRegistry.getResourceDescriptor(rootType);
        return new CrudResourceAction(resourceDescriptor, operationKey, scene, capability);
    }

    private CrudOperationKey statsOperationKey(BaseSpec spec) {
        if (spec instanceof OperationKeySpec) {
            return ((OperationKeySpec) spec).getOperationKey();
        }
        throw new ValidationException("Stats spec.operationKey 不能为空");
    }

    @SuppressWarnings("unchecked")
    private <S extends BaseSpec & GovernableSpec<S>> S validateStatsSpec(S spec) {
        if (spec instanceof StatsSpec) {
            return (S) specValidator.validateStatsSpec((StatsSpec) spec);
        }
        return specValidator.validateBase(spec);
    }

    private String capability(BaseSpec spec) {
        return stringAttribute(spec, "capability");
    }

    private CrudDataScope requireScope(CrudDataScope scope, String scopeName) {
        if (scope == null) {
            throw new DataScopeDeniedException(scopeName + " 范围不能为空");
        }
        if (!scope.isExplicitAll() && scope.getDimensions().isEmpty()) {
            throw new DataScopeDeniedException(scopeName + " 范围维度不能为空");
        }
        return scope;
    }

    /**
     * 应用业务范围贡献器，并逐步收窄治理范围。
     */
    private CrudDataScope applyBusinessScopeConstraints(
        CrudResourceAction action,
        SubjectContext subject,
        BaseSpec spec,
        CrudDataScope grantedScope
    ) {
        CrudDataScope governanceScope = grantedScope;
        for (CrudDataScopeContributor contributor : dataScopeContributors) {
            if (contributor == null || !contributor.supports(action, spec)) {
                continue;
            }
            CrudDataScope businessScopeConstraint = contributor.contribute(action, subject, spec, grantedScope);
            governanceScope = requireScope(scopeIntersectionService.intersect(governanceScope, businessScopeConstraint), "governance");
        }
        return governanceScope;
    }

    private void recordDeny(
        SubjectContext subject,
        CrudResourceAction action,
        CrudDataScope scope,
        CrudException ex,
        long start,
        BaseSpec spec
    ) {
        safeRecord(auditEvent(
            subject,
            action,
            AccessDecision.DENY,
            scope,
            scope,
            CrudGovernanceAuditOutcome.GOVERNANCE_DENIED,
            CrudGovernanceAuditReasonCode.fromCrudErrorCode(ex.getErrorCode()),
            System.currentTimeMillis() - start,
            ex.getStage() == null ? CrudErrorStage.GOVERNANCE : ex.getStage(),
            routeKey(ex, spec),
            requestId(spec),
            traceId(spec)
        ));
    }

    private CrudGovernanceAuditEvent auditEvent(
        SubjectContext subject,
        CrudResourceAction action,
        AccessDecision accessDecision,
        CrudDataScope grantedScope,
        CrudDataScope governanceScope,
        CrudGovernanceAuditOutcome outcome,
        CrudGovernanceAuditReasonCode reason,
        long costMs,
        CrudErrorStage stage,
        String routeKey,
        String requestId,
        String traceId
    ) {
        return CrudGovernanceAuditEvent.of(
            subject,
            action,
            accessDecision,
            grantedScope,
            governanceScope,
            outcome,
            reason,
            costMs,
            stage,
            routeKey,
            requestId,
            traceId
        );
    }

    private void safeRecord(CrudGovernanceAuditEvent event) {
        try {
            auditRecorder.record(event);
        } catch (RuntimeException ex) {
            log.warn("crud governance audit failed", ex);
        }
    }

    private CrudGovernanceAuditReasonCode resolveExecutionFailureReason(Throwable throwable) {
        if (throwable instanceof CrudException) {
            CrudException crudException = (CrudException) throwable;
            if (crudException.getErrorCode() != null) {
                return CrudGovernanceAuditReasonCode.fromCrudErrorCode(crudException.getErrorCode());
            }
        }
        return CrudGovernanceAuditReasonCode.EXECUTION_FAILED;
    }

    private CrudException recordAndReturnGovernanceFailure(GovernanceRun run, CrudException ex) {
        CrudException enriched = enrichGovernanceFailure(ex, run);
        recordDeny(run.subject, run.action, run.grantedScope, enriched, run.startTimeMs, run.currentSpec);
        return enriched;
    }

    private CrudException enrichGovernanceFailure(CrudException ex, GovernanceRun run) {
        return (CrudException) CrudExceptionContext.enrich(
            ex,
            CrudErrorStage.GOVERNANCE,
            routeKey(run.currentSpec),
            run.reason()
        );
    }

    private CrudErrorStage stage(Throwable throwable, CrudErrorStage fallback) {
        if (throwable instanceof CrudException) {
            CrudErrorStage stage = ((CrudException) throwable).getStage();
            if (stage != null) {
                return stage;
            }
        }
        return fallback;
    }

    private String routeKey(Throwable throwable, BaseSpec spec) {
        if (throwable instanceof CrudException) {
            String routeKey = ((CrudException) throwable).getRouteKey();
            if (routeKey != null && !routeKey.trim().isEmpty()) {
                return routeKey;
            }
        }
        return routeKey(spec);
    }

    private String routeKey(BaseSpec spec) {
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

    private String requestId(BaseSpec spec) {
        return stringAttribute(spec, "requestId");
    }

    private String traceId(BaseSpec spec) {
        return stringAttribute(spec, "traceId");
    }

    private String stringAttribute(BaseSpec spec, String key) {
        if (spec == null || key == null) {
            return null;
        }
        Object value = spec.getAttributes().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private enum GovernanceStage {
        SUBJECT,
        ATTRIBUTES,
        VALIDATE,
        RESOURCE,
        PERMISSION,
        SCOPE,
        ENRICH
    }

    private static final class GovernanceRun {
        private final long startTimeMs = System.currentTimeMillis();
        private GovernanceStage stage = GovernanceStage.SUBJECT;
        private BaseSpec currentSpec;
        private SubjectContext subject;
        private CrudResourceAction action;
        private AccessDecision accessDecision;
        private CrudDataScope grantedScope;
        private CrudDataScope governanceScope;

        private GovernanceRun(BaseSpec currentSpec) {
            this.currentSpec = currentSpec;
        }

        private void enter(GovernanceStage nextStage) {
            this.stage = nextStage;
        }

        private String reason() {
            return "GOVERNANCE_" + stage.name();
        }
    }
}
