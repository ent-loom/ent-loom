package com.entloom.crud.starter.web.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * page/list/detail 查询请求。
 */
@Getter
@Setter
public class CrudReadHttpRequest extends CrudQueryHttpRequest {
    /** read 查询选项。 */
    private CrudReadOptions options = new CrudReadOptions();

    public void setOptions(CrudReadOptions options) {
        this.options = options == null ? new CrudReadOptions() : options;
    }
}
