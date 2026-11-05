package com.entloom.crud.core.capability.command.engine;

import com.entloom.crud.core.capability.dao.RowConstraint;
import com.entloom.crud.core.capability.dao.RowConstraintOperator;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import java.util.Objects;

/** Handler 明确声明的关联实体访问；首期仅支持指定字段的非空等值或 IN 范围。 */
public final class RelatedDaoAccess {
    public enum Operation {
        READ, // 读取
        CREATE, // 新增
        UPDATE, // 修改
        DELETE // 删除
    }

    private final Class<?> entityType;
    private final Operation operation;
    private final String scopeField;

    public RelatedDaoAccess(Class<?> entityType, Operation operation, String scopeField) {
        this.entityType = Objects.requireNonNull(entityType, "关联实体不能为空");
        this.operation = Objects.requireNonNull(operation, "关联操作不能为空");
        if (scopeField == null || !scopeField.matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new ValidationException("关联范围字段不规范: " + scopeField);
        this.scopeField = scopeField;
    }

    public Class<?> getEntityType() { return entityType; }
    public String getScopeField() { return scopeField; }

    public void requireScope(RowConstraint scope) {
        if (scope == null || scope.getKind() != RowConstraint.Kind.PREDICATE || !scopeField.equals(scope.getField())
            || scope.getOperator() != RowConstraintOperator.EQ && scope.getOperator() != RowConstraintOperator.IN
            || scope.getValues().isEmpty() || scope.getValues().contains(null))
            throw new PermissionDeniedException("关联 DAO 必须绑定已声明字段的非空等值或 IN 范围: " + scopeField);
    }

    public void requireMethod(String name) {
        Operation requested;
        switch (name) {
            case "findById": case "findAllById": case "findAllByIdAsMap": requested = Operation.READ; break;
            case "insert": case "insertAll": requested = Operation.CREATE; break;
            case "update": case "updateById": case "updateAll": requested = Operation.UPDATE; break;
            case "delete": case "deleteById": case "deleteAll": requested = Operation.DELETE; break;
            default: throw new PermissionDeniedException("未声明的关联 DAO 方法: " + name);
        }
        if (requested != operation) throw new PermissionDeniedException("关联 DAO 未开放该操作: " + name);
    }
}
