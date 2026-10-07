package com.entloom.crud.core.governance.policy;

import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.core.exception.ValidationException;
import java.util.Objects;

/** 项目或适配器提供的操作声明及可诊断来源。 */
public final class BusinessOperationDeclaration {
    private final BusinessOperation operation;
    private final String source;

    public BusinessOperationDeclaration(BusinessOperation operation, String source) {
        this.operation = Objects.requireNonNull(operation, "业务操作不能为空");
        if (source == null || source.trim().isEmpty()) {
            throw new ValidationException("业务操作声明来源不能为空: " + operation);
        }
        this.source = source.trim();
    }

    public BusinessOperation getOperation() {
        return operation;
    }

    public String getSource() {
        return source;
    }
}
