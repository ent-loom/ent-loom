package com.entloom.crud.api.access;

import java.util.Objects;

/** 与 CRUD Spec 无关的稳定资源操作标识。 */
public final class BusinessOperation {
    private final String resourceCode;
    private final String operationCode;

    public BusinessOperation(String resourceCode, String operationCode) {
        this.resourceCode = requireCode(resourceCode, "资源 code");
        this.operationCode = requireCode(operationCode, "操作 code");
    }

    public String getResourceCode() {
        return resourceCode;
    }

    public String getOperationCode() {
        return operationCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BusinessOperation)) {
            return false;
        }
        BusinessOperation operation = (BusinessOperation) other;
        return resourceCode.equals(operation.resourceCode) && operationCode.equals(operation.operationCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceCode, operationCode);
    }

    @Override
    public String toString() {
        return resourceCode + "." + operationCode;
    }

    private static String requireCode(String code, String name) {
        if (code == null || code.trim().isEmpty() || !code.equals(code.trim())) {
            throw new IllegalArgumentException(name + " 不能为空或包含首尾空格");
        }
        return code;
    }
}
