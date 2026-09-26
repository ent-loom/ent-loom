package com.entloom.crud.core.governance.permission;

import com.entloom.crud.annotations.CrudAccessOperation;
import com.entloom.crud.annotations.EntCrudOperations;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CrudOperationDomain;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.governance.policy.EntityActionDeclarations;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import java.util.Arrays;

/**
 * 在项目权限判断前执行实体声明的能力边界。
 */
public class AnnotationCrudPermissionService implements CrudPermissionService {
    private final CrudPermissionService delegate;

    public AnnotationCrudPermissionService(CrudPermissionService delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate 不能为空");
        }
        this.delegate = delegate;
    }

    @Override
    public AccessDecision decide(CrudResourceAction action, SubjectContext subject, BaseSpec spec) {
        if (!withinEntityBoundary(action)) {
            return AccessDecision.DENY;
        }
        return delegate.decide(action, subject, spec);
    }

    private boolean withinEntityBoundary(CrudResourceAction action) {
        if (action == null || action.getResourceDescriptor() == null
            || action.getResourceDescriptor().getEntityType() == null) {
            return true;
        }
        if (action.getOperationDomain() == CrudOperationDomain.COMMAND
            && "ACTION".equals(action.getOperation())) {
            return EntityActionDeclarations.allows(action.getResourceDescriptor().getEntityType(), action.getScene());
        }
        EntCrudOperations operations = action.getResourceDescriptor().getEntityType()
            .getAnnotation(EntCrudOperations.class);
        if (operations == null) {
            return true;
        }
        CrudAccessOperation operation = resolveStandardOperation(action);
        return operation != null && containsStandard(operations.value(), operation);
    }

    private CrudAccessOperation resolveStandardOperation(CrudResourceAction action) {
        if (action.getOperationDomain() == CrudOperationDomain.QUERY
            || action.getOperationDomain() == CrudOperationDomain.COMMAND) {
            try {
                return CrudAccessOperation.valueOf(action.getOperation());
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        return null;
    }

    private boolean containsStandard(CrudAccessOperation[] operations, CrudAccessOperation expected) {
        return Arrays.asList(operations).contains(expected);
    }
}
