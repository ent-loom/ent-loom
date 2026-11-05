package com.example.minicommerce.common.access;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.core.capability.query.engine.AuthorizedQueryExecutor;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.capability.command.patch.NormalizedUpdatePatch;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.example.minicommerce.customer.entity.Customer;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.customer.entity.CustomerAddress;
import com.example.minicommerce.merchant.entity.MerchantArticle;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 真实主 YAML 与 JDBC 验证：被访问实体和当前归属身份是两个不同概念。 */
@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:commerce-owned;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
class OwnedEntityIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthorizedQueryExecutor queries;
    @Autowired BusinessIdentityRegistry identities;
    @Autowired EntityMetaRegistry entities;
    @Autowired MockMvc mvc;

    @BeforeEach
    void seed() {
        jdbc.update("delete from customer_address");
        jdbc.update("delete from merchant_article");
        jdbc.update("""
            insert into customer_address(id, customer_id, recipient, phone, address) values
            (1, 301, '顾客甲', '13800000001', '甲的地址'),
            (2, 302, '顾客乙', '13800000002', '乙的地址')
            """);
        jdbc.update("""
            insert into merchant_article(id, merchant_id, title, content) values
            (1, 1001, '商户甲的文章', '甲的正文'),
            (2, 2001, '商户乙的文章', '乙的正文')
            """);
    }

    @Test
    void 关系字段缺省推导保护订单多重归属并进入实际Patch() {
        var address = entities.getEntityMeta(CustomerAddress.class);
        assertTrue(address.resolveFieldMeta("customerId").isScopeField());
        assertFalse(address.resolveFieldMeta("customerId").isWritable());
        assertTrue(entities.getEntityMeta(MerchantArticle.class).resolveFieldMeta("merchantId").isScopeField());
        assertTrue(entities.getEntityMeta(Order.class).resolveFieldMeta("customerId").isScopeField());
        assertTrue(entities.getEntityMeta(Order.class).resolveFieldMeta("merchantId").isScopeField());
        assertFalse(entities.getEntityMeta(Customer.class).resolveFieldMeta("id").isScopeField());
        assertFalse(entities.getEntityMeta(Order.class).resolveFieldMeta("customerId").isWritable());
        for (Class<?> type : List.of(CustomerAddress.class, Order.class)) {
            var meta = entities.getEntityMeta(type);
            var patch = new DefaultCommandPayloadBinder().bindUpdatePatch(Map.of("id", 1L, "customerId", 302L), type, meta);
            assertThrows(ValidationException.class, () -> NormalizedUpdatePatch.from(patch, meta));
        }
        var order = entities.getEntityMeta(Order.class);
        var merchantPatch = new DefaultCommandPayloadBinder().bindUpdatePatch(Map.of("id", 1L, "merchantId", 2001L),
            Order.class, order);
        assertThrows(ValidationException.class, () -> NormalizedUpdatePatch.from(merchantPatch, order));
    }

    @Test
    void 地址是实体而顾客是访问身份和归属者() {
        var customerA = visitor("customer", "customer-user-a", "301", "consumer");
        var customerB = visitor("customer", "customer-user-b", "302", "consumer");
        assertEquals(List.of(301L), list(CustomerAddress.class, customerA).stream().map(row -> row.longValue("customerId")).toList());
        assertEquals(List.of(302L), list(CustomerAddress.class, customerB).stream().map(row -> row.longValue("customerId")).toList());
        assertThrows(PermissionDeniedException.class, () -> list(CustomerAddress.class,
            visitor("merchant", "merchant-user-a", "1001", "merchant")));
    }

    @Test
    void 文章是实体而商户是访问身份和归属者() {
        var merchantA = visitor("merchant", "merchant-user-a", "1001", "merchant");
        var merchantB = visitor("merchant", "merchant-user-b", "2001", "merchant");
        assertEquals(List.of(1001L), list(MerchantArticle.class, merchantA).stream().map(row -> row.longValue("merchantId")).toList());
        assertEquals(List.of(2001L), list(MerchantArticle.class, merchantB).stream().map(row -> row.longValue("merchantId")).toList());
        assertThrows(PermissionDeniedException.class, () -> list(MerchantArticle.class,
            visitor("customer", "customer-user-a", "301", "consumer")));
    }

    @ParameterizedTest
    @CsvSource({
        "customerAddress, demo-customer-a, customerId, 301, 1",
        "customerAddress, demo-customer-b, customerId, 302, 2",
        "merchantArticle, demo-merchant-a, merchantId, 1001, 1",
        "merchantArticle, demo-merchant-b, merchantId, 2001, 2"
    })
    void 普通归属实体无需业务Controller即可分页并读取本人详情(String entity, String token,
        String ownerField, long owner, long id) throws Exception {
        mvc.perform(post("/api/ent-crud/" + entity + "/page").header("Authorization", "Bearer " + token)
                .header("X-Customer-Id", "999").header("X-Merchant-Id", "999")
                .contentType(MediaType.APPLICATION_JSON).content("{\"options\":{\"page\":1,\"limit\":1}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total").value(1))
            .andExpect(jsonPath("$.data.items[0]." + ownerField).value(owner));
        mvc.perform(post("/api/ent-crud/" + entity + "/detail").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"options\":{\"filter\":{\"id\":" + id + "}}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.item." + ownerField).value(owner));
        long otherId = id == 1 ? 2 : 1;
        mvc.perform(post("/api/ent-crud/" + entity + "/detail").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"options\":{\"filter\":{\"id\":" + otherId + "}}}"))
            .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({"customerAddress, demo-merchant-a", "merchantArticle, demo-customer-a"})
    void 通用入口不因实体已暴露而授予其他身份读取权限(String entity, String token) throws Exception {
        mvc.perform(post("/api/ent-crud/" + entity + "/list").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    private VisitorContext visitor(String code, String subjectId, String identityId, String entry) {
        var subject = new AccessSubject(subjectId, AccessSubjectType.HUMAN);
        return VisitorContext.authenticated(subject, identities.resolve(subject, code, identityId), entry, null, null);
    }

    private List<CrudRecord> list(Class<?> entity, VisitorContext visitor) {
        return queries.list(visitor, QuerySpec.<CrudRecord>builder().rootType(entity).resultType(CrudRecord.class)
            .op(QueryOperation.LIST).limit(20).build());
    }
}
