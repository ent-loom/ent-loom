package com.entloom.crud.starter.command;

import com.entloom.crud.core.capability.dao.RowConstraintNormalizer;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.validation.EntityWriteValidator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.MapBindingResult;
import org.springframework.validation.SmartValidator;
import org.springframework.validation.Validator;

/** 复用应用校验器：更新检查显式字段，创建检查完整实体与跨字段约束。 */
public final class SpringEntityWriteValidator implements EntityWriteValidator {
    private final ObjectProvider<Validator> validators;

    public SpringEntityWriteValidator(ObjectProvider<Validator> validators) {
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

    @Override
    public void validateCreate(EntityMeta meta, Map<String, Object> values) {
        Validator validator = validators.orderedStream().filter(candidate -> candidate.supports(meta.getEntityType()))
            .findFirst().orElse(null);
        if (validator == null) return;
        Object entity = BeanUtils.instantiateClass(meta.getEntityType());
        DirectFieldAccessor accessor = new DirectFieldAccessor(entity);
        values.forEach(accessor::setPropertyValue);
        var errors = new BeanPropertyBindingResult(entity, meta.getResourceDescriptor().getResourceCode());
        validator.validate(entity, errors);
        if (errors.hasErrors()) {
            throw new ValidationException(errors.getAllErrors().stream().map(error -> error.getDefaultMessage())
                .distinct().sorted().collect(Collectors.joining("；")));
        }
    }
}
