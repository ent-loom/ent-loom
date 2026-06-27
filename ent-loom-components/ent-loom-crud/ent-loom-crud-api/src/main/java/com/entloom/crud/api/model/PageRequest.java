package com.entloom.crud.api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 分页请求参数。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PageRequest {
    /** 页码。 */
    private int page;
    /** 分页大小。 */
    private int limit;
}
