package com.entloom.crud.core.governance.execution;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.CrudExceptionContext;
import com.entloom.crud.core.exception.NotFoundException;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.admission.AccessAdmission;
import com.entloom.crud.core.governance.admission.AccessContextScope;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditReasonCode;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.authorization.AuthorizationResult;
import com.entloom.crud.core.governance.authorization.BusinessAuthorizationService;
import com.entloom.crud.core.governance.authorization.BusinessPermissionDecision;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 原始业务查询的统一治理入口，不依赖 HTTP、CRUD Spec 或存储实现。
 *
 * <p>准备阶段声明实际字段用途并将授权范围转换为查询参数，不执行查询 SQL；
 * 执行阶段只消费准备结果。两阶段均须同步完成，不得保存授权结果供后续调用复用。
 * 本入口负责整次查询审计，回调应使用原始 Mapper，避免再次调用已自行审计的受授权执行器或 DAO。
 * 写操作应使用具备事务提交后审计语义的命令入口。</p>
 */
public final class BusinessQueryExecutor {
    private static final Logger log = LoggerFactory.getLogger(BusinessQueryExecutor.class);
    private final BusinessAuthorizationService authorization;
    private final CrudGovernanceAuditRecorder audit;

    public BusinessQueryExecutor(BusinessAuthorizationService authorization, CrudGovernanceAuditRecorder audit) {
        this.authorization = Objects.requireNonNull(authorization, "业务授权服务不能为空");
        this.audit = Objects.requireNonNull(audit, "治理审计记录器不能为空");
    }

    /** 每次执行重新授权；准备失败时不会调用查询回调，审计故障不改变查询结果或原始异常。 */
    public <P, R> R execute(AuthorizationRequest request, Function<AuthorizationResult, P> prepare,
                            Function<P, R> query) {
        Objects.requireNonNull(request, "授权请求不能为空");
        Objects.requireNonNull(prepare, "查询准备回调不能为空");
        Objects.requireNonNull(query, "查询执行回调不能为空");
        long started = System.nanoTime();
        AuthorizationResult result = null;
        CrudErrorStage stage = CrudErrorStage.GOVERNANCE;
        R value;
        try {
            result = Objects.requireNonNull(authorization.authorize(request), "业务授权服务未返回结果");
            if (result.getDecision() != AccessDecision.ALLOW) {
                if (result.getKind() == BusinessPermissionDecision.Kind.TARGET_INACCESSIBLE) {
                    throw new NotFoundException("查询目标不存在或不可访问");
                }
                throw new PermissionDeniedException("业务查询未授权");
            }
            result.requireApplicable(request);
            P prepared = Objects.requireNonNull(prepare.apply(result), "查询准备结果不能为空");
            stage = CrudErrorStage.EXECUTE;
            value = query.apply(prepared);
        } catch (RuntimeException | Error failure) {
            CrudGovernanceAuditReasonCode reason = reason(result, failure, stage);
            CrudGovernanceAuditOutcome outcome = failureOutcome(reason, stage);
            record(request, result, outcome, reason, stage, started, failure);
            if (failure instanceof RuntimeException) {
                throw CrudExceptionContext.enrich((RuntimeException) failure, stage, routeKey(request));
            }
            throw failure;
        }
        record(request, result, CrudGovernanceAuditOutcome.SUCCESS, CrudGovernanceAuditReasonCode.NONE,
            null, started, null);
        return value;
    }

    private static CrudGovernanceAuditOutcome failureOutcome(CrudGovernanceAuditReasonCode reason, CrudErrorStage stage) {
        if (stage == CrudErrorStage.EXECUTE) return CrudGovernanceAuditOutcome.EXECUTION_FAILED;
        // 只将明确的权限、范围与输入约束归为拒绝；其他框架故障保留为治理失败。
        switch (reason) {
            case PERMISSION_DENIED:
            case DATA_SCOPE_DENIED:
            case VALIDATION_ERROR:
            case TYPE_RESOLUTION_FAILED:
            case ENTITY_SCOPE_ILLEGAL:
            case UNSUPPORTED_QUERY_STRATEGY:
            case UNSUPPORTED_OPERATION:
                return CrudGovernanceAuditOutcome.GOVERNANCE_DENIED;
            default:
                return CrudGovernanceAuditOutcome.GOVERNANCE_FAILED;
        }
    }

    private static CrudGovernanceAuditReasonCode reason(AuthorizationResult result, Throwable failure, CrudErrorStage stage) {
        if (stage == CrudErrorStage.GOVERNANCE && result != null
            && result.getKind() == BusinessPermissionDecision.Kind.TARGET_INACCESSIBLE
            && failure instanceof NotFoundException) {
            return CrudGovernanceAuditReasonCode.DATA_SCOPE_DENIED;
        }
        if (failure instanceof CrudException) {
            return CrudGovernanceAuditReasonCode.fromCrudErrorCode(((CrudException) failure).getErrorCode());
        }
        return stage == CrudErrorStage.GOVERNANCE ? CrudGovernanceAuditReasonCode.INTERNAL_ERROR
            : CrudGovernanceAuditReasonCode.EXECUTION_FAILED;
    }

    private void record(AuthorizationRequest request, AuthorizationResult result, CrudGovernanceAuditOutcome outcome,
                        CrudGovernanceAuditReasonCode reason, CrudErrorStage stage, long started, Throwable failure) {
        try {
            VisitorContext visitor = request.getVisitor();
            // 公共分支只将实际认证主体写入审计，不能重新参与业务授权。
            AccessSubject subject = AccessContextScope.currentAdmission()
                .filter(admission -> admission.getVisitorContext() == visitor)
                .flatMap(AccessAdmission::getAuthenticatedSubject).orElse(visitor.getSubject().orElse(null));
            BusinessOperation operation = request.getOperation();
            audit.record(CrudGovernanceAuditEvent.forBusinessQuery(visitor, subject, request, result,
                new CrudResourceAction(operation.getResourceCode(), operation.getOperationCode(), null),
                outcome, reason, (System.nanoTime() - started) / 1_000_000L, stage, routeKey(request)));
        } catch (RuntimeException | Error auditFailure) {
            if (failure != null && auditFailure != failure) failure.addSuppressed(auditFailure);
            log.error("业务查询审计失败，资源={}，操作={}", request.getOperation().getResourceCode(),
                request.getOperation().getOperationCode(), auditFailure);
        }
    }

    private static String routeKey(AuthorizationRequest request) {
        return request.getOperation().getResourceCode() + "." + request.getOperation().getOperationCode();
    }
}
