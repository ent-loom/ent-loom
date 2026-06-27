package com.entloom.crud.api.enums;

import java.util.Locale;

/**
 * CRUD 结构化操作键。
 */
public final class CrudOperationKey {
    private final CrudOperationDomain domain;
    private final String operation;

    private CrudOperationKey(CrudOperationDomain domain, String operation) {
        this.domain = domain;
        this.operation = operation;
    }

    public static CrudOperationKey of(CrudScopedOperation operation) {
        if (operation == null) {
            throw new IllegalArgumentException("operation 不能为空");
        }
        return of(operation.domain(), operation.code());
    }

    public static CrudOperationKey of(CrudOperationDomain domain, String operation) {
        if (domain == null) {
            throw new IllegalArgumentException("operationDomain 不能为空");
        }
        String normalizedOperation = normalizeOperation(operation);
        if (!CrudOperationMatrix.isLegal(domain, normalizedOperation)) {
            throw new IllegalArgumentException("非法操作组合: " + domain + "/" + normalizedOperation);
        }
        return new CrudOperationKey(domain, normalizedOperation);
    }

    public CrudOperationDomain getDomain() {
        return domain;
    }

    public String getOperation() {
        return operation;
    }

    public String getAction() {
        return domain.name() + ":" + operation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CrudOperationKey)) {
            return false;
        }
        CrudOperationKey that = (CrudOperationKey) o;
        return domain == that.domain && operation.equals(that.operation);
    }

    @Override
    public int hashCode() {
        return 31 * domain.hashCode() + operation.hashCode();
    }

    @Override
    public String toString() {
        return domain.name() + "/" + operation;
    }

    private static String normalizeOperation(String operation) {
        if (operation == null || operation.trim().isEmpty()) {
            throw new IllegalArgumentException("operation 不能为空");
        }
        return operation.trim().toUpperCase(Locale.ROOT);
    }
}
