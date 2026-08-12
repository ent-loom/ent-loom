package com.entloom.crud.api.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 统计响应 schema 描述。
 */
@Getter
@Setter
public class CrudStatsSchema {
    private String entity;
    private String mode;
    private List<String> dimensions = new ArrayList<String>();
    private List<String> metrics = new ArrayList<String>();
}
