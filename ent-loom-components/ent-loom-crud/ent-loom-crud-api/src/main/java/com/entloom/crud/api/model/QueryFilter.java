package com.entloom.crud.api.model;

import com.entloom.crud.api.enums.FilterOperator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 结构化过滤表达式。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QueryFilter {
    /** 字段名。 */
    private String field;
    /** 过滤操作符。 */
    @JsonProperty("op")
    private FilterOperator operator;
    /** 取值。 */
    private Object value;
}
