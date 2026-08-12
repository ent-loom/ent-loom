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
    private long total;
    private boolean totalKnown;
    private Boolean hasNext;
    private long totalPages;
    private int returned;
}
