package com.entloom.crud.core.governance.permission;

import com.entloom.crud.annotations.CrudAccessOperation;
import com.entloom.crud.annotations.EntCrudActions;
import com.entloom.crud.annotations.EntCrudOperations;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AnnotationCrudPermissionServiceTest {
    private final CrudPermissionService service = new AnnotationCrudPermissionService(
        (action, subject, spec) -> AccessDecision.ALLOW
    );

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

    @EntCrudOperations(CrudAccessOperation.DETAIL)
    @EntCrudActions("place")
    private static final class RestrictedOrder {
    }

    @EntCrudActions("place")
    private static final class ActionOnlyOrder {
    }

    @EntCrudOperations(CrudAccessOperation.DETAIL)
    private static final class OperationOnlyOrder {
    }

    @EntCrudOperations
    @EntCrudActions
    private static final class ClosedOrder {
    }
}
