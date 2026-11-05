package com.entloom.crud.core.governance.permission;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CrudOperationDomain;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.governance.policy.ScenePolicyRegistry;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import java.util.Objects;

/** 在应用权限判断前执行已编译的实体能力边界；不读取注解或配置。 */
public final class DeclaredCrudPermissionService implements CrudPermissionService {
    private final CrudPermissionService delegate;
    private final ScenePolicyRegistry declarations;

    public DeclaredCrudPermissionService(CrudPermissionService delegate, ScenePolicyRegistry declarations) {
        this.delegate = Objects.requireNonNull(delegate, "权限委托不能为空");
        this.declarations = Objects.requireNonNull(declarations, "实体声明不能为空");
    }

    @Override
    public AccessDecision decide(CrudResourceAction action, SubjectContext subject, BaseSpec spec) {
        if (action != null && action.getResourceDescriptor() != null) {
            boolean allowed = action.getOperationDomain() == CrudOperationDomain.COMMAND && "ACTION".equals(action.getOperation())
                ? declarations.allowsAction(action.getResource(), action.getScene())
                : declarations.allowsStandard(action.getResource(), CrudOperationKey.of(action.getOperationDomain(), action.getOperation()));
            if (!allowed) return AccessDecision.DENY;
        }
        return delegate.decide(action, subject, spec);
    }
}
