package com.entloom.crud.core.capability.command.gateway;

import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.core.exception.CrudExceptionContext;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.execution.ExecutionPipeline;
import com.entloom.crud.core.governance.service.CrudGovernanceResult;
import com.entloom.crud.core.idempotency.IdempotencyManager;
import com.entloom.crud.core.idempotency.IdempotencyPolicy;
import com.entloom.crud.core.runtime.router.CommandRoute;
import com.entloom.crud.core.runtime.router.CommandRouter;
import com.entloom.crud.core.capability.command.spec.CommandExecutionSpec;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.runtime.spec.SpecSnapshotFactory;
import com.entloom.crud.core.util.RouteKeyFactory;
import java.util.Objects;

/**
 * 默认命令网关实现。
 */
public class CommandGatewayImpl implements CommandGateway {
    /** 命令路由器。 */
    private final CommandRouter commandRouter;
    /** 幂等管理器。 */
    private final IdempotencyManager idempotencyManager;
    /** 幂等策略。 */
    private final IdempotencyPolicy idempotencyPolicy;
    /** 统一执行管线。 */
    private final ExecutionPipeline executionPipeline;

    public CommandGatewayImpl(
        CommandRouter commandRouter,
        IdempotencyManager idempotencyManager,
        IdempotencyPolicy idempotencyPolicy,
        ExecutionPipeline executionPipeline
    ) {
        this.commandRouter = Objects.requireNonNull(commandRouter, "commandRouter 不能为空");
        this.idempotencyManager = idempotencyManager;
        this.idempotencyPolicy = idempotencyPolicy == null ? new IdempotencyPolicy() : idempotencyPolicy;
        this.executionPipeline = Objects.requireNonNull(executionPipeline, "executionPipeline 不能为空");
    }

    /**
     * 执行当前命令流程。
     */
    @Override
    public <P, R> R action(CommandSpec<P> spec) {
        return executionPipeline.execute(
            () -> prepareRequestSpec(spec),
            requestSpec -> executionPipeline.governCommand(requestSpec),
            (requestSpec, governedSpec, governance) -> executeAction(governedSpec, governance)
        );
    }

    private <P, R> R executeAction(
        CommandExecutionSpec<P> executionSpec,
        CrudGovernanceResult<?> governance
    ) {
        CommandRoute<P, R> route = route(executionSpec);
        if (idempotencyManager == null || !idempotencyPolicy.shouldApply(executionSpec)) {
            return route.handler().action(executionSpec);
        }
        String tenantId = governance.getSubject().getTenantId();
        String routeKey = RouteKeyFactory.buildCommandRouteKey(executionSpec);
        String storageKey = idempotencyManager.buildStorageKey(
            tenantId,
            routeKey,
            executionSpec.getScene(),
            executionSpec.getIdempotencyKey()
        );
        return idempotencyManager.executeWithIdempotency(
            storageKey,
            executionSpec.getPayload(),
            () -> route.handler().action(executionSpec)
        );
    }

    private <P> CommandSpec<P> prepareRequestSpec(CommandSpec<P> spec) {
        if (spec == null) {
            throw new ValidationException("请求规范(spec)不能为空");
        }
        return SpecSnapshotFactory.copy(spec);
    }

    private <P, R> CommandRoute<P, R> route(CommandExecutionSpec<P> executionSpec) {
        try {
            return commandRouter.route(executionSpec);
        } catch (RuntimeException ex) {
            throw CrudExceptionContext.enrich(ex, CrudErrorStage.ROUTE, safeRouteKey(executionSpec));
        }
    }

    private String safeRouteKey(CommandSpec<?> spec) {
        try {
            return RouteKeyFactory.buildCommandRouteKey(spec);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
