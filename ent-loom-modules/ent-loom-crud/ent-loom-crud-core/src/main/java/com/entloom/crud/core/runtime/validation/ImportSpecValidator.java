package com.entloom.crud.core.runtime.validation;

import com.entloom.crud.api.enums.ImportOperation;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.exception.ValidationException;
import java.util.Objects;

/**
 * Import 规格专用校验器。
 */
public class ImportSpecValidator {
    private final SpecValidator baseValidator;

    public ImportSpecValidator() {
        this(new SpecValidator());
    }

    public ImportSpecValidator(SpecValidator baseValidator) {
        this.baseValidator = Objects.requireNonNull(baseValidator, "baseValidator 不能为空");
    }

    public ImportSpec validate(ImportSpec spec) {
        ImportSpec base = baseValidator.validateBase(spec);
        ImportOperation operation = base.getOperation();
        if (operation == null) {
            throw new ValidationException("导入操作不能为空");
        }
        switch (operation) {
            case VALIDATE:
            case SUBMIT:
                requireFormat(base);
                requireSourceFile(base);
                break;
            case COMMIT:
            case CANCEL:
            case STATUS:
            case DOWNLOAD_ERROR:
                requireTaskId(base);
                break;
            default:
                break;
        }
        return base;
    }

    private void requireFormat(ImportSpec spec) {
        if (isBlank(spec.getFormat())) {
            throw new ValidationException("导入格式(format)不能为空");
        }
    }

    private void requireSourceFile(ImportSpec spec) {
        if (spec.getSourceFile() == null || isBlank(spec.getSourceFile().getFileId())) {
            throw new ValidationException("导入 sourceFile.fileId 不能为空");
        }
    }

    private void requireTaskId(ImportSpec spec) {
        if (isBlank(spec.getTaskId())) {
            throw new ValidationException("导入任务 ID 不能为空");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
