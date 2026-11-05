package com.example.minicommerce.merchant;

import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.example.minicommerce.merchant.entity.MerchantArticle;
import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.core.capability.command.gateway.CommandGateway;
import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.admission.AccessAdmissionService;
import com.entloom.crud.core.governance.admission.AccessContextScope;
import com.entloom.crud.core.governance.admission.CredentialAuthentication;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.config.import=classpath:demo.yml,classpath:crud-policies.yml",
    "spring.datasource.url=${COMMERCE_TEST_JDBC_URL:jdbc:h2:mem:commerce-merchant-article;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${COMMERCE_TEST_JDBC_USER:sa}",
    "spring.datasource.password=${COMMERCE_TEST_JDBC_PASSWORD:}"
})
@AutoConfigureMockMvc
class MerchantArticleIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CommandGateway commandGateway;
    @Autowired AccessAdmissionService admissionService;
    @SpyBean CrudGovernanceAuditRecorder auditRecorder;

    @BeforeEach
    void seed() {
        jdbc.update("delete from merchant_article");
        jdbc.update("insert into merchant_article(id, merchant_id, title, content) values "
            + "(101,1001,'甲','甲正文'),(102,1001,'乙','乙正文'),"
            + "(103,1001,'丙','丙正文'),(201,2001,'丁','丁正文')");
        clearInvocations(auditRecorder);
    }

    @Test
    void 错误主键类型在创建更新删除之前拒绝且数据不变() throws Exception {
        var wrongType = EntityType.of(MerchantArticle.class, Integer.class);
        var article = new MerchantArticle();
        article.setTitle("不能落库");
        article.setContent("正文");
        UpdatePatch<MerchantArticle> patch = objectMapper.readValue("{\"title\":\"不能更新\"}",
            objectMapper.getTypeFactory().constructParametricType(UpdatePatch.class, MerchantArticle.class));
        var admission = admissionService.admit(() -> CredentialAuthentication.authenticated(
            new AccessSubject("merchant-user-a", AccessSubjectType.HUMAN), "merchant", "1001"),
            "merchant", "wrong-id-type", null);
        try (var ignored = AccessContextScope.enter(admission)) {
            var create = assertThrows(ValidationException.class, () -> commandGateway.create(wrongType, article));
            assertTrue(create.getMessage().contains("主键类型与元数据不一致"));
            assertThrows(ValidationException.class, () -> commandGateway.update(wrongType, 101, patch));
            assertThrows(ValidationException.class, () -> commandGateway.delete(wrongType, 101));
        }
        assertEquals(4, jdbc.queryForObject("select count(*) from merchant_article", Integer.class));
        assertEquals("甲", jdbc.queryForObject("select title from merchant_article where id=101", String.class));
    }

    @Test
    void 自定义分页由内核隔离商家并统一投影和计数() throws Exception {
        mvc.perform(post("/api/merchant/articles/page").header("Authorization", "Bearer demo-merchant-a")
                .header("X-Merchant-Id", "2001").contentType(MediaType.APPLICATION_JSON)
                .content("{\"page\":2,\"limit\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
            .andExpect(jsonPath("$.data.page").value(2)).andExpect(jsonPath("$.data.limit").value(1))
            .andExpect(jsonPath("$.data.totalKnown").value(true))
            .andExpect(jsonPath("$.data.totalPages").value(3)).andExpect(jsonPath("$.data.returned").value(1))
            .andExpect(jsonPath("$.requestId").isNotEmpty())
            .andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].id").value(102))
            .andExpect(jsonPath("$.data.items[0].title").value("乙"))
            .andExpect(jsonPath("$.data.items[0].merchantId").doesNotExist())
            .andExpect(jsonPath("$.data.items[0].content").doesNotExist());
        mvc.perform(post("/api/merchant/articles/page").header("Authorization", "Bearer demo-merchant-b")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.page").value(1)).andExpect(jsonPath("$.data.limit").value(20))
            .andExpect(jsonPath("$.data.items[0].id").value(201));
        var events = ArgumentCaptor.forClass(CrudGovernanceAuditEvent.class);
        verify(auditRecorder, times(2)).record(events.capture());
        assertEquals(CrudGovernanceAuditOutcome.SUCCESS, events.getAllValues().get(0).getOutcome());
        assertEquals("merchantArticle", events.getAllValues().get(0).getAction().getResource());
        assertEquals("view", events.getAllValues().get(0).getAction().getAction());
        assertEquals("merchant-user-a", events.getAllValues().get(0).getSubject().getSubjectId());
    }

    @Test
    void 分页参数支持默认值并校验边界() throws Exception {
        for (String body : new String[] {"{}", "{\"page\":null,\"limit\":null}"}) {
            mvc.perform(post("/api/merchant/articles/page").header("Authorization", "Bearer demo-merchant-a")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.limit").value(20));
        }
        mvc.perform(post("/api/merchant/articles/page").header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON).content("{\"page\":1,\"limit\":100}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.limit").value(100));
        for (String body : new String[] {"{\"page\":0}", "{\"limit\":0}", "{\"limit\":101}",
            "{\"unknown\":1}"}) {
            mvc.perform(post("/api/merchant/articles/page").header("Authorization", "Bearer demo-merchant-a")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    void 管理员全量与非法凭证和非法输入() throws Exception {
        mvc.perform(post("/api/merchant/articles/page").header("Authorization", "Bearer demo-platform-admin")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(4));
        for (String token : new String[] {"demo-customer-a", "invalid", ""}) {
            var request = post("/api/merchant/articles/page").contentType(MediaType.APPLICATION_JSON).content("{}");
            if (!token.isEmpty()) request.header("Authorization", "Bearer " + token);
            mvc.perform(request).andExpect(status().isForbidden());
        }
        for (String body : new String[] {"{\"limit\":101}", "{\"page\":0}", "{\"merchantId\":2001}"}) {
            mvc.perform(post("/api/merchant/articles/page").header("Authorization", "Bearer demo-merchant-a")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void 请求标识贯穿业务响应与审计且每次请求独立() throws Exception {
        String previousTraceId = MDC.get("traceId");
        MDC.put("traceId", "trace-merchant-article");
        try {
            var request = post("/api/merchant/articles/page")
                .header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON).content("{}");
            JsonNode first = objectMapper.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
            JsonNode second = objectMapper.readTree(mvc.perform(post("/api/merchant/articles/page")
                    .header("Authorization", "Bearer demo-merchant-a")
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            JsonNode generic = objectMapper.readTree(mvc.perform(post("/api/ent-crud/merchantProduct/page")
                    .header("Authorization", "Bearer demo-merchant-a")
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertFalse(first.path("requestId").asText().isBlank());
            assertFalse(second.path("requestId").asText().isBlank());
            assertNotEquals(first.path("requestId").asText(), second.path("requestId").asText());
            assertEquals("trace-merchant-article", first.path("traceId").asText());
            var events = ArgumentCaptor.forClass(CrudGovernanceAuditEvent.class);
            verify(auditRecorder, times(3)).record(events.capture());
            assertEquals(first.path("requestId").asText(), events.getAllValues().get(0).getRequestId());
            assertEquals(first.path("traceId").asText(), events.getAllValues().get(0).getTraceId());
            assertEquals(second.path("requestId").asText(), events.getAllValues().get(1).getRequestId());
            assertFalse(generic.path("requestId").asText().isBlank());
            assertEquals(generic.path("requestId").asText(), events.getAllValues().get(2).getRequestId());
            assertEquals("trace-merchant-article", generic.path("traceId").asText());
            assertEquals(generic.path("traceId").asText(), events.getAllValues().get(2).getTraceId());
            assertEquals("trace-merchant-article", MDC.get("traceId"));
        } finally {
            if (previousTraceId == null) MDC.remove("traceId");
            else MDC.put("traceId", previousTraceId);
        }
    }

    @Test
    void 自定义增改删复用内核校验归属填充和写入隔离() throws Exception {
        JsonNode created = objectMapper.readTree(mvc.perform(post("/api/merchant/articles")
                .header("Authorization", "Bearer demo-merchant-a").header("X-Merchant-Id", "2001")
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"新文章\",\"content\":\"正文\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows").value(1))
            .andReturn().getResponse().getContentAsString());
        long id = created.path("data").path("id").asLong();
        assertEquals(1001L, jdbc.queryForObject("select merchant_id from merchant_article where id=?", Long.class, id));
        mvc.perform(get("/api/merchant/articles/{id}", id).header("Authorization", "Bearer demo-merchant-a"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").value("正文"))
            .andExpect(jsonPath("$.data.merchantId").value(1001));
        mvc.perform(get("/api/merchant/articles/{id}", id).header("Authorization", "Bearer demo-merchant-b"))
            .andExpect(status().isNotFound());
        mvc.perform(put("/api/merchant/articles/{id}", id).header("Authorization", "Bearer demo-merchant-b")
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"越权\"}"))
            .andExpect(status().isConflict());
        mvc.perform(put("/api/merchant/articles/{id}", id).header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"更新标题\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows").value(1));
        assertEquals("更新标题", jdbc.queryForObject("select title from merchant_article where id=?", String.class, id));
        assertEquals("正文", jdbc.queryForObject("select content from merchant_article where id=?", String.class, id));
        mvc.perform(delete("/api/merchant/articles/{id}", id).header("Authorization", "Bearer demo-merchant-b"))
            .andExpect(status().isConflict());
        mvc.perform(delete("/api/merchant/articles/{id}", id).header("Authorization", "Bearer demo-merchant-a"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows").value(1));
        assertEquals(0, jdbc.queryForObject("select count(*) from merchant_article where id=?", Integer.class, id));
    }

    @Test
    void 自定义写入不能伪造归属或绕过实体校验() throws Exception {
        for (String body : new String[] {"{\"title\":\" \",\"content\":\"正文\"}", "{\"title\":\"缺正文\"}"}) {
            mvc.perform(post("/api/merchant/articles").header("Authorization", "Bearer demo-merchant-a")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/merchant/articles").header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"merchantId\":2001,\"title\":\"伪造\",\"content\":\"正文\"}"))
            .andExpect(status().isBadRequest());
        for (String body : new String[] {"{\"merchantId\":2001}", "{\"id\":201,\"title\":\"伪造\"}"}) {
            mvc.perform(put("/api/merchant/articles/101").header("Authorization", "Bearer demo-merchant-a")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is4xxClientError());
        }
        mvc.perform(put("/api/merchant/articles/101").header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" \"}"))
            .andExpect(status().isBadRequest());
        assertEquals(4, jdbc.queryForObject("select count(*) from merchant_article", Integer.class));
        assertEquals("甲", jdbc.queryForObject("select title from merchant_article where id=101", String.class));
    }

    @Test
    void 类型化Patch保留显式空值并拒绝空更新和未知字段() throws Exception {
        for (String body : new String[] {
            "{\"title\":null}", "{\"title\":null,\"content\":\"新正文\"}", "{}", "{\"unknown\":1}"
        }) {
            mvc.perform(put("/api/merchant/articles/101").header("Authorization", "Bearer demo-merchant-a")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        assertEquals("甲", jdbc.queryForObject("select title from merchant_article where id=101", String.class));
        assertEquals("甲正文", jdbc.queryForObject("select content from merchant_article where id=101", String.class));
    }

    @Test
    void 文章仅使用自定义入口且客户没有文章权限() throws Exception {
        for (String action : new String[] {"page", "list", "detail", "create", "update", "delete"}) {
            mvc.perform(post("/api/ent-crud/merchantArticle/" + action)
                    .header("Authorization", "Bearer demo-merchant-a").contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/merchant/articles").header("Authorization", "Bearer demo-customer-a")
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"越权\",\"content\":\"正文\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void 认证和业务授权失败时错误包络使用同一请求标识() throws Exception {
        String previousTraceId = MDC.get("traceId");
        MDC.put("traceId", "trace-article-denied");
        try {
            String deniedRequestId = null;
            for (String token : new String[] {"invalid", "demo-customer-a"}) {
                JsonNode response = objectMapper.readTree(mvc.perform(post("/api/merchant/articles/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString());
                assertFalse(response.path("requestId").asText().isBlank());
                assertEquals("trace-article-denied", response.path("traceId").asText());
                assertEquals(response.path("requestId").asText(), response.path("error").path("requestId").asText());
                assertEquals(response.path("traceId").asText(), response.path("error").path("traceId").asText());
                if (token.equals("demo-customer-a")) deniedRequestId = response.path("requestId").asText();
            }
            var event = ArgumentCaptor.forClass(CrudGovernanceAuditEvent.class);
            verify(auditRecorder).record(event.capture());
            assertEquals(deniedRequestId, event.getValue().getRequestId());
            assertEquals("trace-article-denied", event.getValue().getTraceId());
            assertEquals("trace-article-denied", MDC.get("traceId"));
        } finally {
            if (previousTraceId == null) MDC.remove("traceId");
            else MDC.put("traceId", previousTraceId);
        }
    }
}
