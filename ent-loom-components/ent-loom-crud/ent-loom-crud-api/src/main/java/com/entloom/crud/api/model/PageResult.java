package com.entloom.crud.api.model;

import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 分页结果。
 *
 * @param <T> 记录类型
 */
@Getter
@Setter
public class PageResult<T> {
    /** 数据项列表。 */
    private List<T> items;
    /** 总记录数。 */
    private long total;
    /** 是否已知精确总数。 */
    private boolean totalKnown;
    /** 是否存在下一页。 */
    private Boolean hasNext;
    /** 页码。 */
    private int page;
    /** 分页大小。 */
    private int limit;

    public PageResult() {
        this(Collections.emptyList(), 0, 1, 10);
    }

    public PageResult(List<T> items, long total, int page, int limit) {
        this.items = items;
        this.total = total;
        this.totalKnown = true;
        this.page = page;
        this.limit = limit;
    }

    public long getTotalPages() {
        if (!totalKnown || limit <= 0) {
            return 0;
        }
        return (total + limit - 1) / limit;
    }

    public int getReturned() {
        return items == null ? 0 : items.size();
    }
}
