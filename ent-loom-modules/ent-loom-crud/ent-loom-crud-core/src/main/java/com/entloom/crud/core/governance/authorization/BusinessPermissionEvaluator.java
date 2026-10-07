package com.entloom.crud.core.governance.authorization;

import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;

/** 项目按次判定动态角色和业务资格；入口及静态声明本身不授予权限。 */
@FunctionalInterface
public interface BusinessPermissionEvaluator {
    BusinessPermissionDecision evaluate(VisitorContext visitor, BusinessOperation operation,
                                        EntityPolicyRegistry.EffectiveRule rule, String targetId);
}
