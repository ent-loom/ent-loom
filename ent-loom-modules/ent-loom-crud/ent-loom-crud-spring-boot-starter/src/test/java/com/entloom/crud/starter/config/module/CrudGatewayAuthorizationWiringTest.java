package com.entloom.crud.starter.config.module;

import com.entloom.crud.api.access.AuthenticationMode;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.execution.ExecutionPipeline;
import com.entloom.crud.core.governance.adapter.CrudBusinessAuthorizationAdapter;
import com.entloom.crud.core.governance.admission.AccessAdmissionService;
import com.entloom.crud.core.governance.admission.AccessContextScope;
import com.entloom.crud.core.governance.admission.CredentialAuthentication;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.core.governance.service.CrudGovernanceService;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CrudGatewayAuthorizationWiringTest {
    @Test
    @SuppressWarnings("unchecked")
    void 自定义权限服务不能绕过实体操作白名单() {
        var entities = mock(com.entloom.crud.core.runtime.meta.EntityMetaRegistry.class);
        when(entities.getResourceDescriptor(Object.class)).thenReturn(
            new com.entloom.crud.core.runtime.meta.ResourceDescriptor(Object.class, "order", "订单", null));
        var validator = mock(com.entloom.crud.core.runtime.validation.SpecValidator.class);
        when(validator.validateQuerySpec(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var permission = mock(com.entloom.crud.core.governance.permission.CrudPermissionService.class);
        when(permission.decide(any(), any(), any())).thenReturn(com.entloom.crud.api.enums.AccessDecision.ALLOW);
        var declarations = new com.entloom.crud.core.governance.policy.ScenePolicyRegistry(
            Collections.emptyList(), Collections.emptyMap(), java.util.Map.of("order", Collections.emptySet()));
        ObjectProvider<com.entloom.crud.core.governance.scope.CrudDataScopeContributor[]> contributors = mock(ObjectProvider.class);
        var service = new CrudGatewayConfiguration().crudGovernanceService(entities, validator, null,
            permission, null, contributors, null, null,
            (action, spec) -> com.entloom.crud.core.governance.policy.ScenePolicyMatch.skipped("default", null), declarations);
        var subject = new com.entloom.crud.api.model.SubjectContext();
        subject.setSubjectId("tester");
        var spec = QuerySpec.<Object>builder().rootType(Object.class).op(QueryOperation.LIST)
            .resultType(Object.class).subject(subject).build();
        assertThrows(PermissionDeniedException.class, () -> service.governQuery(spec));
        verifyNoInteractions(permission);

        // 未受声明限制的操作仍交由应用权限服务裁决，保留其拒绝语义。
        when(permission.decide(any(), any(), any())).thenReturn(com.entloom.crud.api.enums.AccessDecision.DENY);
        var unrestricted = new CrudGatewayConfiguration().crudGovernanceService(entities, validator, null,
            permission, null, contributors, null, null,
            (action, request) -> com.entloom.crud.core.governance.policy.ScenePolicyMatch.skipped("default", null),
            new com.entloom.crud.core.governance.policy.ScenePolicyRegistry(Collections.emptyList()));
        assertThrows(PermissionDeniedException.class, () -> unrestricted.governQuery(spec));
        verify(permission).decide(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void starter_pipeline_should_call_business_authorization_before_legacy_governance() {
        CrudBusinessAuthorizationAdapter adapter = mock(CrudBusinessAuthorizationAdapter.class);
        ObjectProvider<CrudBusinessAuthorizationAdapter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(adapter);
        CrudGovernanceService legacy = mock(CrudGovernanceService.class);
        ExecutionPipeline pipeline = new CrudGatewayConfiguration().executionPipeline(legacy, provider);
        AccessAdmissionService admission = new AccessAdmissionService(new BusinessIdentityRegistry(Collections.emptyList()));
        QuerySpec<Object> spec = QuerySpec.<Object>builder().rootType(Object.class)
            .op(QueryOperation.LIST).resultType(Object.class).build();

        AccessContextScope.withAdmission(admission.admit(AuthenticationMode.ANONYMOUS,
            CredentialAuthentication::absent, "trusted-entry", null, null), () ->
            assertThrows(PermissionDeniedException.class, () -> pipeline.governQuery(spec)));

        verify(adapter).authorize(any(), any(), isNull());
        verifyNoInteractions(legacy);
    }
}
