package com.entloom.crud.api.model;

import lombok.Getter;

/**
 * 创建结果。
 */
@Getter
public class CreateResult {
    private final int rows;
    private final Object id;

    private CreateResult(int rows, Object id) {
        this.rows = rows;
        this.id = id;
    }

    public static CreateResult of(Object id) {
        return new CreateResult(1, id);
    }

    public static CreateResult of(int rows, Object id) {
        return new CreateResult(rows, id);
    }
}
