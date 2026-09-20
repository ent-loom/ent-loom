package com.entloom.crud.api.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 分页信息。
 */
@Getter
@Setter
public class CrudPageInfo {
    private int page;
    private int limit;
    /** 总记录数；未执行计数时为 null。 */
    private Long total;
    private boolean totalKnown;
    private Boolean hasNext;
    private long totalPages;
    private int returned;
}
