package com.entloom.crud.api.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 列表数据载荷。
 */
@Getter
@Setter
public class CrudListData<T> {
    private List<T> items = new ArrayList<T>();
}
