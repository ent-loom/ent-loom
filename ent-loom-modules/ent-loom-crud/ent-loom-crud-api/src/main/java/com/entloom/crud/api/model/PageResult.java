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
    /** 总记录数；未执行计数时为 null。 */
    private Long total;
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
        this.total = Long.valueOf(total);
        this.totalKnown = true;
        this.page = page;
        this.limit = limit;
    }

    /** 创建不查询总数的分页结果。 */
    public static <T> PageResult<T> withoutTotal(
        List<T> items,
        int page,
        int limit,
        boolean hasNext
    ) {
        PageResult<T> result = new PageResult<T>(items, 0L, page, limit);
        result.total = null;
        result.totalKnown = false;
        result.hasNext = Boolean.valueOf(hasNext);
        return result;
    }

    public int getPageNumber() {
        return page;
    }

    public int getPageSize() {
        return limit;
    }

    public void setPageNumber(int pageNumber) {
        this.page = pageNumber;
    }

    public void setPageSize(int pageSize) {
        this.limit = pageSize;
    }

    public long getTotalPages() {
        if (!totalKnown || total == null || limit <= 0) {
            return 0;
        }
        return (total.longValue() + limit - 1) / limit;
    }

    public int getReturned() {
        return items == null ? 0 : items.size();
    }
}
