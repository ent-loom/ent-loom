package com.entloom.crud.api.model;

import com.entloom.crud.api.enums.CountMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 实体列表分页参数。
 *
 * <p>该对象只承载分页和结构化排序，不承载 SQL 片段；排序字段白名单由具体查询入口校验。</p>
 */
public final class PageQuery {
    /** 从 1 开始的页码。 */
    private final int pageNumber;
    /** 单页大小。 */
    private final int pageSize;
    /** 结构化排序条件。 */
    private final List<QuerySort> sorts;
    /** 总数查询策略。 */
    private final CountMode countMode;

    public PageQuery(int pageNumber, int pageSize) {
        this(pageNumber, pageSize, Collections.<QuerySort>emptyList(), CountMode.NONE);
    }

    public PageQuery(int pageNumber, int pageSize, List<QuerySort> sorts, CountMode countMode) {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber 必须从 1 开始");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize 必须大于 0");
        }
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.sorts = copySorts(sorts);
        this.countMode = countMode == null ? CountMode.NONE : countMode;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public List<QuerySort> getSorts() {
        return copySorts(sorts);
    }

    public CountMode getCountMode() {
        return countMode;
    }

    public int pageNumber() {
        return pageNumber;
    }

    public int pageSize() {
        return pageSize;
    }

    public List<QuerySort> sorts() {
        return getSorts();
    }

    public CountMode countMode() {
        return countMode;
    }

    private static List<QuerySort> copySorts(List<QuerySort> source) {
        List<QuerySort> result = new ArrayList<QuerySort>();
        if (source == null) {
            return Collections.unmodifiableList(result);
        }
        for (QuerySort sort : source) {
            if (sort == null) {
                result.add(null);
            } else {
                result.add(new QuerySort(sort.getField(), sort.getDirection(), sort.getTarget()));
            }
        }
        return Collections.unmodifiableList(result);
    }
}
