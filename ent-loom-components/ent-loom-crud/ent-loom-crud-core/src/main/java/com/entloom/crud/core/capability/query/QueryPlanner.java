package com.entloom.crud.core.capability.query;

import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.capability.query.spec.QuerySpec;

/**
 * 查询计划器。
 */
public interface QueryPlanner {
    /**
     * 生成查询计划。
     *
     * @param spec 查询协议
     * @param entityMeta 根实体元数据
     * @param relationGraph 关系图
     * @return 查询计划
     */
    QueryPlan plan(QuerySpec<?> spec, EntityMeta entityMeta, RelationGraph relationGraph);
}
