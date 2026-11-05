package com.entloom.crud.core.governance.permission;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DeclaredCrudPermissionServiceTest {
    private final CrudPermissionService service = (action, subject, spec) -> {
        Class<?> type = action.getResourceDescriptor().getEntityType();
        java.util.Map<String, java.util.Set<String>> actions = new java.util.HashMap<>();
        java.util.Map<String, java.util.Set<CrudOperationKey>> operations = new java.util.HashMap<>();
        if (type == RestrictedOrder.class || type == ActionOnlyOrder.class)
            actions.put("order", java.util.Collections.singleton("place"));
        if (type == RestrictedOrder.class || type == OperationOnlyOrder.class)
            operations.put("order", java.util.Collections.singleton(CrudOperationKey.of(QueryOperation.DETAIL)));
        if (type == ClosedOrder.class) {
            actions.put("order", java.util.Collections.emptySet());
            operations.put("order", java.util.Collections.emptySet());
        }
        return new DeclaredCrudPermissionService((a, u, q) -> AccessDecision.ALLOW,
            new com.entloom.crud.core.governance.policy.ScenePolicyRegistry(java.util.Collections.emptyList(), actions, operations))
            .decide(action, subject, spec);
    };

    @Test
    void should_inherit_undeclared_access_dimension() {
        Assertions.assertEquals(AccessDecision.ALLOW, service.decide(
            action(ActionOnlyOrder.class, QueryOperation.PAGE, null), subject(), null));
        Assertions.assertEquals(AccessDecision.ALLOW, service.decide(
            action(OperationOnlyOrder.class, CommandOperation.ACTION, "cancel"), subject(), null));
    }

    @Test
    void should_limit_standard_operations_to_entity_declaration() {
        Assertions.assertEquals(AccessDecision.ALLOW, service.decide(
            action(RestrictedOrder.class, QueryOperation.DETAIL, "detail"), subject(), null));
        Assertions.assertEquals(AccessDecision.DENY, service.decide(
            action(RestrictedOrder.class, QueryOperation.PAGE, null), subject(), null));
    }

    @Test
    void should_limit_business_actions_to_entity_declaration() {
        Assertions.assertEquals(AccessDecision.ALLOW, service.decide(
            action(RestrictedOrder.class, CommandOperation.ACTION, "place"), subject(), null));
        Assertions.assertEquals(AccessDecision.DENY, service.decide(
            action(RestrictedOrder.class, CommandOperation.ACTION, "cancel"), subject(), null));
    }

    @Test
    void should_deny_dimension_declared_with_empty_whitelist() {
        Assertions.assertEquals(AccessDecision.DENY, service.decide(
            action(ClosedOrder.class, QueryOperation.DETAIL, null), subject(), null));
        Assertions.assertEquals(AccessDecision.DENY, service.decide(
            action(ClosedOrder.class, CommandOperation.ACTION, "place"), subject(), null));
    }

    private CrudResourceAction action(Class<?> entityType,
                                      com.entloom.crud.api.enums.CrudScopedOperation operation,
                                      String scene) {
        return new CrudResourceAction(
            new ResourceDescriptor(entityType, "order", "test", null),
            CrudOperationKey.of(operation), scene, null);
    }

    private SubjectContext subject() {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("developer");
        return subject;
    }



    private static final class RestrictedOrder {
    }


    private static final class ActionOnlyOrder {
    }


    private static final class OperationOnlyOrder {
    }



    private static final class ClosedOrder {
    }
}
