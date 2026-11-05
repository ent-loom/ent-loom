package com.entloom.crud.starter.command;

import com.entloom.crud.core.capability.dao.RowConstraintNormalizer;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.validation.EntityUpdateValidator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.validation.MapBindingResult;
import org.springframework.validation.SmartValidator;
import org.springframework.validation.Validator;

/** 复用应用的字段校验器，仅检查 Patch 中显式提交的字段。 */
public final class SpringEntityUpdateValidator implements EntityUpdateValidator {
    private final ObjectProvider<Validator> validators;

    public SpringEntityUpdateValidator(ObjectProvider<Validator> validators) {
        this.validators = validators;
    }

    @Override
    public void validate(EntityMeta meta, Map<String, Object> values) {
        SmartValidator validator = validators.orderedStream().filter(SmartValidator.class::isInstance)
            .map(SmartValidator.class::cast).findFirst().orElse(null);
        if (validator == null) return;
        Map<String, Object> normalized = new LinkedHashMap<>();
        values.forEach((field, value) -> normalized.put(field,
            RowConstraintNormalizer.normalizeValue(meta.resolveFieldMeta(field), value)));
        var errors = new MapBindingResult(normalized, meta.getResourceDescriptor().getResourceCode());
        normalized.forEach((field, value) -> validator.validateValue(meta.getEntityType(), field, value, errors));
        if (errors.hasErrors()) {
            throw new ValidationException(errors.getAllErrors().stream().map(error -> error.getDefaultMessage())
                .distinct().sorted().collect(Collectors.joining("；")));
        }
    }
}
