package com.entloom.crud.api.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 导入行级错误。
 */
@Getter
@Setter
public class CrudRowErrorData {
    private int rowNumber;
    private String field;
    private String code;
    private String message;
}
