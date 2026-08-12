package com.entloom.crud.api.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 响应 schema 描述。
 */
@Getter
@Setter
public class CrudSchema {
    private String kind;
    private String entity;
    private String viewType;
    private List<CrudFieldSchema> fields = new ArrayList<CrudFieldSchema>();
}
