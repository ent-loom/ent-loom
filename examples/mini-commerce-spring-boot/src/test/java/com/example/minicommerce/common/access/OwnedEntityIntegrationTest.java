package com.example.minicommerce.common.access;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.core.governance.authorization.BusinessAuthorizationService;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.core.capability.query.engine.AuthorizedQueryExecutor;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.capability.command.patch.NormalizedUpdatePatch;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.example.minicommerce.customer.entity.Customer;
import com.example.minicommerce.order.entity.CommerceOrder;
import com.example.minicommerce.customer.entity.CustomerLevel;
import com.example.minicommerce.merchant.entity.MerchantArticle;
import com.example.minicommerce.merchant.entity.MerchantWxSetting;
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
    "spring.config.import=classpath:demo.yml,classpath:crud-policies.yml",
    "spring.datasource.url=jdbc:h2:mem:commerce-owned;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
class OwnedEntityIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthorizedQueryExecutor authorizedQueryExecutor;
    @Autowired BusinessIdentityRegistry businessIdentityRegistry;
    @Autowired EntityMetaRegistry entityMetaRegistry;
    @Autowired MockMvc mvc;
    @Autowired BusinessAuthorizationService authorization;

    @BeforeEach
    void seed() {
        jdbc.update("delete from customer_level");
        jdbc.update("delete from merchant_article");
        jdbc.update("""
            insert into customer_level(id, merchant_id, customer_id, level) values
            (1, 1001, 301, 'STANDARD'),
            (2, 2001, 302, 'GOLD')
            """);
        jdbc.update("""
            insert into merchant_article(id, merchant_id, title, content) values
            (1, 1001, '商户甲的文章', '甲的正文'),
            (2, 2001, '商户乙的文章', '乙的正文')
            """);
    }

    @Test
    void 关系字段缺省推导保护订单多重归属并进入实际Patch() {
        var address = entityMetaRegistry.getEntityMeta(CustomerLevel.class);
        assertTrue(address.resolveFieldMeta("customerId").isScopeField());
        assertTrue(address.resolveFieldMeta("merchantId").isScopeField());
        assertFalse(address.resolveFieldMeta("merchantId").isWritable());
        assertFalse(address.resolveFieldMeta("customerId").isWritable());
        assertTrue(entityMetaRegistry.getEntityMeta(MerchantArticle.class).resolveFieldMeta("merchantId").isScopeField());
        assertTrue(entityMetaRegistry.getEntityMeta(CommerceOrder.class).resolveFieldMeta("customerId").isScopeField());
        assertTrue(entityMetaRegistry.getEntityMeta(CommerceOrder.class).resolveFieldMeta("merchantId").isScopeField());
        assertFalse(entityMetaRegistry.getEntityMeta(Customer.class).resolveFieldMeta("id").isScopeField());
        assertFalse(entityMetaRegistry.getEntityMeta(CommerceOrder.class).resolveFieldMeta("customerId").isWritable());
        for (Class<?> type : List.of(CustomerLevel.class, CommerceOrder.class)) {
            var meta = entityMetaRegistry.getEntityMeta(type);
            var patch = new DefaultCommandPayloadBinder().bindUpdatePatch(Map.of("id", 1L, "customerId", 302L), type, meta);
            assertThrows(ValidationException.class, () -> NormalizedUpdatePatch.from(patch, meta));
        }
        var order = entityMetaRegistry.getEntityMeta(CommerceOrder.class);
        var merchantPatch = new DefaultCommandPayloadBinder().bindUpdatePatch(Map.of("id", 1L, "merchantId", 2001L),
            CommerceOrder.class, order);
        assertThrows(ValidationException.class, () -> NormalizedUpdatePatch.from(merchantPatch, order));
    }

    @Test
    void 会员等级按用户与商家分别限制读取范围() {
        var customerA = visitor("customer", "customer-user-a", "301", "consumer");
        var customerB = visitor("customer", "customer-user-b", "302", "consumer");
        assertEquals(List.of(301L), list(CustomerLevel.class, customerA).stream().map(CustomerLevel::getCustomerId).toList());
        assertEquals(List.of(302L), list(CustomerLevel.class, customerB).stream().map(CustomerLevel::getCustomerId).toList());
        assertEquals(List.of(1001L), list(CustomerLevel.class,
            visitor("merchant", "merchant-user-a", "1001", "merchant")).stream()
            .map(CustomerLevel::getMerchantId).toList());
    }

    @Test
    void 内部微信设置不向商家和客户开放独立授权查询() {
        var merchantA = visitor("merchant", "merchant-user-a", "1001", "merchant");
        var customerA = visitor("customer", "customer-user-a", "301", "consumer");
        for (Class<?> type : List.of(MerchantWxSetting.class)) {
            assertThrows(PermissionDeniedException.class, () -> list(type, merchantA));
            assertThrows(PermissionDeniedException.class, () -> list(type, customerA));
            String resource = entityMetaRegistry.getEntityMeta(type).getResourceDescriptor().getResourceCode();
            for (String action : List.of("view", "create", "update", "delete")) {
                for (var deniedVisitor : List.of(merchantA, customerA)) {
                    assertThrows(PermissionDeniedException.class, () -> authorization.authorize(
                        new AuthorizationRequest(deniedVisitor, new BusinessOperation(resource, action), null)));
                }
            }
            // 验证授权结果，无需为内部微信设置额外创建测试表。
            var admin = visitor("super-admin", "platform-admin", "1", "consumer");
            for (String action : List.of("view", "create", "update", "delete")) {
                assertEquals(AccessDecision.ALLOW, authorization.authorize(new AuthorizationRequest(
                    admin, new BusinessOperation(resource, action), null)).getDecision());
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
        "customerLevel, demo-customer-a, customerId, 301, 1",
        "customerLevel, demo-customer-b, customerId, 302, 2",
        "customerLevel, demo-merchant-a, merchantId, 1001, 1",
        "customerLevel, demo-merchant-b, merchantId, 2001, 2"
    })
    void 普通归属实体无需业务Controller即可分页并读取本人详情(String entity, String token,
        String ownerField, long owner, long id) throws Exception {
        mvc.perform(post("/api/ent-crud/" + entity + "/page").header("Authorization", "Bearer " + token)
                .header("X-Customer-Id", "999").header("X-Merchant-Id", "999")
                .contentType(MediaType.APPLICATION_JSON).content("{\"options\":{\"page\":1,\"limit\":1}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total").value(1))
            .andExpect(jsonPath("$.data.items[0]." + ownerField).value(owner));
        mvc.perform(post("/api/ent-crud/" + entity + "/detail").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + id + "}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.item." + ownerField).value(owner));
        long otherId = id == 1 ? 2 : 1;
        mvc.perform(post("/api/ent-crud/" + entity + "/detail").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + otherId + "}"))
            .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({"merchantArticle, demo-customer-a"})
    void 内部实体通用入口不开放(String entity, String token) throws Exception {
        mvc.perform(post("/api/ent-crud/" + entity + "/list").header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void 银行卡仅本人可维护且商家不能访问() throws Exception {
        jdbc.update("delete from customer_bank_card");
        String created = mvc.perform(post("/api/ent-crud/customerBankCard/create")
                .header("Authorization", "Bearer demo-customer-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"bankName\":\"演示银行\",\"cardLastFour\":\"1234\",\"bindingToken\":\"demo-binding\"}}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.data.id")).longValue();
        assertEquals(301L, jdbc.queryForObject("select customer_id from customer_bank_card where id=?", Long.class, id));
        String payload = "{\"payload\":{\"id\":" + id + ",\"bankName\":\"更新银行\"}}";
        String query = "{\"id\":" + id + "}";
        for (String action : List.of("create", "detail", "update", "delete")) {
            mvc.perform(post("/api/ent-crud/customerBankCard/" + action)
                    .header("Authorization", "Bearer demo-merchant-a").contentType(MediaType.APPLICATION_JSON)
                    .content(action.equals("detail") ? query : payload))
                .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/ent-crud/customerBankCard/detail")
                .header("Authorization", "Bearer demo-customer-b").contentType(MediaType.APPLICATION_JSON).content(query))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/ent-crud/customerBankCard/update")
                .header("Authorization", "Bearer demo-customer-b").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().is4xxClientError());
        mvc.perform(post("/api/ent-crud/customerBankCard/delete")
                .header("Authorization", "Bearer demo-customer-b").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().is4xxClientError());
        mvc.perform(post("/api/ent-crud/customerBankCard/update")
                .header("Authorization", "Bearer demo-customer-a").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isOk());
        mvc.perform(post("/api/ent-crud/customerBankCard/detail")
                .header("Authorization", "Bearer demo-customer-a").contentType(MediaType.APPLICATION_JSON).content(query))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.item.bankName").value("更新银行"));
        mvc.perform(post("/api/ent-crud/customerBankCard/delete")
                .header("Authorization", "Bearer demo-customer-a").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"id\":" + id + "}}"))
            .andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("select count(*) from customer_bank_card", Integer.class));
    }

    @Test
    void 用户不能自行升级而商家只能维护本店等级() throws Exception {
        String payload = "{\"payload\":{\"id\":1,\"level\":\"GOLD\"}}";
        mvc.perform(post("/api/ent-crud/customerLevel/update")
                .header("Authorization", "Bearer demo-customer-a").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/ent-crud/customerLevel/update")
                .header("Authorization", "Bearer demo-merchant-b").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().is4xxClientError());
        assertEquals("STANDARD", jdbc.queryForObject("select level from customer_level where id=1", String.class));
        mvc.perform(post("/api/ent-crud/customerLevel/update")
                .header("Authorization", "Bearer demo-merchant-a").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isOk());
        assertEquals("GOLD", jdbc.queryForObject("select level from customer_level where id=1", String.class));
    }

    private VisitorContext visitor(String code, String subjectId, String identityId, String entry) {
        var subject = new AccessSubject(subjectId, AccessSubjectType.HUMAN);
        return VisitorContext.authenticated(subject, businessIdentityRegistry.resolve(subject, code, identityId), entry, null, null);
    }

    private <T> List<T> list(Class<T> entity, VisitorContext visitor) {
        return authorizedQueryExecutor.list(visitor, QuerySpec.builder(entity)
            .op(QueryOperation.LIST).limit(20).build());
    }
}
