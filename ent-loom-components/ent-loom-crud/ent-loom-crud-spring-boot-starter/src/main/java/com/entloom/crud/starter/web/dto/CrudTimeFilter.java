package com.entloom.crud.starter.web.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 时间过滤参数。
 */
@Getter
@Setter
public class CrudTimeFilter {
    /** 时间字段。 */
    private String field;
    /** 开始时间（包含）。 */
    private String start;
    /** 结束时间（不包含）。 */
    private String end;
    /** 时区。 */
    private String timezone;
    /** 预设时间范围（字符串协议；内置 preset 由框架枚举解析，业务可扩展自定义 preset）。 */
    private String preset;
}
