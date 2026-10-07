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
