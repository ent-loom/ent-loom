package com.entloom.crud.core.governance.scope;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.runtime.spec.BaseSpec;

/**
 * 业务侧范围收窄贡献器。
 */
public interface CrudDataScopeContributor {
    boolean supports(CrudResourceAction action, BaseSpec spec);

    CrudDataScope contribute(CrudResourceAction action, SubjectContext subject, BaseSpec spec, CrudDataScope grantedScope);
}
