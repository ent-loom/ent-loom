package com.entloom.crud.core.capability.query.engine;

import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.List;

/** 按次授权的类型化查询入口；普通业务使用实体或 DTO，动态投影显式选择记录类型。 */
public interface AuthorizedQueryExecutor {
    <R> List<R> list(VisitorContext visitor, QuerySpec<R> spec);

    <R> PageResult<R> page(VisitorContext visitor, QuerySpec<R> spec);

    <R> R findOne(VisitorContext visitor, QuerySpec<R> spec);

    <R> R detail(VisitorContext visitor, QuerySpec<R> spec);
}
