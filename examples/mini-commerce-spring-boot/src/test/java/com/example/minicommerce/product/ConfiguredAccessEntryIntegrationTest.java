package com.example.minicommerce.product;

import com.entloom.crud.core.adapter.AccessEntryResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 使用实际入口 Bean 与运行配置验证 Demo，不替换治理组件。 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:commerce-visibility-demo;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.sql.init.mode=always"
})
@ActiveProfiles({"example", "visibility"})
@AutoConfigureMockMvc
class ConfiguredAccessEntryIntegrationTest {
    @Autowired AccessEntryResolver accessEntryResolver;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test
    void 默认用户端自动过滤且下单场景沿用可信入口() throws Exception {
        assertEquals("consumer", accessEntryResolver.resolveAccessEntry(null));
        jdbc.update("delete from order_item");
        jdbc.update("delete from `order`");
        jdbc.update("delete from product");
        jdbc.update("delete from customer");
        jdbc.update("insert into customer values (1, 'Demo 客户', 'demo@example.com')");
        jdbc.update("insert into product values (10, '启用商品', 10, true), (30, '停用商品', 30, false)");
        mvc.perform(post("/api/ent-crud/product/page").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(10));
        mvc.perform(post("/api/ent-crud/order/action/place").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"customerId\":1,\"items\":[{\"productId\":10,\"quantity\":1}]}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalAmount").value(10.0));
    }
}
