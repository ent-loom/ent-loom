package com.example.minicommerce.merchant;

import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.config.import=classpath:demo.yml,classpath:crud-policies.yml",
    "spring.datasource.url=${COMMERCE_TEST_JDBC_URL:jdbc:h2:mem:commerce-product-summary;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${COMMERCE_TEST_JDBC_USER:sa}",
    "spring.datasource.password=${COMMERCE_TEST_JDBC_PASSWORD:}"
})
@AutoConfigureMockMvc
class ProductSummaryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @SpyBean CrudGovernanceAuditRecorder auditRecorder;

    @BeforeEach
    void seed() {
        jdbc.update("delete from merchant_product");
        jdbc.update("insert into merchant_product(id, merchant_id, name, price, active, version) values "
            + "(101,1001,'甲',10,true,0),(102,1001,'乙',20,false,0),"
            + "(103,1001,'丙',30,true,0),(201,2001,'丁',40,true,0)");
        clearInvocations(auditRecorder);
    }

    @Test
    void 商家分页和计数取授权范围与筛选交集() throws Exception {
        mvc.perform(post("/api/merchant/products/page").header("Authorization", "Bearer demo-merchant-a")
                .header("X-Merchant-Id", "2001").contentType(MediaType.APPLICATION_JSON)
                .content("{\"page\":2,\"limit\":1,\"active\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
            .andExpect(jsonPath("$.requestId").isNotEmpty())
            .andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].id").value(103))
            .andExpect(jsonPath("$.data.items[0].name").value("丙"))
            .andExpect(jsonPath("$.data.items[0].merchantId").doesNotExist())
            .andExpect(jsonPath("$.data.items[0].version").doesNotExist());
        mvc.perform(post("/api/merchant/products/page").header("Authorization", "Bearer demo-merchant-b")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(201));
        var events = ArgumentCaptor.forClass(CrudGovernanceAuditEvent.class);
        verify(auditRecorder, times(2)).record(events.capture());
        assertEquals(CrudGovernanceAuditOutcome.SUCCESS, events.getAllValues().get(0).getOutcome());
        assertEquals("merchantProduct", events.getAllValues().get(0).getAction().getResource());
        assertEquals("view", events.getAllValues().get(0).getAction().getAction());
        assertEquals("merchant-user-a", events.getAllValues().get(0).getSubject().getSubjectId());
    }

    @Test
    void 管理员全量与非法凭证和非法输入() throws Exception {
        mvc.perform(post("/api/merchant/products/page").header("Authorization", "Bearer demo-platform-admin")
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3));
        for (String token : new String[] {"demo-customer-a", "invalid", ""}) {
            var request = post("/api/merchant/products/page").contentType(MediaType.APPLICATION_JSON).content("{}");
            if (!token.isEmpty()) request.header("Authorization", "Bearer " + token);
            mvc.perform(request).andExpect(status().isForbidden());
        }
        for (String body : new String[] {"{\"limit\":101}", "{\"page\":0}", "{\"merchantId\":2001}"}) {
            mvc.perform(post("/api/merchant/products/page").header("Authorization", "Bearer demo-merchant-a")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
    }
}
