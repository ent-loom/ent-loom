package com.entloom.crud.core.capability.query;

import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.List;

/**
 * 查询默认排序解析器。
 */
public interface QueryDefaultSortResolver {
    /**
     * 解析需要补齐的默认排序。
     *
     * @param spec 查询规格
     * @param rootMeta 根实体元数据
     * @return 默认排序；空列表表示不补齐默认排序
     */
    List<QuerySort> resolve(QuerySpec<?> spec, EntityMeta rootMeta);
}
