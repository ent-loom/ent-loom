package com.entloom.crud.core.capability.command.engine;

import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.*;
import com.entloom.crud.core.capability.command.spec.*;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.foundation.write.*;
import com.entloom.crud.core.governance.adapter.CrudBusinessAuthorizationAdapter;
import com.entloom.crud.core.governance.admission.AccessContextScope;
import com.entloom.crud.core.governance.audit.*;
import com.entloom.crud.core.governance.authorization.AuthorizationResult;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.governance.policy.ScenePolicyRegistry;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.router.CommandRouter;
import java.util.LinkedHashMap;

/** ACTION 复用统一授权、路由、事务与审计；标准写入继续交给受授权 JDBC 执行器。 */
public final class AuthorizedActionCommandExecutor implements AuthorizedCommandExecutor {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AuthorizedActionCommandExecutor.class);
    private final AuthorizedCommandExecutor commands;
    private final CrudBusinessAuthorizationAdapter authorization;
    private final CommandRouter router;
    private final EntityMetaRegistry entities;
    private final CrudWriteTransactionExecutor transactions;
    private final CrudGovernanceAuditRecorder audit;
    private final ScenePolicyRegistry scenes;

    public AuthorizedActionCommandExecutor(AuthorizedCommandExecutor commands, CrudBusinessAuthorizationAdapter authorization,
        CommandRouter router, EntityMetaRegistry entities, CrudWriteTransactionExecutor transactions,
        CrudGovernanceAuditRecorder audit, ScenePolicyRegistry scenes) {
        this.commands = commands;
        this.authorization = authorization;
        this.router = router;
        this.entities = entities;
        this.transactions = transactions;
        this.audit = audit;
        this.scenes = scenes;
    }

    @Override
    public <P, R> R action(VisitorContext visitor, CommandSpec<P> source) {
        if (source == null || source.getOp() != CommandOperation.ACTION) return commands.action(visitor, source);
        long started = System.nanoTime();
        AuthorizationResult result = null;
        CrudErrorStage stage = CrudErrorStage.GOVERNANCE;
        java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean observed = new java.util.concurrent.atomic.AtomicBoolean();
        com.entloom.crud.api.access.AccessSubject subject = AccessContextScope.currentAdmission()
            .flatMap(admission -> admission.getAuthenticatedSubject()).orElse(null);
        try {
            if (visitor == null || visitor != AccessContextScope.currentVisitor().orElse(null)) throw new PermissionDeniedException("业务动作缺少当前可信访问者");
            if (source.isDryRun() || source.getIdempotencyKey() != null && !source.getIdempotencyKey().trim().isEmpty())
                throw new com.entloom.crud.core.exception.ValidationException("业务动作尚不支持 dry-run 或幂等键");
            CommandSpec<P> spec = source.toBuilder().subject(null).build();
            String resource = entities.getEntityMeta(spec.getRootType()).getResourceDescriptor().getResourceCode();
            com.entloom.crud.core.governance.policy.ScenePolicy policy = scenes.find(new com.entloom.crud.core.governance.policy.ScenePolicyKey(
                visitor.getAccessEntry(), resource, spec.getOperationKey(), spec.getScene()));
            if (policy == null) throw new PermissionDeniedException("业务动作未向当前入口开放");
            String portal = new com.entloom.crud.core.adapter.AttributePortalResolver().resolvePortal(spec);
            if (!policy.getAllowedPortals().isEmpty() && !policy.getAllowedPortals().contains(portal))
                throw new PermissionDeniedException("业务动作未向当前 portal 开放");
            result = authorization.authorize(visitor, spec, null);
            if (result.getDecision() != AccessDecision.ALLOW) throw new PermissionDeniedException("业务动作授权拒绝");
            LinkedHashMap<String, Object> attributes = new LinkedHashMap<String, Object>(spec.getAttributes());
            if (!(router instanceof com.entloom.crud.core.runtime.router.CommandActionSceneResolver))
                throw new PermissionDeniedException("业务动作路由器缺少契约解析能力");
            com.entloom.crud.core.capability.command.handler.CommandActionContract contract =
                ((com.entloom.crud.core.runtime.router.CommandActionSceneResolver) router).resolveActionContract(
                    spec.getRootType(), spec.getEntityClasses(), spec.getScene());
            attributes.put(AuthorizedActionContext.ATTRIBUTE, new AuthorizedActionContext(
                spec.getRootType(), visitor, result, contract.getRelatedAccess()));
            CommandExecutionSpec<P> execution = CommandExecutionSpec.<P>executionBuilder().from(spec).attributes(attributes).build();
            com.entloom.crud.core.runtime.router.CommandRoute<P, R> route = router.<P, R>route(execution);
            stage = CrudErrorStage.EXECUTE;
            final AuthorizationResult authorized = result;
            return transactions.executeObserved(CrudWriteTransactionPolicy.SINGLE_TRANSACTION,
                () -> {
                    observed.set(true);
                    return route.handler().action(execution);
                }, committed -> {
                    completed.set(true);
                    recordSafely(visitor, subject, source, authorized, committed ? CrudGovernanceAuditOutcome.SUCCESS
                        : CrudGovernanceAuditOutcome.EXECUTION_FAILED, committed ? CrudGovernanceAuditReasonCode.NONE
                        : CrudGovernanceAuditReasonCode.EXECUTION_FAILED, started, CrudErrorStage.EXECUTE);
                });
        } catch (RuntimeException | Error failure) {
            if (!observed.get() && !completed.get()) recordSafely(visitor, subject, source, result,
                stage == CrudErrorStage.EXECUTE ? CrudGovernanceAuditOutcome.EXECUTION_FAILED
                    : CrudGovernanceAuditOutcome.GOVERNANCE_DENIED,
                failure instanceof com.entloom.crud.core.exception.CrudException
                    ? CrudGovernanceAuditReasonCode.fromCrudErrorCode(((com.entloom.crud.core.exception.CrudException) failure).getErrorCode())
                    : CrudGovernanceAuditReasonCode.EXECUTION_FAILED, started, stage);
            throw failure;
        }
    }

    private void recordSafely(VisitorContext visitor, com.entloom.crud.api.access.AccessSubject subject,
        CommandSpec<?> spec, AuthorizationResult result, CrudGovernanceAuditOutcome outcome,
        CrudGovernanceAuditReasonCode reason, long started, CrudErrorStage stage) {
        try { record(visitor, subject, spec, result, outcome, reason, started, stage); }
        catch (RuntimeException failure) {
            LOG.warn("业务动作最终审计失败，业务结果为 {}，动作为 {}", outcome, spec.getScene(), failure);
        }
    }

    private void record(VisitorContext visitor, com.entloom.crud.api.access.AccessSubject subject,
        CommandSpec<?> spec, AuthorizationResult result,
        CrudGovernanceAuditOutcome outcome, CrudGovernanceAuditReasonCode reason, long started, CrudErrorStage stage) {
        com.entloom.crud.core.runtime.meta.EntityMeta meta = entities.getEntityMeta(spec.getRootType());
        audit.record(CrudGovernanceAuditEvent.forBusinessCommand(visitor,
            subject, result,
            new CrudResourceAction(meta.getResourceDescriptor(), spec.getOperationKey(), spec.getScene(), null),
            outcome, reason, (System.nanoTime() - started) / 1_000_000, stage, null));
    }
}
