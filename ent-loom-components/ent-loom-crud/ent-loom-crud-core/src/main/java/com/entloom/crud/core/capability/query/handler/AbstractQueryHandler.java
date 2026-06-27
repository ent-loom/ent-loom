package com.entloom.crud.core.capability.query.handler;

import com.entloom.crud.core.capability.query.engine.QueryEngine;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.List;

/**
 * 查询处理器模板基类。
 *
 * @param <R> 返回类型
 */
public abstract class AbstractQueryHandler<R> implements QueryHandler<R> {
    /** 默认查询引擎。 */
    protected final QueryEngine defaultQueryEngine;

    protected AbstractQueryHandler(QueryEngine defaultQueryEngine) {
        this.defaultQueryEngine = defaultQueryEngine;
    }

    @Override
    public boolean supports(QuerySpec<R> spec) {
        return true;
    }

    @Override
    public PageResult<R> page(QuerySpec<R> spec) {
        PageResult<R> page = defaultQueryEngine.page(spec);
        return afterPage(page, spec);
    }

    /**
     * 分页后置处理钩子。
     *
     * @param page 分页数据
     * @param spec 查询 spec
     * @return 处理结果
     */
    protected PageResult<R> afterPage(PageResult<R> page, QuerySpec<R> spec) {
        return page;
    }

    @Override
    public List<R> list(QuerySpec<R> spec) {
        return defaultQueryEngine.list(spec);
    }

    @Override
    public R findOne(QuerySpec<R> spec) {
        return defaultQueryEngine.findOne(spec);
    }

    @Override
    public R detail(QuerySpec<R> spec) {
        return defaultQueryEngine.detail(spec);
    }
}
