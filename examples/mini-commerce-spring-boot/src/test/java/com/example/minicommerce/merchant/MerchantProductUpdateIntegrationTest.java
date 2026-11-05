package com.example.minicommerce.merchant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import org.springframework.boot.test.mock.mockito.SpyBean;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 使用实际配置、认证、授权及 JDBC 事务；测试不加入外层事务。 */
@SpringBootTest(properties = {
    "spring.config.import=classpath:demo.yml,classpath:crud-policies.yml",
    "spring.datasource.url=${COMMERCE_TEST_JDBC_URL:jdbc:h2:mem:commerce-merchant;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${COMMERCE_TEST_JDBC_USER:sa}",
    "spring.datasource.password=${COMMERCE_TEST_JDBC_PASSWORD:}"
})
@AutoConfigureMockMvc
class MerchantProductUpdateIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @SpyBean CrudGovernanceAuditRecorder auditRecorder;

    @BeforeEach
    void prepare() {
        jdbc.update("delete from merchant_product");
        jdbc.update("insert into merchant_product(id, merchant_id, name, price, active, version) values "
            + "(101, 1001, '商家商品一', 10, true, 0), (102, 1001, '商家商品二', 20, true, 0), "
            + "(201, 2001, '商家商品一', 30, true, 0)");
        clearInvocations(auditRecorder);
    }

    @Test
    void 匿名和客户仅返回上架商品白名单且商家仍按归属读取() throws Exception {
        jdbc.update("update merchant_product set active=false where id=102");
        mvc.perform(post("/api/ent-crud/merchantProduct/list").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].name").exists())
            .andExpect(jsonPath("$.data.items[0].version").doesNotExist())
            .andExpect(jsonPath("$.data.items[0].active").doesNotExist());
        mvc.perform(post("/api/ent-crud/merchantProduct/detail").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":102}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/ent-crud/merchantProduct/detail").header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":102}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.item.active").value(false));
        mvc.perform(post("/api/ent-crud/merchantProduct/list").header("Authorization", "Bearer demo-customer-a"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].version").doesNotExist());
        mvc.perform(post("/api/ent-crud/merchantProduct/detail").header("Authorization", "Bearer demo-customer-a")
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":102}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/ent-crud/merchantProduct/list").header("Authorization", "Bearer invalid"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/ent-crud/merchantProduct/update").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"id\":101,\"price\":1,\"version\":0}}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void 两个商家只读取自己的商品且请求不能切换身份() throws Exception {
        mvc.perform(post("/api/ent-crud/merchantProduct/list").contentType(MediaType.APPLICATION_JSON)
                .content("{\"options\":{\"limit\":100}}").header("Authorization", "Bearer demo-merchant-a")
                .header("X-Merchant-Id", "2001"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[?(@.merchantId != 1001)]").isEmpty());
        mvc.perform(post("/api/ent-crud/merchantProduct/list").contentType(MediaType.APPLICATION_JSON)
                .content("{\"options\":{\"limit\":100}}").header("Authorization", "Bearer demo-merchant-b"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(201));
    }

    @Test
    void 公共查询允许无凭证但无效凭证仍拒绝() throws Exception {
        mvc.perform(post("/api/ent-crud/merchantProduct/list").contentType(MediaType.APPLICATION_JSON)
                .content("{\"options\":{\"limit\":100}}")).andExpect(status().isOk());
        mvc.perform(post("/api/ent-crud/merchantProduct/list").contentType(MediaType.APPLICATION_JSON)
                .content("{\"options\":{\"limit\":100}}").header("Authorization", "Bearer unknown"))
            .andExpect(status().isForbidden());
    }

    @Test
    void 私有策略仅允许所属商家删除商品() throws Exception {
        String body = "{\"payload\":{\"id\":201},\"options\":{\"expectedVersion\":0}}";
        mvc.perform(post("/api/ent-crud/merchantProduct/delete").contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/ent-crud/merchantProduct/delete").header("Authorization", "Bearer demo-customer-a")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/ent-crud/merchantProduct/delete").header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        assertRow(201, "30.000000", 0L, 2001L);
        mvc.perform(post("/api/ent-crud/merchantProduct/delete").header("Authorization", "Bearer demo-merchant-b")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rows").value(1));
        assertEquals(0, jdbc.queryForObject("select count(*) from merchant_product where id=201", Integer.class));
        assertRow(101, "10.000000", 0L, 1001L);
    }

    @Test
    void 通用商品分页计数与筛选都不能扩大商家范围() throws Exception {
        mvc.perform(post("/api/ent-crud/merchantProduct/page").header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON).content("{\"options\":{\"page\":2,\"limit\":1}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total").value(2))
            .andExpect(jsonPath("$.data.items[0].id").value(101));
        mvc.perform(post("/api/ent-crud/merchantProduct/page").header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"options\":{\"filter\":{\"merchantId\":2001}}}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/ent-crud/merchantProduct/page").header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"options\":{\"limit\":1,\"countMode\":\"NONE\"}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.totalKnown").value(false))
            .andExpect(jsonPath("$.data.page.hasNext").value(true))
            .andExpect(jsonPath("$.data.items[0].merchantId").value(1001));
        mvc.perform(post("/api/ent-crud/merchantProduct/list").header("Authorization", "Bearer demo-customer-a"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(3));
    }

    @Test
    void 仅用实体声明完成商品创建校验归属填充和版本初始化() throws Exception {
        String created = mvc.perform(post("/api/ent-crud/merchantProduct/create")
                .header("Authorization", "Bearer demo-merchant-a").header("X-Merchant-Id", "2001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"name\":\"新商品\",\"price\":18.5,\"active\":true}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows").value(1))
            .andExpect(jsonPath("$.data.id").isNumber()).andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.data.id")).longValue();
        assertRow(id, "18.500000", 0L, 1001L);
        mvc.perform(post("/api/ent-crud/merchantProduct/update").header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"id\":" + id + ",\"active\":false,\"version\":0}}"))
            .andExpect(status().isOk());
        assertRow(id, "18.500000", 1L, 1001L);
        mvc.perform(post("/api/ent-crud/merchantProduct/detail").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + id + "}"))
            .andExpect(status().isNotFound());
        for (String payload : new String[] {
            "{\"name\":\"非法价格\",\"price\":0,\"active\":true}",
            "{\"name\":\"缺少价格\",\"active\":true}",
            "{\"name\":\"小数超限\",\"price\":1.1234567,\"active\":true}"
        }) {
            mvc.perform(post("/api/ent-crud/merchantProduct/create").header("Authorization", "Bearer demo-merchant-a")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"payload\":" + payload + "}"))
                .andExpect(status().isBadRequest());
        }
        assertEquals(4, jdbc.queryForObject("select count(*) from merchant_product", Integer.class));
    }

    @Test
    void 两条商品改价后分别递增版本且归属不变() throws Exception {
        update("""
            {"items":[{"id":101,"price":11.5,"version":0},{"id":102,"price":22,"version":0}]}
            """).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(101))
            .andExpect(jsonPath("$.data.items[1].id").value(102));
        assertRow(101, "11.500000", 1L, 1001L);
        assertRow(102, "22.000000", 1L, 1001L);
        assertRow(201, "30.000000", 0L, 2001L);
    }

    @Test
    void 通用金额载荷保留完整十四位整数和六位小数() throws Exception {
        update("{\"items\":[{\"id\":101,\"price\":12345678901234.123456,\"version\":0}]}")
            .andExpect(status().isOk());
        assertRow(101, "12345678901234.123456", 1L, 1001L);
    }

    @Test
    void 第二项跨商家则第一项商品价格和版本回滚() throws Exception {
        update("""
            {"items":[{"id":101,"price":11,"version":0},{"id":201,"price":33,"version":0}]}
            """).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WRITE_CONFLICT"));
        assertRow(101, "10.000000", 0L, 1001L);
        assertRow(201, "30.000000", 0L, 2001L);
    }

    @Test
    void 第二项过期版本或不存在与跨商家同样拒绝整批() throws Exception {
        for (long id : new long[] {102, 999}) {
            update("{\"items\":[{\"id\":101,\"price\":11,\"version\":0},"
                + "{\"id\":" + id + ",\"price\":22,\"version\":9}]}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WRITE_CONFLICT"));
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
            "{\"items\":[{\"id\":101,\"price\":null,\"version\":0}]}",
            "{\"items\":[{\"id\":101,\"price\":1.1234567,\"version\":0}]}",
            "{\"items\":[{\"id\":101,\"price\":100000000000000,\"version\":0}]}",
            "{\"items\":[{\"id\":101,\"price\":11,\"version\":-1}]}",
            "{\"items\":[{\"id\":101.5,\"price\":11,\"version\":0}]}",
            "{\"items\":[{\"id\":101,\"price\":11,\"version\":0.5}]}",
            "{\"items\":[{\"id\":101,\"version\":0}]}",
            "{\"items\":[]}"}) {
            update(body).andExpect(status().isBadRequest());
            assertRow(101, "10.000000", 0L, 1001L);
        }
    }

    @Test
    void 不同商品可更新不同字段且未提交字段保持原值() throws Exception {
        update("""
            {"items":[{"id":102,"name":"新名称","version":0},{"id":101,"active":false,"version":0}]}
            """).andExpect(status().isOk()).andExpect(jsonPath("$.data.rows").value(2))
            .andExpect(jsonPath("$.data.items[0].id").value(102))
            .andExpect(jsonPath("$.data.items[1].id").value(101));
        assertRow(101, "10.000000", 1L, 1001L);
        assertRow(102, "20.000000", 1L, 1001L);
        assertEquals("新名称", jdbc.queryForObject("select name from merchant_product where id=102", String.class));
        assertEquals(false, jdbc.queryForObject("select active from merchant_product where id=101", Boolean.class));
    }

    @Test
    void 显式空值不能清空商品名称或启用状态() throws Exception {
        for (String field : new String[] {"name", "active"}) {
            update("{\"items\":[{\"id\":101,\"" + field + "\":null,\"version\":0}]}")
                .andExpect(status().isBadRequest());
            assertRow(101, "10.000000", 0L, 1001L);
        }
    }

    @Test
    void 任一项金额非法则整批不写入并返回实体中文校验提示() throws Exception {
        update("""
            {"items":[{"id":101,"price":11,"version":0},{"id":102,"price":0,"version":0}]}
            """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("价格须大于 0"));
        assertRow(101, "10.000000", 0L, 1001L);
        assertRow(102, "20.000000", 0L, 1001L);
    }

    @Test
    void 未授权字段未知字段及客户身份不能通过批量入口写入() throws Exception {
        for (String field : new String[] {"merchantId", "missingField"}) {
            update("{\"items\":[{\"id\":101,\"" + field + "\":2001,\"version\":0}]}")
                .andExpect(status().is4xxClientError());
            assertRow(101, "10.000000", 0L, 1001L);
        }
        mvc.perform(post("/api/ent-crud/merchantProduct/updateBatch").header("Authorization", "Bearer demo-customer-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"items\":[{\"id\":101,\"price\":1,\"version\":0}]}}"))
            .andExpect(status().isForbidden());
        assertRow(101, "10.000000", 0L, 1001L);
    }

    @Test
    void 批量更新不接受外层事务且回滚目标没有成功审计() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            try {
                update("{\"items\":[{\"id\":101,\"price\":11,\"version\":0}]}")
                    .andExpect(status().isBadRequest());
            } catch (Exception error) {
                throw new AssertionError(error);
            }
        });
        assertRow(101, "10.000000", 0L, 1001L);
        assertTrue(mockingDetails(auditRecorder).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals("record"))
            .map(invocation -> (CrudGovernanceAuditEvent) invocation.getArgument(0))
            .noneMatch(event -> event.getOutcome() == CrudGovernanceAuditOutcome.SUCCESS));
    }

    @Test
    void 单条通用更新也校验实体金额约束() throws Exception {
        mvc.perform(post("/api/ent-crud/merchantProduct/update").header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"id\":101,\"price\":0},\"options\":{\"expectedVersion\":0}}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("价格须大于 0"));
        assertRow(101, "10.000000", 0L, 1001L);
    }

    @Test
    void 单条与批量更新共用版本条件且不能用无版本写入绕过乐观锁() throws Exception {
        mvc.perform(post("/api/ent-crud/merchantProduct/update").header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON).content("{\"payload\":{\"id\":101,\"price\":11}}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ent-crud/merchantProduct/update").header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON).content("{\"payload\":{\"id\":101,\"price\":11,\"version\":0}}"))
            .andExpect(status().isOk());
        assertRow(101, "11.000000", 1L, 1001L);
        update("{\"items\":[{\"id\":101,\"price\":99,\"version\":0}]}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WRITE_CONFLICT"));
        assertRow(101, "11.000000", 1L, 1001L);
    }

    @Test
    void 通用接口不能绕过商家入口修改商品() throws Exception {
        mvc.perform(post("/api/ent-crud/merchantProduct/update").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"id\":101,\"merchantId\":2001,\"price\":1,\"version\":0}}"))
            .andExpect(status().isForbidden());
        assertRow(101, "10.000000", 0L, 1001L);
    }

    @Test
    void 商家商品默认开放但无商家身份时拒绝通用写入() throws Exception {
        mvc.perform(post("/api/ent-crud/merchantProduct/update").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"id\":101,\"name\":\"越权\",\"version\":0}}"))
            .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions update(String body) throws Exception {
        return mvc.perform(post("/api/ent-crud/merchantProduct/updateBatch").header("Authorization", "Bearer demo-merchant-a")
            .contentType(MediaType.APPLICATION_JSON).content("{\"payload\":" + body + "}"));
    }

    private void assertRow(long id, String price, long version, long merchantId) {
        assertEquals(price, jdbc.queryForObject("select price from merchant_product where id=?", java.math.BigDecimal.class, id).toPlainString());
        assertEquals(version, jdbc.queryForObject("select version from merchant_product where id=?", Long.class, id));
        assertEquals(merchantId, jdbc.queryForObject("select merchant_id from merchant_product where id=?", Long.class, id));
    }
}
