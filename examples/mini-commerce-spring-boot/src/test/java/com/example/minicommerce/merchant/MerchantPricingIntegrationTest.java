package com.example.minicommerce.merchant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 使用实际配置、认证、授权及 JDBC 事务；测试不加入外层事务。 */
@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:commerce-merchant;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
class MerchantPricingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void prepare() {
        jdbc.update("delete from merchant_product");
        jdbc.update("insert into merchant_product(id, product_id, merchant_id, price, version) values "
            + "(101, 10, 1001, 10, 0), (102, 20, 1001, 20, 0), (201, 10, 2001, 30, 0)");
    }

    @Test
    void 两个商家只读取自己的报价且请求不能切换身份() throws Exception {
        mvc.perform(get("/api/merchant/products").header("Authorization", "Bearer demo-merchant-a")
                .header("X-Merchant-Id", "2001"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[?(@.merchantId != 1001)]").isEmpty());
        mvc.perform(get("/api/merchant/products").header("Authorization", "Bearer demo-merchant-b"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].id").value(201));
    }

    @Test
    void 缺失凭证或无效凭证不能访问商家接口() throws Exception {
        mvc.perform(get("/api/merchant/products")).andExpect(status().isForbidden());
        mvc.perform(get("/api/merchant/products").header("Authorization", "Bearer unknown"))
            .andExpect(status().isForbidden());
    }

    @Test
    void 两条报价提交后分别递增版本且归属不变() throws Exception {
        update("""
            {"items":[{"id":101,"price":11.5,"version":0},{"id":102,"price":22,"version":0}]}
            """).andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").value(101))
            .andExpect(jsonPath("$.data[1]").value(102));
        assertRow(101, "11.500000", 1L, 1001L);
        assertRow(102, "22.000000", 1L, 1001L);
        assertRow(201, "30.000000", 0L, 2001L);
    }

    @Test
    void 第二项跨商家则第一项价格和版本回滚() throws Exception {
        update("""
            {"items":[{"id":101,"price":11,"version":0},{"id":201,"price":33,"version":0}]}
            """).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRICE_UPDATE_REJECTED"));
        assertRow(101, "10.000000", 0L, 1001L);
        assertRow(201, "30.000000", 0L, 2001L);
    }

    @Test
    void 第二项过期版本或不存在与跨商家同样拒绝整批() throws Exception {
        for (long id : new long[] {102, 999}) {
            update("{\"items\":[{\"id\":101,\"price\":11,\"version\":0},"
                + "{\"id\":" + id + ",\"price\":22,\"version\":9}]}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRICE_UPDATE_REJECTED"));
            assertRow(101, "10.000000", 0L, 1001L);
            assertRow(102, "20.000000", 0L, 1001L);
        }
    }

    @Test
    void 重复目标缺少版本和非法金额不写入() throws Exception {
        for (String body : new String[] {
            "{\"items\":[{\"id\":101,\"price\":11,\"version\":0},{\"id\":101,\"price\":12,\"version\":0}]}",
            "{\"items\":[{\"id\":101,\"price\":11}]}",
            "{\"items\":[{\"id\":101,\"price\":0,\"version\":0}]}",
            "{\"items\":[]}"}) {
            update(body).andExpect(status().isBadRequest());
            assertRow(101, "10.000000", 0L, 1001L);
        }
    }

    @Test
    void 通用接口不能绕过商家入口修改报价() throws Exception {
        mvc.perform(post("/api/ent-crud/merchantProduct/update").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"id\":101,\"merchantId\":2001,\"price\":1}}"))
            .andExpect(status().isNotFound());
        assertRow(101, "10.000000", 0L, 1001L);
    }

    private org.springframework.test.web.servlet.ResultActions update(String body) throws Exception {
        return mvc.perform(post("/api/merchant/products/prices").header("Authorization", "Bearer demo-merchant-a")
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void assertRow(long id, String price, long version, long merchantId) {
        assertEquals(price, jdbc.queryForObject("select price from merchant_product where id=?", java.math.BigDecimal.class, id).toPlainString());
        assertEquals(version, jdbc.queryForObject("select version from merchant_product where id=?", Long.class, id));
        assertEquals(merchantId, jdbc.queryForObject("select merchant_id from merchant_product where id=?", Long.class, id));
    }
}
