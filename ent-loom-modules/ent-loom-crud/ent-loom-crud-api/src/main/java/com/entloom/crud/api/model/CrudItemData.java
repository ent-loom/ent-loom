package com.entloom.crud.api.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 单项数据载荷。
 */
@Getter
@Setter
public class CrudItemData<T> {
    private T item;
}
