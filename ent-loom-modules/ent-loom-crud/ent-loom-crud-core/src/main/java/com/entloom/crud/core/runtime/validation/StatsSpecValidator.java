package com.entloom.crud.core.runtime.validation;

import com.entloom.crud.core.capability.stats.StatsSpec;
import com.entloom.crud.core.exception.ValidationException;
import java.util.Objects;

/**
 * Stats 规格专用校验器。
 */
public class StatsSpecValidator {
    private final SpecValidator baseValidator;

    public StatsSpecValidator() {
        this(new SpecValidator());
    }

    public StatsSpecValidator(SpecValidator baseValidator) {
        this.baseValidator = Objects.requireNonNull(baseValidator, "baseValidator 不能为空");
    }

    public StatsSpec validate(StatsSpec spec) {
        StatsSpec base = baseValidator.validateBase(spec);
        if (base.getOperationKey() == null) {
            throw new ValidationException("Stats 操作不能为空");
        }
        if (base.getPayload() == null) {
            throw new ValidationException("stats 查询必须提供统计 payload");
        }
        return base;
    }
}
