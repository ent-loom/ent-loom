package com.example.minicommerce.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 随机端口真实 HTTP 与独立 H2，复用实际认证和治理配置，不加入外层事务。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:commerce-merchant-order-page;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password="
})
class MerchantOrderPageIntegrationTest {
    private static final String CONSUMER_PAGE = "/api/consumer/orders/page";
    private static final String MERCHANT_PAGE = "/api/merchant/orders/page";

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @BeforeEach
    void 准备交叉客户和商家归属数据() {
        jdbc.update("delete from merchant_order");
        jdbc.update("""
            insert into merchant_order(id, order_id, customer_id, merchant_id, total_amount) values
            (501, 9001, 301, 1001, 10),
            (502, 9001, 301, 2001, 20),
            (503, 9002, 302, 1001, 30),
            (504, 9003, 301, 1001, 40),
            (505, 9004, 302, 2001, 50)
            """);
    }

    @Test
    void 两个客户读取同一实体时各自限定客户范围且默认分页返回精确总数() throws Exception {
        JsonNode data = assertPage(post(CONSUMER_PAGE, "demo-customer-a", "{}"), 3, 1, 20, 501, 502, 504);
        assertEquals(9001, data.get("items").get(0).get("orderId").asLong());
        assertEquals(301, data.get("items").get(0).get("customerId").asLong());
        assertEquals(1001, data.get("items").get(0).get("merchantId").asLong());
        assertEquals(0, BigDecimal.TEN.compareTo(data.get("items").get(0).get("totalAmount").decimalValue()));
        assertPage(post(CONSUMER_PAGE, "demo-customer-b", "{}"), 2, 1, 20, 503, 505);
    }

    @Test
    void 两个商家读取同一实体时各自限定商家范围且总数不包含其他商家() throws Exception {
        assertPage(post(MERCHANT_PAGE, "demo-merchant-a", "{}"), 3, 1, 20, 501, 503, 504);
        assertPage(post(MERCHANT_PAGE, "demo-merchant-b", "{}"), 2, 1, 20, 502, 505);
    }

    @Test
    void 客户追加商家条件与客户授权范围取交集() throws Exception {
        // 同一商家的 503 属于另一客户，不能随商家筛选混入。
        assertPage(post(CONSUMER_PAGE, "demo-customer-a", "{\"merchantId\":1001}"), 2, 1, 20, 501, 504);
        assertPage(post(CONSUMER_PAGE, "demo-customer-a", "{\"merchantId\":2001}"), 1, 1, 20, 502);
        assertPage(post(CONSUMER_PAGE, "demo-customer-b", "{\"merchantId\":1001}"), 1, 1, 20, 503);
        assertPage(post(CONSUMER_PAGE, "demo-customer-a", "{\"merchantId\":9999}"), 0, 1, 20);
    }

    @Test
    void 商家追加客户条件与商家授权范围取交集() throws Exception {
        // 同一客户的 502 属于另一商家，不能随客户筛选混入。
        assertPage(post(MERCHANT_PAGE, "demo-merchant-a", "{\"customerId\":301}"), 2, 1, 20, 501, 504);
        assertPage(post(MERCHANT_PAGE, "demo-merchant-a", "{\"customerId\":302}"), 1, 1, 20, 503);
        assertPage(post(MERCHANT_PAGE, "demo-merchant-b", "{\"customerId\":301}"), 1, 1, 20, 502);
        assertPage(post(MERCHANT_PAGE, "demo-merchant-a", "{\"customerId\":9999}"), 0, 1, 20);
    }

    @ParameterizedTest
    @CsvSource({
        "/api/consumer/orders/page, demo-customer-a, 3, 501, 502",
        "/api/consumer/orders/page, demo-customer-b, 2, 503, 505",
        "/api/merchant/orders/page, demo-merchant-a, 3, 501, 503",
        "/api/merchant/orders/page, demo-merchant-b, 2, 502, 505"
    })
    void 两端分页按主键升序稳定且每页保留范围内总数(String route, String token,
        long total, long first, long second) throws Exception {
        assertPage(post(route, token, "{\"pageNumber\":1,\"pageSize\":1}"), total, 1, 1, first);
        assertPage(post(route, token, "{\"pageNumber\":2,\"pageSize\":1}"), total, 2, 1, second);
        assertPage(post(route, token, "{\"pageNumber\":1,\"pageSize\":1}"), total, 1, 1, first);
        assertPage(post(route, token, "{\"pageNumber\":4,\"pageSize\":1}"), total, 4, 1);
    }

    @ParameterizedTest
    @CsvSource({
        "/api/consumer/orders/page, demo-customer-a, merchantId",
        "/api/merchant/orders/page, demo-merchant-a, customerId"
    })
    void 筛选后的分页总数也只统计授权与筛选的交集(String route, String token, String field) throws Exception {
        long id = field.equals("merchantId") ? 1001 : 301;
        String body = "{\"%s\":%d,\"pageNumber\":%d,\"pageSize\":1}";
        assertPage(post(route, token, body.formatted(field, id, 1)), 2, 1, 1, 501);
        assertPage(post(route, token, body.formatted(field, id, 2)), 2, 2, 1, 504);
    }

    @ParameterizedTest
    @ValueSource(strings = {CONSUMER_PAGE, MERCHANT_PAGE})
    void 两端均拒绝缺失或无效凭证且伪造请求头不能代替认证(String route) {
        assertStatus(post(route, null, "{}"), 403);
        assertStatus(post(route, "unknown", "{}"), 403);
        assertStatus(post(route, null, "{}", forgedHeaders()), 403);
        assertStatus(post(route, "unknown", "{}", forgedHeaders()), 403);
    }

