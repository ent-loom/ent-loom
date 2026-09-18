package com.example.minicommerce.configuration;

import com.example.minicommerce.order.controller.OrderController;
import com.example.minicommerce.order.controller.OrderExceptionHandler;
import com.example.minicommerce.customer.entity.Customer;
import com.example.minicommerce.product.entity.Product;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.order.entity.OrderItem;
import com.example.minicommerce.order.enums.OrderStatus;
import com.example.minicommerce.customer.dao.CustomerDao;
import com.example.minicommerce.product.dao.ProductDao;
import com.example.minicommerce.order.repository.OrderRepository;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        CustomerDao customers = mock(CustomerDao.class);
        ProductDao products = mock(ProductDao.class);
        OrderRepository orders = mock(OrderRepository.class);
        Customer customer = new Customer();
        customer.setId(2001L);
        customer.setDisplayName("Ada Lovelace");
        Product product = new Product();
        product.setId(1001L);
        product.setName("Entity Book");
        product.setPrice(new BigDecimal("19.90"));
        product.setActive(true);
        when(customers.findById(2001L)).thenReturn(Optional.of(customer));
        when(products.findForOrder(1001L)).thenReturn(Optional.of(product));
        when(orders.insert(org.mockito.ArgumentMatchers.any(Order.class),
            org.mockito.ArgumentMatchers.anyList())).thenReturn(3001L);

        AtomicReference<HttpServletRequest> currentRequest = new AtomicReference<>();
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        when(requestProvider.getIfAvailable()).thenAnswer(invocation -> currentRequest.get());
        OrderAccessPolicy accessPolicy = new OrderAccessPolicy(
            new ServletPrincipalCrudSubjectResolver(requestProvider),
            new RuleBasedCrudPermissionService(List.of(permission("alice", "PLACE")))
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(
                new PlaceOrderService(customers, products, orders, accessPolicy),
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

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orders).insert(orderCaptor.capture(), itemsCaptor.capture());
        Order order = orderCaptor.getValue();
        assertEquals(2001L, order.getCustomerId());
        assertEquals(OrderStatus.CREATED, order.getStatus());
        assertEquals(new BigDecimal("39.80"), order.getTotalAmount());
        assertNotNull(order.getCreatedAt());
        assertEquals(1, itemsCaptor.getValue().size());
        OrderItem item = itemsCaptor.getValue().getFirst();
        assertEquals(1001L, item.getProductId());
        assertEquals("Entity Book", item.getProductName());
        assertEquals(new BigDecimal("19.90"), item.getUnitPrice());
        assertEquals(2, item.getQuantity());
        assertEquals(new BigDecimal("39.80"), item.getLineAmount());
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
