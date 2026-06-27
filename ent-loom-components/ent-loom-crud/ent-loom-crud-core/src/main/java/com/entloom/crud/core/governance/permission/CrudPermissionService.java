package com.entloom.crud.core.governance.permission;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.runtime.spec.BaseSpec;

/**
 * 访问权限服务。
 */
public interface CrudPermissionService {
    /**
     * 解析访问判定结果。
     */
    AccessDecision decide(CrudResourceAction action, SubjectContext subject, BaseSpec spec);
}
