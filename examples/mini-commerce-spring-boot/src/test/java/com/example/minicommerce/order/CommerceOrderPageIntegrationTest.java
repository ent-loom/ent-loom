package com.example.minicommerce.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.junit.jupiter.api.Assertions.*;

/** 随机端口真实 HTTP，验证同一订单资源的双身份范围、按需展开与统一 CRUD 协议。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.config.import=classpath:demo.yml,classpath:crud-policies.yml",
    "spring.datasource.url=${COMMERCE_TEST_JDBC_URL:jdbc:h2:mem:commerce-order-page;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${COMMERCE_TEST_JDBC_USER:sa}",
    "spring.datasource.password=${COMMERCE_TEST_JDBC_PASSWORD:}"
})
class CommerceOrderPageIntegrationTest {
    private static final String BASE = "/api/ent-crud/commerceOrder/";
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @BeforeEach
    void 准备交叉客户商家和订单明细() {
        jdbc.update("delete from commerce_order_item");
        jdbc.update("delete from commerce_order");
        jdbc.update("delete from customer");
        jdbc.update("insert into customer(id,display_name,email,merchant_id) values "
            + "(301,'客户甲','a@example.com',1001),(302,'客户乙','b@example.com',2001)");
        jdbc.update("""
            insert into commerce_order(id, customer_id, merchant_id, status, total_amount, created_at) values
            (501, 301, 1001, 'CREATED', 10, '2026-10-07 10:00:00'),
            (502, 301, 2001, 'CREATED', 20, '2026-10-07 10:00:00'),
            (503, 302, 1001, 'CREATED', 30, '2026-10-07 10:01:00'),
            (504, 301, 1001, 'CREATED', 40, '2026-10-07 10:02:00'),
            (505, 302, 2001, 'CREATED', 50, '2026-10-07 10:03:00')
            """);
        jdbc.update("""
            insert into commerce_order_item(id,order_id,merchant_product_id,product_name,unit_price,quantity,line_amount) values
            (601,501,101,'甲商品',2,2,4),(602,501,102,'乙商品',3,2,6),
            (603,502,201,'另一商家商品',20,1,20),(604,503,103,'另一客户商品',30,1,30)
            """);
    }

    @ParameterizedTest
    @CsvSource({"demo-customer-a,3,504,502,501", "demo-merchant-a,3,504,503,501"})
    void 同一通用分页入口按可信身份限制范围(String token, long total, long a, long b, long c) throws Exception {
        JsonNode data = assertPage(post("page", token, "{}"), total, 1, 20, a, b, c);
        JsonNode item = data.get("items").get(0);
        assertEquals("CREATED", item.get("status").asText());
        assertTrue(item.hasNonNull("createdAt"));
        assertFalse(item.has("customer"));
        assertFalse(item.has("orderItemList"));
    }

    @ParameterizedTest
    @CsvSource({"demo-customer-b,505,503", "demo-merchant-b,505,502"})
    void 另一账号只读取各自范围(String token, long a, long b) throws Exception {
        assertPage(post("page", token, "{}"), 2, 1, 20, a, b);
    }

    @ParameterizedTest
    @CsvSource({"demo-customer-a,merchantId,1001", "demo-merchant-a,customerId,301"})
    void 额外筛选和分页总数始终与身份范围取交集(String token, String field, long id) throws Exception {
        String request = "{\"options\":{\"filter\":{\"%s\":%d},\"page\":%d,\"limit\":1}}";
        assertPage(post("page", token, request.formatted(field, id, 1)), 2, 1, 1, 504);
        assertPage(post("page", token, request.formatted(field, id, 2)), 2, 2, 1, 501);
        assertPage(post("page", token, "{\"options\":{\"filter\":{\"%s\":9999}}}".formatted(field)), 0, 1, 20);
    }

    @ParameterizedTest
    @CsvSource({"demo-customer-a,3,504,502", "demo-customer-b,2,505,503",
        "demo-merchant-a,3,504,503", "demo-merchant-b,2,505,502"})
    void 创建时间和主键倒序稳定分页并保留范围内总数(String token, long total, long first, long second) throws Exception {
        assertPage(post("page", token, "{\"options\":{\"page\":1,\"limit\":1}}"), total, 1, 1, first);
        assertPage(post("page", token, "{\"options\":{\"page\":2,\"limit\":1}}"), total, 2, 1, second);
        assertPage(post("page", token, "{\"options\":{\"page\":4,\"limit\":1}}"), total, 4, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "list", "detail", "findOne"})
    void 缺失或无效凭证及伪造请求头均不能使用旧演示主体读取订单(String operation) {
        String body = operation.equals("detail") ? "{\"id\":501}" : "{\"options\":{\"filter\":{\"id\":501}}}";
        assertStatus(post(operation, null, body), 403);
        assertStatus(post(operation, "unknown", body), 403);
        assertStatus(post(operation, null, body, forgedHeaders()), 403);
    }

    @ParameterizedTest
    @CsvSource({"demo-customer-a,customerId,302", "demo-merchant-a,merchantId,2001"})
    void 请求归属筛选取可信范围交集且请求头不能切换身份(String token, String field, long id) throws Exception {
        assertPage(post("page", token, "{\"options\":{\"filter\":{\"%s\":%d}}}".formatted(field, id)), 0, 1, 20);
        assertPage(post("page", token, "{\"options\":{\"filter\":{\"totalAmount\":40}}}"), 1, 1, 20, 504);
        if (token.contains("customer")) assertPage(post("page", token, "{}", forgedHeaders()), 3, 1, 20, 504, 502, 501);
        else assertPage(post("page", token, "{}", forgedHeaders()), 3, 1, 20, 504, 503, 501);
    }

    @ParameterizedTest
    @CsvSource({"demo-customer-a,503", "demo-customer-b,501", "demo-merchant-a,502", "demo-merchant-b,501"})
    void 跨归属详情不可见且唯一查询返回空(String token, long id) throws Exception {
        String body = "{\"options\":{\"filter\":{\"id\":%d}}}".formatted(id);
        assertStatus(post("detail", token, "{\"id\":%d}".formatted(id)), 404);
        assertTrue(data(post("findOne", token, body)).get("item").isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"demo-customer-a", "demo-merchant-a"})
    void 通用详情只返回值字段且不自动填充结构属性(String token) throws Exception {
        JsonNode item = data(post("detail", token,
            "{\"id\":501}")).get("item");
        assertEquals(501, item.get("id").asLong());
        assertEquals(301, item.get("customerId").asLong());
        assertEquals(1001, item.get("merchantId").asLong());
        assertFalse(item.has("customer"));
        assertFalse(item.has("orderItemList"));
        item = data(post("detail", token,
            "{\"id\":501,\"options\":{\"selectFields\":[\"id\"]}}")).get("item");
        assertEquals(1, item.size());
        assertEquals(501, item.get("id").asLong());
        assertStatus(post("list", token, "{\"entityCodes\":[\"commerceOrder\",\"commerceOrderItem\"]}"), 404);
        assertStatus(post("detail", token, "{\"id\":501,\"options\":{\"resultMode\":\"ENTITY\"}}"), 403);
        assertStatus(post("page/custom", token, "{}"), 403);
        assertStatus(post("page", token, "{\"options\":{\"selectFields\":[\"customer.email\"]}}"), 403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"demo-customer-a", "demo-merchant-a"})
    void 未配置EXPAND时通用查询仅展开请求的候选关系字段(String token) throws Exception {
        String body = """
            {"options":{"filter":{"id":501},"expandRelations":["customer","orderItemList"]}}
            """;
        for (String operation : new String[] {"page", "list", "findOne", "detail"}) {
            String actualBody = operation.equals("detail")
                ? "{\"id\":501,\"options\":{\"expandRelations\":[\"customer\",\"orderItemList\"]}}" : body;
            JsonNode result = data(post(operation, token, actualBody));
            JsonNode item = operation.equals("page") || operation.equals("list")
                ? result.get("items").get(0) : result.get("item");
            assertEquals(501, item.get("id").asLong());
            assertEquals(301, item.get("customer").get("id").asLong());
            assertEquals("客户甲", item.get("customer").get("displayName").asText());
            assertEquals("a@example.com", item.get("customer").get("email").asText());
            assertEquals(2, item.get("orderItemList").size());
            item.get("orderItemList").forEach(line -> assertEquals(501, line.get("orderId").asLong()));
            if (operation.equals("page")) assertEquals(1, result.get("page").get("total").asLong());

            String onlyItemsBody = operation.equals("detail")
                ? "{\"id\":501,\"options\":{\"expandRelations\":[\"orderItemList\"]}}"
                : "{\"options\":{\"filter\":{\"id\":501},\"expandRelations\":[\"orderItemList\"]}}";
            JsonNode onlyItems = data(post(operation, token, onlyItemsBody));
            JsonNode withoutCustomer = operation.equals("page") || operation.equals("list")
                ? onlyItems.get("items").get(0) : onlyItems.get("item");
            assertFalse(withoutCustomer.has("customer"));
            assertEquals(2, withoutCustomer.get("orderItemList").size());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"demo-customer-a", "demo-merchant-a"})
    void 未授权关系及嵌套展开仍拒绝(String token) {
        for (String operation : new String[] {"page", "list", "findOne", "detail"}) {
            for (String relation : new String[] {"merchantId", "customer.address", "orderItemList.merchantProduct"}) {
                String body = operation.equals("detail")
                    ? "{\"id\":501,\"options\":{\"expandRelations\":[\"%s\"]}}".formatted(relation)
                    : "{\"options\":{\"filter\":{\"id\":501},\"expandRelations\":[\"%s\"]}}".formatted(relation);
                assertStatus(post(operation, token, body), 403);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"demo-customer-a,503", "demo-merchant-a,502"})
    void 显式展开仍遵循根订单范围且缺失身份拒绝(String token, long id) throws Exception {
        String body = """
            {"options":{"filter":{"id":%d},"expandRelations":["customer","orderItemList"]}}
            """.formatted(id);
        String detailBody = "{\"id\":%d,\"options\":{\"expandRelations\":[\"customer\",\"orderItemList\"]}}".formatted(id);
        assertStatus(post("detail", token, detailBody), 404);
        assertTrue(data(post("findOne", token, body)).get("item").isNull());
        for (String operation : new String[] {"page", "list", "findOne", "detail"}) {
            assertStatus(post(operation, null, operation.equals("detail") ? detailBody : body), 403);
        }
        assertTrue(data(post("list", token, body)).get("items").isEmpty());
        assertPage(post("page", token, body), 0, 1, 20);
    }

    @Test
    void 展开区分无明细与客户缺失且不存在订单遵循唯一查询语义() throws Exception {
        jdbc.update("delete from customer where id=301");
        String body = """
            {"id":504,"options":{"nullFieldMode":"INCLUDE","expandRelations":["customer","orderItemList"]}}
            """;
        JsonNode item = data(post("detail", "demo-customer-a", body)).get("item");
        assertTrue(item.path("customer").isNull());
        assertTrue(item.get("orderItemList").isArray());
        assertTrue(item.get("orderItemList").isEmpty());
        assertFalse(data(post("detail", "demo-customer-a", body.replace("\"nullFieldMode\":\"INCLUDE\",", "")))
            .get("item").has("customer"));
        String missing = body.replace("504", "9999");
        assertStatus(post("detail", "demo-customer-a", missing), 404);
        assertTrue(data(post("findOne", "demo-customer-a",
            "{\"options\":{\"filter\":{\"id\":9999},\"expandRelations\":[\"customer\",\"orderItemList\"]}}"))
            .get("item").isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"demo-customer-a", "demo-merchant-a"})
    void 一对多展开不放大根分页且关联只来自本页订单(String token) throws Exception {
        JsonNode result = assertPage(post("page", token, """
            {"options":{"page":2,"limit":2,"sorts":[{"field":"id","direction":"DESC"}],
              "expandRelations":["customer","orderItemList"]}}
            """), 3, 2, 2, 501);
        assertEquals(2, result.get("items").get(0).get("orderItemList").size());
        JsonNode uncounted = data(post("page", token, """
            {"options":{"page":1,"limit":2,"countMode":"NONE",
              "expandRelations":["orderItemList"]}}
            """));
        assertEquals(2, uncounted.get("items").size());
        assertTrue(uncounted.get("page").get("hasNext").asBoolean());
        uncounted.get("items").forEach(order -> order.get("orderItemList").forEach(line ->
            assertEquals(order.get("id").asLong(), line.get("orderId").asLong())));
    }

    @Test
    void 有计数与无计数分页保持相同归属() throws Exception {
        String options = "{\"options\":{\"page\":1,\"limit\":1,\"sorts\":[{\"field\":\"id\",\"direction\":\"ASC\"}]%s}}";
        assertPage(post("page", "demo-merchant-a", options.formatted("")), 3, 1, 1, 501);
        JsonNode page = data(post("page", "demo-merchant-a", options.formatted(",\"countMode\":\"NONE\"")));
        assertTrue(page.get("page").get("hasNext").asBoolean());
        assertFalse(page.get("page").get("totalKnown").asBoolean());
        assertEquals(501, page.get("items").get(0).get("id").asLong());
        assertFalse(page.get("items").get(0).has("orderItemList"));
        page = data(post("list", "demo-customer-a", "{\"options\":{\"filter\":{\"merchantId\":1001}}}"));
        assertEquals(2, page.get("items").size());
    }

    @Test
    void 默认排序优先创建时间而显式排序优先调用方且同时间按主键倒序() throws Exception {
        jdbc.update("update commerce_order set created_at='2026-10-08 10:00:00' where id=501");
        assertPage(post("page", "demo-customer-a", "{}"), 3, 1, 20, 501, 504, 502);
        assertPage(post("page", "demo-customer-a",
            "{\"options\":{\"sorts\":[{\"field\":\"id\",\"direction\":\"DESC\"}]}}"),
            3, 1, 20, 504, 502, 501);
        jdbc.update("update commerce_order set created_at='2026-10-08 10:00:00' where id=502");
        assertPage(post("page", "demo-customer-a", "{}"), 3, 1, 20, 502, 501, 504);
    }

    @ParameterizedTest
    @CsvSource({"demo-customer-a,502", "demo-merchant-a,503"})
    void 省略SORT复用返回字段并保留归属稳定分页和上限(String token, long middle) throws Exception {
        for (String field : new String[] {"id", "totalAmount"}) {
            assertPage(post("page", token,
                "{\"options\":{\"sorts\":[{\"field\":\"%s\",\"direction\":\"DESC\"}]}}".formatted(field)),
                3, 1, 20, 504, middle, 501);
        }
        assertPage(post("page", token,
            "{\"options\":{\"sorts\":[{\"field\":\"createdAt\",\"direction\":\"DESC\"}]}}"),
            3, 1, 20, 504, token.contains("customer") ? 501 : middle,
            token.contains("customer") ? middle : 501);
        assertPage(post("page", token,
            "{\"options\":{\"sorts\":[{\"field\":\"createdAt\",\"direction\":\"DESC\"}],\"page\":2,\"limit\":1}}"),
            3, 2, 1, token.contains("customer") ? 501 : middle);
        assertStatus(post("page", token, "{\"options\":{\"limit\":1001}}"), 400);
        assertStatus(post("page", token,
            "{\"options\":{\"sorts\":[{\"field\":\"customer.email\",\"direction\":\"DESC\"}]}}"), 403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update", "delete", "action/unknown"})
    void 查询策略不授予订单普通写入或未知动作(String operation) {
        assertStatus(post(operation, "demo-customer-a", "{\"payload\":{\"id\":501,\"customerId\":302}}"),
            operation.equals("action/unknown") ? 404 : 403);
    }

    @Test
    void 明细仍关闭独立Http且旧业务查询路由已删除() {
        assertStatus(post("/api/ent-crud/commerceOrderItem/list", "demo-customer-a", "{}"), 404);
        assertStatus(post("/api/consumer/orders/page", "demo-customer-a", "{}"), 404);
        assertStatus(post("/api/merchant/orders/page", "demo-merchant-a", "{}"), 404);
    }

    private ResponseEntity<String> post(String operation, String token, String body) {
        return post(operation, token, body, new HttpHeaders());
    }

    private ResponseEntity<String> post(String operation, String token, String body, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        String route = operation.startsWith("/") ? operation : BASE + operation;
        return http.exchange(route, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private HttpHeaders forgedHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Customer-Id", "302");
        headers.set("X-Merchant-Id", "2001");
        headers.set("X-Subject-Id", "customer-user-b");
        headers.set("X-Access-Entry", "management");
        return headers;
    }

    private void assertStatus(ResponseEntity<String> response, int status) {
        assertEquals(status, response.getStatusCode().value(), response.getBody());
    }

    private JsonNode data(ResponseEntity<String> response) throws Exception {
        assertStatus(response, 200);
        return json.readTree(response.getBody()).get("data");
    }

    private JsonNode assertPage(ResponseEntity<String> response, long total, int number, int limit, long... ids)
        throws Exception {
        JsonNode data = data(response);
        JsonNode page = data.get("page");
        assertEquals(total, page.get("total").asLong(), response.getBody());
        assertTrue(page.get("totalKnown").asBoolean(), response.getBody());
        assertEquals(number, page.get("page").asInt(), response.getBody());
        assertEquals(limit, page.get("limit").asInt(), response.getBody());
        var actual = new ArrayList<Long>();
        data.get("items").forEach(item -> actual.add(item.get("id").asLong()));
        assertEquals(Arrays.stream(ids).boxed().toList(), actual, response.getBody());
        return data;
    }
}
