package com.example.minicommerce.configuration;

import com.example.minicommerce.order.controller.OrderController;
import com.example.minicommerce.order.controller.OrderExceptionHandler;
import com.example.minicommerce.order.dto.OrderCustomerInfo;
import com.example.minicommerce.order.dto.OrderProductInfo;
import com.example.minicommerce.order.repository.CustomerRepository;
import com.example.minicommerce.order.repository.OrderRepository;
import com.example.minicommerce.order.repository.ProductRepository;
import com.example.minicommerce.order.security.OrderAccessPolicy;
import com.example.minicommerce.order.service.OrderQueryService;
import com.example.minicommerce.order.service.PlaceOrderService;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.core.governance.permission.CrudPermissionRule;
import com.entloom.crud.core.governance.permission.RuleBasedCrudPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证容器 Principal 能穿过主体适配器和订单权限链进入业务 Controller。 */
class ProductionPrincipalAuthorizationTest {
    @Test
    void authenticatedPrincipalCanPlaceOrderThroughBusinessController() throws Exception {
        CustomerRepository customers = mock(CustomerRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        OrderRepository orders = mock(OrderRepository.class);
        when(customers.findById(2001L)).thenReturn(Optional.of(new OrderCustomerInfo(2001L, "Ada Lovelace")));
        when(products.findById(1001L)).thenReturn(Optional.of(new OrderProductInfo(
            1001L, "Entity Book", new BigDecimal("19.90"), true)));
        when(orders.insertOrder(eq(2001L), eq(com.example.minicommerce.order.enums.OrderStatus.CREATED),
            eq(new BigDecimal("39.80")), org.mockito.ArgumentMatchers.any())).thenReturn(3001L);

        AtomicReference<HttpServletRequest> currentRequest = new AtomicReference<>();
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        when(requestProvider.getIfAvailable()).thenAnswer(invocation -> currentRequest.get());
        OrderAccessPolicy accessPolicy = new OrderAccessPolicy(
            new ServletPrincipalCrudSubjectResolver(requestProvider),
            new RuleBasedCrudPermissionService(List.of(permission("alice", "PLACE")))
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(
                new PlaceOrderService(products, customers, orders, accessPolicy),
                new OrderQueryService(orders, accessPolicy)))
            .setControllerAdvice(new OrderExceptionHandler())
            .addInterceptors(requestContextInterceptor(currentRequest))
            .build();

        mockMvc.perform(post("/orders")
                .principal(() -> "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":2001,\"items\":[{\"productId\":1001,\"quantity\":2}]}")
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderId").value(3001))
            .andExpect(jsonPath("$.totalAmount").value(39.80));

        verify(orders).insertOrder(eq(2001L), eq(com.example.minicommerce.order.enums.OrderStatus.CREATED),
            eq(new BigDecimal("39.80")), org.mockito.ArgumentMatchers.any());
        verify(orders).insertItems(eq(3001L), org.mockito.ArgumentMatchers.anyList());
    }

    private CrudPermissionRule permission(String subjectId, String action) {
        return new CrudPermissionRule("order", action, "default", AccessDecision.ALLOW,
            Set.of(subjectId), Set.of(), Set.of());
    }

    private HandlerInterceptor requestContextInterceptor(AtomicReference<HttpServletRequest> currentRequest) {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(
                jakarta.servlet.http.HttpServletRequest request,
                jakarta.servlet.http.HttpServletResponse response,
                Object handler
            ) {
                currentRequest.set(request);
                return true;
            }

            @Override
            public void postHandle(
                jakarta.servlet.http.HttpServletRequest request,
                jakarta.servlet.http.HttpServletResponse response,
                Object handler,
                ModelAndView modelAndView
            ) {
                currentRequest.set(request);
            }

            @Override
            public void afterCompletion(
                jakarta.servlet.http.HttpServletRequest request,
                jakarta.servlet.http.HttpServletResponse response,
                Object handler,
                Exception exception
            ) {
                currentRequest.set(null);
            }
        };
    }
}
