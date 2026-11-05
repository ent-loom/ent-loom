package com.entloom.crud.core.governance.execution;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.AuthenticationMode;
import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.NotFoundException;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.admission.AccessAdmissionService;
import com.entloom.crud.core.governance.admission.AccessContextScope;
import com.entloom.crud.core.governance.admission.CredentialAuthentication;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditReasonCode;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationResult;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.authorization.BusinessAuthorizationService;
import com.entloom.crud.core.governance.authorization.BusinessPermissionDecision;
import com.entloom.crud.core.governance.authorization.BusinessPermissionEvaluator;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.core.governance.policy.BusinessOperationCatalog;
import com.entloom.crud.core.governance.policy.BusinessOperationDeclaration;
import com.entloom.crud.core.governance.policy.EntityPolicyBinding;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyTemplate;
import com.entloom.crud.core.governance.policy.PolicyIdentityBranch;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BusinessQueryExecutorTest {
    private static final BusinessOperation VIEW = new BusinessOperation("product", "view");
    private final List<CrudGovernanceAuditEvent> events = new ArrayList<CrudGovernanceAuditEvent>();
    private final VisitorContext visitor = VisitorContext.anonymous("public", "request-1", "trace-1");

    @Test
    void 每次重新授权并在执行完成后记录一次完整审计() {
        AtomicInteger calls = new AtomicInteger();
        BusinessQueryExecutor executor = executor((visitor, operation, rule, target) -> {
            calls.incrementAndGet();
            return allowed();
        });
        for (int i = 0; i < 2; i++) {
            AuthorizationRequest request = request(null);
            String value = executor.execute(request, result -> {
                assertSame(request, result.getRequest());
                result.getFields().requireAllowed(AuthorizationFields.Use.RETURN, "id");
                return result;
            }, result -> {
                assertEquals(calls.get() - 1, events.size());
                return "商品摘要";
            });
            assertEquals("商品摘要", value);
        }
        assertEquals(2, calls.get());
        assertEquals(2, events.size());
        assertNotSame(events.get(0).getAuthorizationResult(), events.get(1).getAuthorizationResult());
        CrudGovernanceAuditEvent event = events.get(0);
        assertEquals(CrudGovernanceAuditOutcome.SUCCESS, event.getOutcome());
        assertEquals(CrudGovernanceAuditReasonCode.NONE, event.getReason());
        assertNull(event.getStage());
        assertSame(visitor, event.getVisitor());
        assertEquals("request-1", event.getRequestId());
        assertEquals("trace-1", event.getTraceId());
        assertEquals("product", event.getAction().getResource());
        assertEquals("view", event.getAction().getAction());
        assertEquals("product.view", event.getRouteKey());
        assertTrue(event.getCostMs() >= 0);
    }

    @Test
    void 操作拒绝时不进入准备或查询() {
        BusinessQueryExecutor executor = executor((visitor, operation, rule, target) -> BusinessPermissionDecision.deny());
        PermissionDeniedException failure = assertThrows(PermissionDeniedException.class,
            () -> executor.execute(request(null), result -> fail("不应准备"), plan -> fail("不应查询")));
        assertEquals(CrudErrorStage.GOVERNANCE, failure.getStage());
        assertEquals("product.view", failure.getRouteKey());
        assertEvent(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED, CrudGovernanceAuditReasonCode.PERMISSION_DENIED,
            CrudErrorStage.GOVERNANCE);
    }

    @Test
    void 目标不可访问保留审计类别并隐藏目标存在性() {
        BusinessQueryExecutor executor = executor((visitor, operation, rule, target) -> BusinessPermissionDecision.targetInaccessible());
        assertThrows(NotFoundException.class,
            () -> executor.execute(request("101"), result -> fail("不应准备"), plan -> fail("不应查询")));
        assertEvent(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED, CrudGovernanceAuditReasonCode.DATA_SCOPE_DENIED,
            CrudErrorStage.GOVERNANCE);
        assertEquals("101", events.get(0).getAuthorizationResult().getRequest().getTargetId());
    }

    @Test
    void 未声明操作在授权结果签发前拒绝且仍保留资源和链路() {
        BusinessQueryExecutor executor = executor((visitor, operation, rule, target) -> fail("不应进入动态授权"));
        AuthorizationRequest request = new AuthorizationRequest(visitor, new BusinessOperation("product", "missing"), "101");
        assertThrows(PermissionDeniedException.class, () -> executor.execute(
            request,
            result -> fail("不应准备"), plan -> fail("不应查询")));
        assertEvent(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED, CrudGovernanceAuditReasonCode.PERMISSION_DENIED,
            CrudErrorStage.GOVERNANCE);
        assertNull(events.get(0).getAuthorizationResult());
        assertSame(request, events.get(0).getAuthorizationRequest());
        assertEquals("101", events.get(0).getAuthorizationRequest().getTargetId());
        assertEquals("missing", events.get(0).getAction().getAction());
        assertEquals("request-1", events.get(0).getRequestId());
    }

    @Test
    void 所有字段用途均由框架校验且缺权限时不执行查询() {
        BusinessQueryExecutor executor = executor((visitor, operation, rule, target) -> allowed());
        for (AuthorizationFields.Use use : AuthorizationFields.Use.values()) {
            assertThrows(PermissionDeniedException.class, () -> executor.execute(request(null), result -> {
                result.getFields().requireAllowed(use, "missing");
                return result;
            }, plan -> fail("未授权字段不能进入查询")));
        }
        assertEquals(AuthorizationFields.Use.values().length, events.size());
        for (CrudGovernanceAuditEvent event : events) {
            assertEquals(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED, event.getOutcome());
        }
    }

    @Test
    void 空准备结果不进入查询且记为治理内部错误() {
        BusinessQueryExecutor executor = executor((visitor, operation, rule, target) -> allowed());
        assertThrows(NullPointerException.class,
            () -> executor.execute(request(null), result -> null, plan -> fail("不应查询")));
        assertEvent(CrudGovernanceAuditOutcome.GOVERNANCE_FAILED, CrudGovernanceAuditReasonCode.INTERNAL_ERROR,
            CrudErrorStage.GOVERNANCE);
    }

    @Test
    void 授权器内部异常不误报为权限拒绝() {
        IllegalStateException failure = new IllegalStateException("策略故障");
        BusinessQueryExecutor executor = executor((visitor, operation, rule, target) -> { throw failure; });
        AuthorizationRequest request = request("101");
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> executor.execute(request, result -> fail("不应准备"), plan -> fail("不应查询"))));
        assertEvent(CrudGovernanceAuditOutcome.GOVERNANCE_FAILED, CrudGovernanceAuditReasonCode.INTERNAL_ERROR,
            CrudErrorStage.GOVERNANCE);
        assertEquals(AccessDecision.DENY, events.get(0).getAccessDecision());
        assertNull(events.get(0).getAuthorizationResult());
        assertSame(request, events.get(0).getAuthorizationRequest());
    }

    @Test
    void 授权与准备阶段的框架服务故障均记为治理失败() {
        for (CrudErrorCode code : new CrudErrorCode[] {CrudErrorCode.FILE_SERVICE_UNAVAILABLE,
                CrudErrorCode.ATTRIBUTE_CONTRIBUTION_FAILED, CrudErrorCode.INTERNAL_ERROR}) {
            CrudException failure = new CrudException(code, "框架服务故障");
            BusinessQueryExecutor authorizationFailure = executor((visitor, operation, rule, target) -> { throw failure; });
            assertSame(failure, assertThrows(CrudException.class, () -> authorizationFailure.execute(
                request("101"), result -> fail("不应准备"), plan -> fail("不应查询"))));
            assertEvent(CrudGovernanceAuditOutcome.GOVERNANCE_FAILED,
                CrudGovernanceAuditReasonCode.fromCrudErrorCode(code), CrudErrorStage.GOVERNANCE);
            events.clear();

            BusinessQueryExecutor preparationFailure = executor((visitor, operation, rule, target) -> allowed());
            assertSame(failure, assertThrows(CrudException.class, () -> preparationFailure.execute(
                request("101"), result -> { throw failure; }, plan -> fail("不应查询"))));
            assertEvent(CrudGovernanceAuditOutcome.GOVERNANCE_FAILED,
                CrudGovernanceAuditReasonCode.fromCrudErrorCode(code), CrudErrorStage.GOVERNANCE);
            events.clear();
        }
    }

    @Test
    void 准备阶段输入约束失败仍记为治理拒绝() {
        BusinessQueryExecutor executor = executor((visitor, operation, rule, target) -> allowed());
        ValidationException failure = new ValidationException("查询条件非法");
        assertSame(failure, assertThrows(ValidationException.class, () -> executor.execute(
            request(null), result -> { throw failure; }, plan -> fail("不应查询"))));
        assertEvent(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED, CrudGovernanceAuditReasonCode.VALIDATION_ERROR,
            CrudErrorStage.GOVERNANCE);
    }

    @Test
    void 准备阶段程序异常不误报为权限拒绝() {
        BusinessQueryExecutor executor = executor((visitor, operation, rule, target) -> allowed());
        IllegalStateException failure = new IllegalStateException("范围转换故障");
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> executor.execute(request(null), result -> { throw failure; }, plan -> fail("不应查询"))));
        assertEvent(CrudGovernanceAuditOutcome.GOVERNANCE_FAILED, CrudGovernanceAuditReasonCode.INTERNAL_ERROR,
            CrudErrorStage.GOVERNANCE);
        assertNotNull(events.get(0).getAuthorizationResult());
    }

    @Test
    void 查询异常保留原异常并记录执行失败() {
        BusinessQueryExecutor executor = executor((visitor, operation, rule, target) -> allowed());
        IllegalStateException failure = new IllegalStateException("SQL 故障");
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> executor.execute(request(null), result -> result, plan -> { throw failure; })));
        assertEvent(CrudGovernanceAuditOutcome.EXECUTION_FAILED, CrudGovernanceAuditReasonCode.EXECUTION_FAILED,
            CrudErrorStage.EXECUTE);
    }

    @Test
    void 查询框架异常保留错误码并补充阶段() {
        BusinessQueryExecutor executor = executor((visitor, operation, rule, target) -> allowed());
        ValidationException failure = new ValidationException("查询结果非法");
        assertSame(failure, assertThrows(ValidationException.class,
            () -> executor.execute(request(null), result -> result, plan -> { throw failure; })));
        assertEquals(CrudErrorStage.EXECUTE, failure.getStage());
        assertEvent(CrudGovernanceAuditOutcome.EXECUTION_FAILED, CrudGovernanceAuditReasonCode.VALIDATION_ERROR,
            CrudErrorStage.EXECUTE);
    }

    @Test
    void 审计故障不改变成功结果且不重复记录() {
        AtomicInteger calls = new AtomicInteger();
        BusinessQueryExecutor executor = new BusinessQueryExecutor(service((visitor, operation, rule, target) -> allowed()),
            event -> { calls.incrementAndGet(); throw new IllegalStateException("审计故障"); });
        assertNull(executor.execute(request(null), result -> result, plan -> null));
        assertEquals(1, calls.get());
    }

    @Test
    void 审计故障不覆盖原始Error且保留为附加异常() {
        IllegalStateException auditFailure = new IllegalStateException("审计故障");
        BusinessQueryExecutor executor = new BusinessQueryExecutor(service((visitor, operation, rule, target) -> allowed()),
            event -> { throw auditFailure; });
        AssertionError failure = new AssertionError("查询失败");
        assertSame(failure, assertThrows(AssertionError.class,
            () -> executor.execute(request(null), result -> result, plan -> { throw failure; })));
        assertArrayEquals(new Throwable[] {auditFailure}, failure.getSuppressed());
    }

    @Test
    void 公共授权不携带主体但审计保留同次准入的实际认证主体() {
        AccessSubject actor = new AccessSubject("actor-1", AccessSubjectType.HUMAN);
        var admission = new AccessAdmissionService(new BusinessIdentityRegistry(Collections.emptyList()))
            .admit(AuthenticationMode.ANONYMOUS, () -> CredentialAuthentication.authenticated(actor),
                "public", "request-public", "trace-public");
        BusinessQueryExecutor executor = executor((visitor, operation, rule, target) -> {
            assertTrue(visitor.isAnonymous());
            assertFalse(visitor.getSubject().isPresent());
            return allowed();
        });
        AccessContextScope.withAdmission(admission, () -> {
            executor.execute(new AuthorizationRequest(admission.getVisitorContext(), VIEW, null), result -> result, plan -> "摘要");
            executor.execute(request(null), result -> result, plan -> "另一次查询");
        });
        assertEquals("actor-1", events.get(0).getSubject().getSubjectId());
        assertFalse(events.get(0).getVisitor().getSubject().isPresent());
        assertNull(events.get(1).getSubject());
        assertFalse(AccessContextScope.currentAdmission().isPresent());
    }

    private void assertEvent(CrudGovernanceAuditOutcome outcome, CrudGovernanceAuditReasonCode reason, CrudErrorStage stage) {
        assertEquals(1, events.size());
        assertEquals(outcome, events.get(0).getOutcome());
        assertEquals(reason, events.get(0).getReason());
        assertEquals(stage, events.get(0).getStage());
    }

    private AuthorizationRequest request(String target) {
        return new AuthorizationRequest(visitor, VIEW, target);
    }

    private BusinessQueryExecutor executor(BusinessPermissionEvaluator evaluator) {
        return new BusinessQueryExecutor(service(evaluator), events::add);
    }

    private static BusinessPermissionDecision allowed() {
        return BusinessPermissionDecision.allow(AuthorizationScope.all(), AuthorizationFields.of(
            Collections.singletonMap(AuthorizationFields.Use.RETURN, Collections.singletonList("id"))));
    }

    private static BusinessAuthorizationService service(BusinessPermissionEvaluator evaluator) {
        ResourceDescriptor resource = new ResourceDescriptor(Product.class, "product", null, Collections.emptyList());
        EntityMeta meta = new EntityMeta(Product.class, resource, "product", "id", null,
            Collections.singletonMap("id", new EntityFieldMeta("id", String.class, "id", false, false, true, true)));
        BusinessOperationCatalog operations = new BusinessOperationCatalog(Collections.singletonList(resource),
            Collections.singletonList(new BusinessOperationDeclaration(VIEW, "测试商品查询")));
        EntityPolicyTemplate policy = new EntityPolicyTemplate("product", "测试公共策略", Collections.singletonList(
            new EntityPolicyTemplate.Rule("view", PolicyIdentityBranch.publicBranch(), Collections.emptyList())));
        EntityPolicyRegistry policies = new EntityPolicyRegistry(Collections.singletonList(meta), Collections.singletonList(policy),
            Collections.singletonList(new EntityPolicyBinding(Product.class, "product", "测试绑定", Collections.emptyMap())),
            operations, new BusinessIdentityRegistry(Collections.emptyList()));
        return new BusinessAuthorizationService(operations, policies, evaluator);
    }

    private static class Product { }
}
