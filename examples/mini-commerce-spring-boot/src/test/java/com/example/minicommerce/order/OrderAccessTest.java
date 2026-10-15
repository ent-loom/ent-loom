package com.example.minicommerce.order;

import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.authorization.BusinessPermissionDecision;
import com.entloom.crud.core.governance.authorization.BusinessPermissionEvaluator;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 验证身份准入、业务授权和创建范围均生效，通用写入不能绕过下单流程。 */
@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:commerce-access;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
class OrderAccessTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean BusinessPermissionEvaluator evaluator;

    @BeforeEach
    void prepare() {
        jdbc.update("delete from order_item");
        jdbc.update("delete from `order`");
        jdbc.update("delete from merchant_product");
        jdbc.update("delete from customer");
        jdbc.update("insert into customer values (301, '测试客户', 'test@example.com')");
        jdbc.update("insert into merchant_product(id, merchant_id, name, price, active, version) values "
            + "(10, 1001, '商家一商品', 10, true, 0), (20, 2001, '商家二商品', 20, true, 0)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "unknown", "demo-merchant-a"})
    void 下单拒绝缺失无效及商家身份(String token) throws Exception {
        mvc.perform(post("/api/consumer/orders/place").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(command()))
            .andExpect(status().isForbidden());
        verifyNoInteractions(evaluator);
        assertEmpty();
    }

    @Test
    void 通过客户准入仍须获得独立下单业务授权() throws Exception {
        when(evaluator.evaluate(any(), any(), any(), any())).thenReturn(BusinessPermissionDecision.deny());
        mvc.perform(post("/api/consumer/orders/place").header("Authorization", "Bearer demo-customer-a")
                .contentType(MediaType.APPLICATION_JSON).content(command()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        verify(evaluator).evaluate(any(), eq(new com.entloom.crud.api.access.BusinessOperation("order", "place")), any(), isNull());
        assertEmpty();
    }

    @Test
    void 动态范围限制后一个商家时整次下单回滚() throws Exception {
        when(evaluator.evaluate(any(), any(), any(), any())).thenReturn(BusinessPermissionDecision.allow(
            AuthorizationScope.equal("merchantId", 1001L), AuthorizationFields.of(Map.of(AuthorizationFields.Use.RETURN, java.util.List.of("id")))));
        mvc.perform(post("/api/consumer/orders/place").header("Authorization", "Bearer demo-customer-a")
                .contentType(MediaType.APPLICATION_JSON).content(command()))
            .andExpect(status().isForbidden());
        verify(evaluator, times(3)).evaluate(any(), any(), any(), any());
        assertEmpty();
    }

    @Test
    void 客户不能通过请求体指定订单归属() throws Exception {
        for (String field : new String[]{"customerId", "merchantId"}) {
            mvc.perform(post("/api/consumer/orders/place").header("Authorization", "Bearer demo-customer-a")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"" + field + "\":302,\"items\":[{\"merchantProductId\":10,\"quantity\":1}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update", "delete", "action/place", "detail", "page"})
    void 通用订单接口关闭以避免绕过业务入口(String operation) throws Exception {
        mvc.perform(post("/api/ent-crud/order/" + operation).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ENTITY_NOT_EXPOSED"));
        assertEmpty();
    }

    private void assertEmpty() {
        assertEquals(0, jdbc.queryForObject("select count(*) from `order`", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from order_item", Integer.class));
    }

    private String command() {
        return "{\"items\":[{\"merchantProductId\":10,\"quantity\":1},{\"merchantProductId\":20,\"quantity\":1}]}";
    }
}
