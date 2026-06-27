package com.entloom.crud.core.runtime.spec;

import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.api.model.QueryTimeRange;
import java.util.List;

/**
 * 带查询过滤/排序/时间范围的 spec 视图。
 */
public interface FilterableSpec {
    List<QueryFilter> getFilters();

    List<QuerySort> getSorts();

    QueryTimeRange getTime();
}
