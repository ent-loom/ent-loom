package com.entloom.crud.core.governance.policy;

import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.runtime.spec.BaseSpec;

/** Scene Policy 准入服务。 */
public interface ScenePolicyService {
    ScenePolicyMatch match(CrudResourceAction action, BaseSpec spec);
}
