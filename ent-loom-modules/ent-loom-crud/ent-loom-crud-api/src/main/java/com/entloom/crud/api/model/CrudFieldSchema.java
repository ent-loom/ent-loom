package com.entloom.crud.api.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 字段 schema 描述。
 */
@Getter
@Setter
public class CrudFieldSchema {
    private String name;
    private String sourceField;
    private String column;
    private String javaType;
    private boolean nullable;
    private boolean relation;
    private boolean filterable;
    private boolean sortable;
}
