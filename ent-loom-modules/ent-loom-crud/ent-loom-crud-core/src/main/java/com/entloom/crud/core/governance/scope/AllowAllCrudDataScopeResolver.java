package com.entloom.crud.core.governance.scope;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;

/**
 * 默认全量范围实现。
 */
public class AllowAllCrudDataScopeResolver implements CrudDataScopeResolver {
    @Override
    public CrudDataScope resolveQueryScope(CrudResourceAction action, SubjectContext subject, QuerySpec<?> spec) {
        return CrudDataScope.allowAll();
    }

    @Override
    public CrudDataScope resolveCommandScope(CrudResourceAction action, SubjectContext subject, CommandSpec<?> spec) {
        return CrudDataScope.allowAll();
    }
}
