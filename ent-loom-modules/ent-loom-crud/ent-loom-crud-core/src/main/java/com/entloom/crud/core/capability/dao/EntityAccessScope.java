package com.entloom.crud.core.capability.dao;

import com.entloom.crud.core.exception.ValidationException;

/**
 * EntityDao 创建时绑定的不可变访问范围。
 */
public final class EntityAccessScope {
    private final RowConstraint rowConstraint;

    private EntityAccessScope(RowConstraint rowConstraint) {
        if (rowConstraint == null) {
            throw new ValidationException("EntityAccessScope.rowConstraint 不能为空");
        }
        this.rowConstraint = rowConstraint;
    }

    public static EntityAccessScope of(RowConstraint rowConstraint) {
        return new EntityAccessScope(rowConstraint);
    }

    public static EntityAccessScope unrestricted() {
        return new EntityAccessScope(RowConstraint.unrestricted());
    }

    public RowConstraint getRowConstraint() {
        return rowConstraint;
    }

    public RowConstraint rowConstraint() {
        return rowConstraint;
    }
}
