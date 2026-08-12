package com.entloom.crud.starter.web.facade;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.runtime.context.CrudInvocationContext;
import com.entloom.crud.core.runtime.context.CrudRequestContextHolder;
import com.entloom.crud.core.capability.command.gateway.CommandGateway;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.starter.web.assembler.CrudCommandSpecAssembler;
import com.entloom.crud.starter.web.dto.CrudCommandHttpRequest;
import com.entloom.crud.starter.web.support.CrudResponseBuilder;
import lombok.RequiredArgsConstructor;

/**
 * 供业务层 Controller 复用的命令门面。
 */
@RequiredArgsConstructor
public class EntCrudCommandFacade {
    private final CommandGateway commandGateway;
    private final CrudSubjectResolver subjectResolver;
    private final CrudCommandSpecAssembler commandSpecAssembler;
    private final CrudResponseBuilder crudResponseBuilder;

    public CrudResponse<?> create(String entity, String scene, CrudCommandHttpRequest request, CrudInvocationContext context) {
        return action(CommandOperation.CREATE, entity, scene, request, context);
    }

    public CrudResponse<?> update(String entity, String scene, CrudCommandHttpRequest request, CrudInvocationContext context) {
        return action(CommandOperation.UPDATE, entity, scene, request, context);
    }

    public CrudResponse<?> delete(String entity, String scene, CrudCommandHttpRequest request, CrudInvocationContext context) {
        return action(CommandOperation.DELETE, entity, scene, request, context);
    }

    public CrudResponse<?> saveOrUpdate(String entity, String scene, CrudCommandHttpRequest request, CrudInvocationContext context) {
        return action(CommandOperation.SAVE_OR_UPDATE, entity, scene, request, context);
    }

    public CrudResponse<?> createBatch(String entity, String scene, CrudCommandHttpRequest request, CrudInvocationContext context) {
        return action(CommandOperation.CREATE_BATCH, entity, scene, request, context);
    }

    public CrudResponse<?> updateBatch(String entity, String scene, CrudCommandHttpRequest request, CrudInvocationContext context) {
        return action(CommandOperation.UPDATE_BATCH, entity, scene, request, context);
    }

    public CrudResponse<?> deleteBatch(String entity, String scene, CrudCommandHttpRequest request, CrudInvocationContext context) {
        return action(CommandOperation.DELETE_BATCH, entity, scene, request, context);
    }

    public CrudResponse<?> saveOrUpdateBatch(String entity, String scene, CrudCommandHttpRequest request, CrudInvocationContext context) {
        return action(CommandOperation.SAVE_OR_UPDATE_BATCH, entity, scene, request, context);
    }

    public CrudResponse<?> action(String entity, String scene, CrudCommandHttpRequest request, CrudInvocationContext context) {
        return action(CommandOperation.ACTION, entity, scene, request, context);
    }

    private CrudResponse<?> action(CommandOperation op, String entity, String scene, CrudCommandHttpRequest request, CrudInvocationContext context) {
        CrudCommandHttpRequest actualRequest = request == null ? new CrudCommandHttpRequest() : request;
        crudResponseBuilder.bind(commandSpecAssembler.resolveRequestId(actualRequest), CrudOperationKey.of(op));
        return withContext(context, () -> {
            SubjectContext subject = subjectResolver.resolveOrThrow();
            CommandSpec<Object> spec = commandSpecAssembler.assemble(
                entity,
                op,
                scene,
                actualRequest,
                subject
            );
            return adapt(commandGateway.action(spec));
        });
    }

    private <T> T withContext(CrudInvocationContext context, java.util.function.Supplier<T> supplier) {
        CrudInvocationContext actualContext = context == null ? CrudInvocationContext.empty() : context;
        return CrudRequestContextHolder.withAttributes(actualContext.getAttributes(), supplier);
    }

    @SuppressWarnings("unchecked")
    private CrudResponse<?> adapt(Object result) {
        if (result instanceof CommandResult<?>) {
            CommandResult<?> commandResult = (CommandResult<?>) result;
            java.util.Map<String, Object> meta = new java.util.LinkedHashMap<String, Object>();
            if (commandResult.isIdempotentReplay()) {
                meta.put("idempotentReplay", Boolean.TRUE);
            }
            Object data = commandResult.getData();
            if (data instanceof java.util.Map<?, ?>) {
                data = CrudRecord.copyOf((java.util.Map<String, Object>) data);
            }
            return crudResponseBuilder.respond(
                commandResult.isSuccess(),
                commandResult.getCode() == null ? "OK" : commandResult.getCode(),
                commandResult.getMessage() == null ? "OK" : commandResult.getMessage(),
                data,
                meta
            );
        }
        if (result instanceof java.util.Map<?, ?>) {
            return crudResponseBuilder.success(CrudRecord.copyOf((java.util.Map<String, Object>) result));
        }
        return crudResponseBuilder.success(result);
    }
}
