package com.example.minicommerce.merchant.service;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.BusinessIdentityDefinition;
import com.entloom.crud.api.access.BusinessIdentityIdType;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationResult;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.authorization.BusinessAuthorizationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductSummaryServiceTest {
    private final BusinessAuthorizationService businessAuthorizationService = mock(BusinessAuthorizationService.class);
    private final MerchantProductService merchantProductService = mock(MerchantProductService.class);
    private final CrudGovernanceAuditRecorder auditRecorder = mock(CrudGovernanceAuditRecorder.class);
    private final ProductSummaryService productSummaryService = new ProductSummaryService(
        businessAuthorizationService, merchantProductService, auditRecorder);
    private final VisitorContext visitor = VisitorContext.authenticated(
        new AccessSubject("merchant-user-a", AccessSubjectType.HUMAN),
        new BusinessIdentityDefinition("merchant", "merchantId", BusinessIdentityIdType.LONG).snapshot("1001"),
        "merchant", "request-1", null);
    private final AuthorizationResult result = mock(AuthorizationResult.class);
    private final ProductSummaryService.Query query = new ProductSummaryService.Query(1, 20, true);

    @BeforeEach
    void authorize() {
        when(businessAuthorizationService.authorize(any())).thenReturn(result);
        when(result.getDecision()).thenReturn(AccessDecision.ALLOW);
        when(result.getFields()).thenReturn(fields(List.of("id", "name", "price"), List.of("active"), List.of("id")));
    }

    @Test
    void 每次授权且空范围不访问数据库() {
        when(result.getScope()).thenReturn(AuthorizationScope.none());
        assertEquals(0, productSummaryService.page(visitor, query).total());
        assertEquals(0, productSummaryService.page(visitor, query).total());
        verify(businessAuthorizationService, times(2)).authorize(any());
        verify(result, times(2)).requireApplicable(any());
        verifyNoInteractions(merchantProductService);
        var events = ArgumentCaptor.forClass(CrudGovernanceAuditEvent.class);
        verify(auditRecorder, times(2)).record(events.capture());
        assertEquals(CrudGovernanceAuditOutcome.SUCCESS, events.getValue().getOutcome());
    }

    @Test
    void 全量和商家范围使用明确分支() {
        when(result.getScope()).thenReturn(AuthorizationScope.all(), AuthorizationScope.equal("merchantId", "1001"));
        var page = new ProductSummaryService.Page(List.of(), 1, 20, 0);
        when(merchantProductService.pageAll(true, 1, 20)).thenReturn(page);
        when(merchantProductService.pageMerchant(1001, true, 1, 20)).thenReturn(page);
        productSummaryService.page(visitor, query);
        productSummaryService.page(visitor, query);
        verify(merchantProductService).pageAll(true, 1, 20);
        verify(merchantProductService).pageMerchant(1001, true, 1, 20);
    }

    @Test
    void 不支持或不匹配的范围及缺失字段均在SQL前拒绝() {
        for (AuthorizationScope scope : List.of(AuthorizationScope.equal("customerId", 301),
                AuthorizationScope.equal("merchantId", 2001), AuthorizationScope.in("merchantId", List.of(1001)))) {
            when(result.getScope()).thenReturn(scope);
            assertThrows(PermissionDeniedException.class, () -> productSummaryService.page(visitor, query));
        }
        when(result.getScope()).thenReturn(AuthorizationScope.all());
        for (AuthorizationFields fields : List.of(fields(List.of("id", "name"), List.of("active"), List.of("id")),
                fields(List.of("id", "name", "price"), List.of(), List.of("id")),
                fields(List.of("id", "name", "price"), List.of("active"), List.of()))) {
            when(result.getFields()).thenReturn(fields);
            assertThrows(PermissionDeniedException.class, () -> productSummaryService.page(visitor, query));
        }
        when(result.getDecision()).thenReturn(AccessDecision.DENY);
        assertThrows(PermissionDeniedException.class, () -> productSummaryService.page(visitor, query));
        verifyNoInteractions(merchantProductService);
        var events = ArgumentCaptor.forClass(CrudGovernanceAuditEvent.class);
        verify(auditRecorder, times(7)).record(events.capture());
        events.getAllValues().forEach(event -> assertEquals(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED, event.getOutcome()));
    }

    private static AuthorizationFields fields(List<String> returned, List<String> filter, List<String> sort) {
        return AuthorizationFields.of(Map.of(AuthorizationFields.Use.RETURN, returned,
            AuthorizationFields.Use.FILTER, filter, AuthorizationFields.Use.SORT, sort));
    }
}
