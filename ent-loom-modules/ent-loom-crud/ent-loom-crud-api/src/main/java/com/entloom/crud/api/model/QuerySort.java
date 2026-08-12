package com.entloom.crud.api.model;

import com.entloom.crud.api.enums.SortDirection;
import com.entloom.crud.api.enums.SortTarget;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 排序表达式。
 */
@Getter
@Setter
@NoArgsConstructor
public class QuerySort {
    /** 字段名。 */
    private String field;
    /** 排序方向。 */
    private SortDirection direction = SortDirection.ASC;
    /** 排序目标。 */
    private SortTarget target = SortTarget.AUTO;

    public QuerySort(String field, SortDirection direction) {
        this(field, direction, SortTarget.AUTO);
    }

    public QuerySort(String field, SortDirection direction, SortTarget target) {
        this.field = field;
        this.direction = direction == null ? SortDirection.ASC : direction;
        this.target = target == null ? SortTarget.AUTO : target;
    }
}
