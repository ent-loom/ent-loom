package com.entloom.crud.core.governance.permission;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.runtime.spec.BaseSpec;

/**
 * 默认放行权限实现。
 */
public class AllowAllCrudPermissionService implements CrudPermissionService {
    @Override
    public AccessDecision decide(CrudResourceAction action, SubjectContext subject, BaseSpec spec) {
        return AccessDecision.ALLOW;
    }
}
