package com.entloom.crud.starter.web.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * page/list/detail 查询请求。
 */
@Getter
@Setter
public class CrudReadHttpRequest extends CrudQueryHttpRequest {
    /** 详情目标主键；仅 detail 路由使用，不能与其他筛选条件混用。 */
    private Object id;

    /** read 查询选项。 */
    private CrudReadOptions options = new CrudReadOptions();

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    public void setOptions(CrudReadOptions options) {
        this.options = options == null ? new CrudReadOptions() : options;
    }
}
