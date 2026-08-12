package com.entloom.crud.api.model;

import lombok.Getter;

/**
 * 影响行数结果。
 */
@Getter
public class RowsResult {
    private final int rows;

    private RowsResult(int rows) {
        this.rows = rows;
    }

    public static RowsResult of(int rows) {
        return new RowsResult(rows);
    }
}
