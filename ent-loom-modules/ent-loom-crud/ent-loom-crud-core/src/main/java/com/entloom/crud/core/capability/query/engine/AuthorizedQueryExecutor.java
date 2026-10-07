package com.entloom.crud.core.capability.query.engine;

import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.List;

/** 按次授权、受约束执行与审计的查询入口；缺少访问者或非 CrudRecord 等不支持形状必须拒绝。 */
public interface AuthorizedQueryExecutor {
    List<CrudRecord> list(VisitorContext visitor, QuerySpec<?> spec);

    PageResult<CrudRecord> page(VisitorContext visitor, QuerySpec<?> spec);

    CrudRecord findOne(VisitorContext visitor, QuerySpec<?> spec);

    CrudRecord detail(VisitorContext visitor, QuerySpec<?> spec);
}
