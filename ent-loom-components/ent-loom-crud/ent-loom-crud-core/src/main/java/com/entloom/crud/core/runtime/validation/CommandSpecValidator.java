package com.entloom.crud.core.runtime.validation;

import com.entloom.crud.core.exception.IdempotencyKeyRequiredException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.idempotency.IdempotencyPolicy;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import java.util.Objects;

/**
 * Command 规格专用校验器。
 */
public class CommandSpecValidator {
    private final SpecValidator baseValidator;
    private final IdempotencyPolicy idempotencyPolicy;

    public CommandSpecValidator() {
        this(new SpecValidator(), new IdempotencyPolicy());
    }

    public CommandSpecValidator(SpecValidator baseValidator) {
        this(baseValidator, new IdempotencyPolicy());
    }

    public CommandSpecValidator(SpecValidator baseValidator, IdempotencyPolicy idempotencyPolicy) {
        this.baseValidator = Objects.requireNonNull(baseValidator, "baseValidator 不能为空");
        this.idempotencyPolicy = idempotencyPolicy == null ? new IdempotencyPolicy() : idempotencyPolicy;
    }

    /**
     * 校验命令规格并返回规范化副本。
     */
    public <P> CommandSpec<P> validate(CommandSpec<P> spec) {
        CommandSpec<P> base = baseValidator.validateBase(spec);
        if (base.getOp() == null) {
            throw new ValidationException("命令操作不能为空");
        }
        if (base.getResultType() == null) {
            throw new ValidationException("命令结果类型(resultType)不能为空，无返回结果时请使用 Void.class");
        }
        if (idempotencyPolicy.isRequired(base)) {
            if (base.getIdempotencyKey() == null || base.getIdempotencyKey().trim().isEmpty()) {
                throw new IdempotencyKeyRequiredException("当前命令策略要求必须提供幂等键(idempotencyKey)");
            }
        }
        return base;
    }
}
