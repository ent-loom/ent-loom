package com.entloom.crud.core.runtime.validation;

import com.entloom.crud.api.enums.ExportOperation;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.exception.ValidationException;
import java.util.Objects;

/**
 * Export 规格专用校验器。
 */
public class ExportSpecValidator {
    private final SpecValidator baseValidator;

    public ExportSpecValidator() {
        this(new SpecValidator());
    }

    public ExportSpecValidator(SpecValidator baseValidator) {
        this.baseValidator = Objects.requireNonNull(baseValidator, "baseValidator 不能为空");
    }

    public ExportSpec validate(ExportSpec spec) {
        ExportSpec base = baseValidator.validateBase(spec);
        ExportOperation operation = base.getOperation();
        if (operation == null) {
            throw new ValidationException("导出操作不能为空");
        }
        switch (operation) {
            case PREVIEW:
            case SUBMIT:
                requireFormat(base);
                break;
            case DOWNLOAD:
            case STATUS:
            case CANCEL:
                requireTaskId(base);
                break;
            default:
                break;
        }
        return base;
    }

    private void requireFormat(ExportSpec spec) {
        if (isBlank(spec.getFormat())) {
            throw new ValidationException("导出格式(format)不能为空");
        }
    }

    private void requireTaskId(ExportSpec spec) {
        if (isBlank(spec.getTaskId())) {
            throw new ValidationException("导出任务 ID 不能为空");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
