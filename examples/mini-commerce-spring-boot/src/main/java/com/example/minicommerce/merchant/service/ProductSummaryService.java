package com.example.minicommerce.merchant.service;

import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditReasonCode;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationResult;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.authorization.BusinessAuthorizationService;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 原始业务查询自行消费本次授权范围与字段，不能降级为全量。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSummaryService {
    private static final BusinessOperation VIEW = new BusinessOperation("merchantProduct", "view");
    private final BusinessAuthorizationService authorization;
    private final ProductSummaryMapper mapper;
    private final CrudGovernanceAuditRecorder audit;

    public Page page(VisitorContext visitor, Query query) {
        long started = System.nanoTime();
        AuthorizationResult result = null;
        CrudGovernanceAuditOutcome outcome = CrudGovernanceAuditOutcome.GOVERNANCE_DENIED;
        try {
            var request = new AuthorizationRequest(visitor, VIEW, null);
            result = authorization.authorize(request);
            if (result.getDecision() != AccessDecision.ALLOW) throw new PermissionDeniedException("商品查询未授权");
            result.requireApplicable(request);
            for (String field : List.of("id", "name", "price")) requireField(result, AuthorizationFields.Use.RETURN, field);
            requireField(result, AuthorizationFields.Use.SORT, "id");
            if (query.active() != null) requireField(result, AuthorizationFields.Use.FILTER, "active");
            AuthorizationScope scope = result.getScope();
            Long merchantId = null;
            if (scope.getKind() == AuthorizationScope.Kind.EQUAL && "merchantId".equals(scope.getField())
                && scope.getValues().size() == 1 && visitor.findIdentity("merchant").isPresent()
                && visitor.requireIdentity("merchant").getIdentityId().equals(scope.getValues().get(0).toString())) {
                merchantId = Long.valueOf(visitor.requireIdentity("merchant").getIdentityId());
            } else if (scope.getKind() != AuthorizationScope.Kind.ALL && scope.getKind() != AuthorizationScope.Kind.NONE) {
                throw new PermissionDeniedException("商品摘要不支持当前授权范围");
            }
            outcome = CrudGovernanceAuditOutcome.EXECUTION_FAILED;
            Page page = scope.getKind() == AuthorizationScope.Kind.NONE ? new Page(List.of(), query.page(), query.limit(), 0)
                : merchantId == null ? mapper.pageAll(query.active(), query.page(), query.limit())
                : mapper.pageMerchant(merchantId, query.active(), query.page(), query.limit());
            outcome = CrudGovernanceAuditOutcome.SUCCESS;
            return page;
        } finally {
            try {
                audit.record(CrudGovernanceAuditEvent.forBusinessQuery(visitor, visitor.getSubject().orElse(null), result,
                    new CrudResourceAction("merchantProduct", "view", null), outcome,
                    outcome == CrudGovernanceAuditOutcome.SUCCESS ? CrudGovernanceAuditReasonCode.NONE
                        : outcome == CrudGovernanceAuditOutcome.EXECUTION_FAILED ? CrudGovernanceAuditReasonCode.EXECUTION_FAILED
                        : CrudGovernanceAuditReasonCode.PERMISSION_DENIED,
                    (System.nanoTime() - started) / 1_000_000L,
                    outcome == CrudGovernanceAuditOutcome.SUCCESS ? null : outcome == CrudGovernanceAuditOutcome.EXECUTION_FAILED
                        ? CrudErrorStage.EXECUTE : CrudErrorStage.GOVERNANCE, "merchantProduct.mvcPage"));
            } catch (RuntimeException auditFailure) {
                log.error("商品摘要审计失败", auditFailure);
            }
        }
    }

    private static void requireField(AuthorizationResult result, AuthorizationFields.Use use, String field) {
        if (!result.getFields().allows(use, field)) throw new PermissionDeniedException("商品摘要字段未授权: " + field);
    }

    public record Query(@Min(1) Integer page, @Min(1) @Max(100) Integer limit, Boolean active) {
        public Query {
            page = page == null ? 1 : page;
            limit = limit == null ? 20 : limit;
        }

        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("不支持的请求字段: " + name);
        }
    }

    public record Summary(Long id, String name, BigDecimal price) { }

    public record Page(List<Summary> items, int page, int limit, long total) { }
}
