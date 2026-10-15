package com.example.minicommerce.common.web;

import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.starter.config.CrudAccessProperties;
import com.entloom.crud.starter.web.registry.ExposedEntityRegistry;
import com.example.minicommerce.customer.entity.Customer;
import com.example.minicommerce.customer.entity.CustomerAddress;
import com.example.minicommerce.merchant.entity.MerchantArticle;
import com.example.minicommerce.merchant.entity.MerchantProduct;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.order.entity.OrderItem;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证实体扫描、默认 HTTP 暴露及 Meta 文档契约。 */
@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:commerce-contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
class EntityContractIntegrationTest {
    @Autowired ExposedEntityRegistry exposedEntities;
    @Autowired EntityMetaRegistry entityMetaRegistry;
    @Autowired CrudAccessProperties accessProperties;
    @Autowired MockMvc mvc;

    @Test
    void 实体只从业务包注册且Http默认开放并遵循实体禁用配置() {
        assertEquals(Set.of(Customer.class, CustomerAddress.class, Order.class, OrderItem.class,
                MerchantProduct.class, MerchantArticle.class),
            entityMetaRegistry.getEntityMetas().stream().map(EntityMeta::getEntityType).collect(Collectors.toSet()));
        assertEquals(Set.of(), accessProperties.getEntities().keySet());
        assertEquals(MerchantProduct.class, exposedEntities.resolveOrThrow("merchantProduct"));
        assertEquals(CustomerAddress.class, exposedEntities.resolveOrThrow("customerAddress"));
        assertEquals(MerchantArticle.class, exposedEntities.resolveOrThrow("merchantArticle"));
        assertEquals(Customer.class, exposedEntities.resolveOrThrow("customer"));
        assertThrows(CrudException.class, () -> exposedEntities.resolveOrThrow("order"));
        assertThrows(
            CrudException.class,
            () -> exposedEntities.resolveOrThrow("orderItem"));
        assertThrows(
            CrudException.class,
            () -> exposedEntities.resolveOrThrow(OrderItem.class.getName()));
    }

    @Test
    void 文档只注册订单与明细两层且不再暴露平台商品() throws Exception {
        mvc.perform(get("/api/ent-doc/contract"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[*].resourceCode",
                org.hamcrest.Matchers.containsInAnyOrder("customer", "customerAddress", "order", "orderItem",
                    "merchantProduct", "merchantArticle")))
            .andExpect(jsonPath("$.entities[?(@.resourceCode == 'merchantProduct')].fields[*].property",
                org.hamcrest.Matchers.containsInAnyOrder("id", "merchantId", "name", "price", "active", "version")))
            .andExpect(jsonPath("$.entities[?(@.resourceCode == 'orderItem')].fields[*].property",
                org.hamcrest.Matchers.containsInAnyOrder("id", "orderId", "merchantProductId", "merchantId", "productName", "unitPrice", "quantity", "lineAmount")));
    }
}
