package com.entloom.crud.core.capability.query;

import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.Collections;
import java.util.List;

/**
 * 不补齐默认排序的解析器。
 */
public final class NoOpQueryDefaultSortResolver implements QueryDefaultSortResolver {
    /** 单例实例。 */
    public static final NoOpQueryDefaultSortResolver INSTANCE = new NoOpQueryDefaultSortResolver();

    private NoOpQueryDefaultSortResolver() {
    }

    @Override
    public List<QuerySort> resolve(QuerySpec<?> spec, EntityMeta rootMeta) {
        return Collections.emptyList();
    }
}
