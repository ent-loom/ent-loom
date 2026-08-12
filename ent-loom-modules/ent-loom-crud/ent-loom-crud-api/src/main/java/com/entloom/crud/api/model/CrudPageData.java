package com.entloom.crud.api.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 分页数据载荷。
 */
@Getter
@Setter
public class CrudPageData<T> extends CrudListData<T> {
    private CrudPageInfo page;
}
