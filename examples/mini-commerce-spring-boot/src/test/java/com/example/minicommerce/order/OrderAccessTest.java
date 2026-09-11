package com.example.minicommerce.order;

import com.example.minicommerce.order.service.OrderQueryService;
import com.example.minicommerce.order.service.PlaceOrderService;
import com.example.minicommerce.order.repository.CustomerRepository;
import com.example.minicommerce.order.repository.OrderRepository;
import com.example.minicommerce.order.repository.ProductRepository;
import com.example.minicommerce.order.security.OrderAccessPolicy;
import com.example.minicommerce.order.security.OrderAction;
import com.example.minicommerce.order.controller.OrderController;
import com.example.minicommerce.order.controller.OrderExceptionHandler;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.permission.CrudPermissionRule;
import com.entloom.crud.core.governance.permission.RuleBasedCrudPermissionService;
import com.entloom.crud.core.governance.subject.FailClosedCrudSubjectResolver;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 业务入口必须在读取或写入数据前完成授权，不能依赖 CRUD Controller 的检查。 */
class OrderAccessTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);

    @Test
    void missingRulesRejectBothHttpEntrypointsBeforePersistence() throws Exception {
        assertDenied(policy("local-developer", List.of()));
    }

    @Test
    void explicitDenyRejectsBothHttpEntrypointsBeforePersistence() throws Exception {
        assertDenied(policy("local-developer", List.of(rule("*", AccessDecision.DENY))));
    }

    @Test
    void unknownSubjectCannotUseDeveloperRules() throws Exception {
        assertDenied(policy("other-user", List.of(rule("*", AccessDecision.ALLOW))));
    }

    @Test
    void missingSubjectFailsClosed() {
        OrderAccessPolicy policy = policy(null, List.of(rule("*", AccessDecision.ALLOW)));
        assertThrows(PermissionDeniedException.class, () -> policy.require(OrderAction.PLACE));
    }

    @Test
    void defaultSubjectResolverReturnsForbiddenInsteadOfServerError() throws Exception {
        assertDenied(new OrderAccessPolicy(new FailClosedCrudSubjectResolver(),
            new RuleBasedCrudPermissionService(List.of(rule("*", AccessDecision.ALLOW)))));
    }

    @Test
    void permissionIsSpecificToBusinessAction() {
        OrderAccessPolicy policy = policy("local-developer", List.of(rule("PLACE", AccessDecision.ALLOW)));
        assertDoesNotThrow(() -> policy.require(OrderAction.PLACE));
        assertThrows(PermissionDeniedException.class, () -> policy.require(OrderAction.DETAIL));
    }

    private void assertDenied(OrderAccessPolicy policy) throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new OrderController(
                new PlaceOrderService(products, customers, orders, policy),
                new OrderQueryService(orders, policy)))
            .setControllerAdvice(new OrderExceptionHandler()).build();
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":2001,\"items\":[{\"productId\":1001,\"quantity\":1}]}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));
        mvc.perform(get("/orders/1"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));
        verifyNoInteractions(products, customers, orders);
    }

    private OrderAccessPolicy policy(String subjectId, List<CrudPermissionRule> rules) {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId(subjectId);
        return new OrderAccessPolicy(() -> subject, new RuleBasedCrudPermissionService(rules));
    }

    private CrudPermissionRule rule(String action, AccessDecision decision) {
        return new CrudPermissionRule("order", action, "default", decision,
            Set.of("local-developer"), Set.of(), Set.of());
    }
}
