package com.entloom.crud.core.governance.authorization;

import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;

/** 按实体绑定的可选动态授权扩展；只能拒绝或进一步收窄静态授权，不能增加操作资格。 */
public interface EntityGrantCustomizer {
    Class<?> getEntityType();

    BusinessPermissionDecision customize(VisitorContext visitor, BusinessOperation operation, String targetId,
                                         BusinessPermissionDecision declared);
}