    @ParameterizedTest
    @CsvSource({
        "/api/merchant/orders/page, demo-customer-a",
        "/api/merchant/orders/page, demo-customer-b",
        "/api/consumer/orders/page, demo-merchant-a",
        "/api/consumer/orders/page, demo-merchant-b"
    })
    void 两端不能混用身份令牌且请求头不能伪造入口(String route, String token) {
        assertStatus(post(route, token, "{}"), 403);
        assertStatus(post(route, token, "{}", forgedHeaders()), 403);
    }

    @Test
    void 客户请求体和请求头不能将客户归属切换为另一客户() throws Exception {
        String body = "{\"customerId\":302}";
        // 严格 DTO 拒绝未知归属字段，不接受请求指定当前客户。
        assertInvalidBody(post(CONSUMER_PAGE, "demo-customer-a", body));
        assertPage(post(CONSUMER_PAGE, "demo-customer-a", "{}", forgedHeaders()), 3, 1, 20, 501, 502, 504);
        assertInvalidBody(post(CONSUMER_PAGE, "demo-customer-a", body, forgedHeaders()));
        assertInvalidBody(post(CONSUMER_PAGE, "demo-customer-a", "{\"customerId\":302,\"merchantId\":1001}",
            forgedHeaders()));
        assertPage(post(CONSUMER_PAGE, "demo-customer-a", "{\"merchantId\":1001}", forgedHeaders()),
            2, 1, 20, 501, 504);
    }

    @Test
    void 商家请求体和请求头不能将商家归属切换为另一商家() throws Exception {
        String body = "{\"merchantId\":2001}";
        // 严格 DTO 拒绝未知归属字段，不接受请求指定当前商家。
        assertInvalidBody(post(MERCHANT_PAGE, "demo-merchant-a", body));
        assertPage(post(MERCHANT_PAGE, "demo-merchant-a", "{}", forgedHeaders()), 3, 1, 20, 501, 503, 504);
        assertInvalidBody(post(MERCHANT_PAGE, "demo-merchant-a", body, forgedHeaders()));
        assertInvalidBody(post(MERCHANT_PAGE, "demo-merchant-a", "{\"merchantId\":2001,\"customerId\":301}",
            forgedHeaders()));
        assertPage(post(MERCHANT_PAGE, "demo-merchant-a", "{\"customerId\":301}", forgedHeaders()),
            2, 1, 20, 501, 504);
    }

    @ParameterizedTest
    @CsvSource({
        "/api/consumer/orders/page, demo-customer-a, merchantId",
        "/api/merchant/orders/page, demo-merchant-a, customerId"
    })
    void 两端拒绝非法分页和非正数筛选标识(String route, String token, String field) {
        for (String body : new String[] {
            "{\"pageNumber\":0}", "{\"pageNumber\":-1}",
            "{\"pageSize\":0}", "{\"pageSize\":-1}", "{\"pageSize\":101}",
            "{\"%s\":-1}".formatted(field), "{\"%s\":0}".formatted(field)
        }) {
            assertStatus(post(route, token, body), 400);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "demo-customer-a", "demo-customer-b", "demo-merchant-a", "demo-merchant-b"})
    void 通用分页路由始终拒绝未暴露的商家子订单实体(String token) throws Exception {
        ResponseEntity<String> response = post("/api/ent-crud/merchantOrder/page", token.isEmpty() ? null : token, "{}");
        assertStatus(response, 404);
        assertEquals("ENTITY_NOT_EXPOSED", json.readTree(response.getBody()).get("code").asText());
    }

    private ResponseEntity<String> post(String route, String token, String body) {
        return post(route, token, body, new HttpHeaders());
    }

    private ResponseEntity<String> post(String route, String token, String body, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return http.exchange(route, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private HttpHeaders forgedHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Customer-Id", "302");
        headers.set("X-Merchant-Id", "2001");
        headers.set("X-Subject-Id", "customer-user-b");
        headers.set("X-Access-Entry", "management");
        headers.set("X-Crud-Access-Entry", "management");
        return headers;
    }

    private void assertStatus(ResponseEntity<String> response, int status) {
        assertEquals(status, response.getStatusCode().value(), response.getBody());
    }

    private void assertInvalidBody(ResponseEntity<String> response) throws Exception {
        assertStatus(response, 400);
        JsonNode result = json.readTree(response.getBody());
        assertEquals("VALIDATION_ERROR", result.get("code").asText(), response.getBody());
        assertTrue(result.get("data").isNull(), response.getBody());
    }

    private JsonNode assertPage(ResponseEntity<String> response, long total, int page, int size, long... ids)
        throws Exception {
        assertStatus(response, 200);
        JsonNode data = json.readTree(response.getBody()).get("data");
        assertEquals(total, data.get("total").asLong(), response.getBody());
        assertTrue(data.get("totalKnown").asBoolean(), response.getBody());
        assertEquals(page, data.get("pageNumber").asInt(), response.getBody());
        assertEquals(size, data.get("pageSize").asInt(), response.getBody());
        assertTrue(data.get("items").isArray(), response.getBody());
        var actualIds = new ArrayList<Long>();
        data.get("items").forEach(item -> actualIds.add(item.get("id").asLong()));
        assertEquals(Arrays.stream(ids).boxed().toList(), actualIds, response.getBody());
        return data;
    }
}
