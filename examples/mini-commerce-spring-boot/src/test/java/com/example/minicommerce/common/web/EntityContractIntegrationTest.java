package com.example.minicommerce.common.web;

import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.starter.web.registry.ExposedEntityRegistry;
import com.example.minicommerce.customer.entity.Customer;
import com.example.minicommerce.customer.entity.CustomerLevel;
import com.example.minicommerce.merchant.entity.MerchantArticle;
import com.example.minicommerce.merchant.entity.Merchant;
import com.example.minicommerce.merchant.entity.MerchantProduct;
import com.example.minicommerce.merchant.entity.MerchantWxConfig;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.order.entity.OrderItem;
import com.jayway.jsonpath.JsonPath;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.entloom.ddl.api.MetadataLoader ddlMetadataLoader;

    @Test
    void 商户文章仅凭正文业务语义推导文本列() {
        com.entloom.ddl.api.DdlEntityMetadata article = ddlMetadataLoader.load(
                new com.entloom.ddl.api.MetadataLoadRequest(java.util.List.of(), java.util.List.of(MerchantArticle.class)))
            .stream().filter(entity -> entity.entityClassName().equals(MerchantArticle.class.getName())).findFirst().orElseThrow();
        com.entloom.ddl.api.DdlFieldMetadata content = article.fields().stream()
            .filter(field -> field.fieldName().equals("content")).findFirst().orElseThrow();
        assertEquals("text", new com.entloom.ddl.core.MysqlTypeMapper().toSqlType(content));
        assertEquals(-1, content.textCapacity().businessMaxLength());
        assertEquals(-1, content.textCapacity().physicalLength());
        com.entloom.ddl.api.DdlFieldMetadata title = article.fields().stream()
            .filter(field -> field.fieldName().equals("title")).findFirst().orElseThrow();
        assertEquals("varchar(200)", new com.entloom.ddl.core.MysqlTypeMapper().toSqlType(title));
    }


    @Test
    void 实体只从业务包注册且Http默认开放并遵循实体禁用配置() {
        assertEquals(Set.of(com.example.minicommerce.customer.entity.CustomerBankCard.class, Customer.class, CustomerLevel.class, Order.class, OrderItem.class,
                Merchant.class, MerchantProduct.class, MerchantArticle.class, MerchantWxConfig.class),
            entityMetaRegistry.getEntityMetas().stream().map(EntityMeta::getEntityType).collect(Collectors.toSet()));
        assertEquals(MerchantProduct.class, exposedEntities.resolveOrThrow("merchantProduct"));
        assertEquals(CustomerLevel.class, exposedEntities.resolveOrThrow("customerLevel"));
        assertThrows(CrudException.class, () -> exposedEntities.resolveOrThrow("merchantArticle"));
        assertEquals(Customer.class, exposedEntities.resolveOrThrow("customer"));
        assertEquals(Order.class, exposedEntities.resolveOrThrow("order"));
        assertThrows(CrudException.class, () -> exposedEntities.resolveOrThrow("merchantOrder"));
        assertEquals(Merchant.class, exposedEntities.resolveOrThrow("merchant"));
        assertThrows(
            CrudException.class,
            () -> exposedEntities.resolveOrThrow("orderItem"));
        assertThrows(
            CrudException.class,
            () -> exposedEntities.resolveOrThrow(OrderItem.class.getName()));
    }

    @Test
    void 商家主数据建表且三处外键均指向已注册商家实体() {
        assertEquals("merchant", entityMetaRegistry.getEntityMeta(Merchant.class).getTable());
        var merchant = ddlMetadataLoader.load(new com.entloom.ddl.api.MetadataLoadRequest(
                java.util.List.of(), java.util.List.of(Merchant.class)))
            .stream().filter(entity -> entity.entityClassName().equals(Merchant.class.getName()))
            .findFirst().orElseThrow();
        assertEquals(Set.of("id", "name", "legalRepresentativeName", "legalRepresentativePhone"), merchant.fields().stream()
            .map(com.entloom.ddl.api.DdlFieldMetadata::fieldName).collect(Collectors.toSet()));
        for (Class<?> source : java.util.List.of(Order.class, MerchantProduct.class, MerchantArticle.class)) {
            var edges = entityMetaRegistry.getRelationGraph(source).outgoingOf(source).stream()
                .filter(edge -> edge.getToEntity() == Merchant.class).toList();
            assertEquals(1, edges.size());
            assertEquals("merchantId", edges.get(0).getFromField());
            assertEquals("id", edges.get(0).getToField());
        }
    }

    @Test
    void 订单结构属性复用已有关系生成展开映射且不映射数据库列() throws Exception {
        for (String name : java.util.List.of("customer", "orderItemList")) {
            var field = Order.class.getDeclaredField(name);
            assertNotNull(field.getAnnotation(com.entloom.meta.annotations.EntField.class));
            assertNull(field.getAnnotation(com.entloom.meta.annotations.EntRelation.class));
            assertNull(field.getAnnotation(com.entloom.crud.annotations.EntCrudField.class));
        }
        assertNull(Order.class.getDeclaredField("customerId")
            .getAnnotation(com.entloom.crud.annotations.EntCrudField.class));
        var relations = entityMetaRegistry.getRelationGraph(Order.class).outgoingOf(Order.class);
        var customer = relations.stream().filter(edge -> "customer".equals(edge.getRelationField())).toList();
        assertEquals(1, customer.size());
        assertEquals("customerId", customer.getFirst().getFromField());
        assertEquals("id", customer.getFirst().getToField());
        var items = relations.stream().filter(edge -> "orderItemList".equals(edge.getRelationField())).toList();
        assertEquals(1, items.size());
        assertEquals("id", items.getFirst().getFromField());
        assertEquals("orderId", items.getFirst().getToField());
        var order = ddlMetadataLoader.load(new com.entloom.ddl.api.MetadataLoadRequest(
                java.util.List.of(), java.util.List.of(Order.class)))
            .stream().filter(entity -> entity.entityClassName().equals(Order.class.getName()))
            .findFirst().orElseThrow();
        assertEquals(Set.of("id", "customerId", "merchantId", "status", "totalAmount", "createdAt"),
            order.fields().stream().filter(com.entloom.ddl.api.DdlFieldMetadata::persisted)
                .map(com.entloom.ddl.api.DdlFieldMetadata::fieldName).collect(Collectors.toSet()));
    }

    @Test
    void 客户身份按主键限定客户资料范围() throws Exception {
        jdbc.update("delete from customer");
        jdbc.update("insert into customer(id, display_name, email, merchant_id) values "
            + "(301, '客户甲', 'a@example.com', 1001), (302, '客户乙', 'b@example.com', 2001)");
        mvc.perform(post("/api/ent-crud/customer/detail").header("Authorization", "Bearer demo-customer-a")
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":301}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.item.id").value(301));
        mvc.perform(post("/api/ent-crud/customer/detail").header("Authorization", "Bearer demo-customer-a")
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":302}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void 客户基础Crud保留创建更新读取和删除拒绝() throws Exception {
        mvc.perform(post("/api/ent-crud/customer/create").header("Authorization", "Bearer demo-platform-admin").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"payload":{"displayName":"缺少邮箱"}}
                    """))
            .andExpect(status().isBadRequest());
        String created = mvc.perform(post("/api/ent-crud/customer/create").header("Authorization", "Bearer demo-platform-admin").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"payload":{"displayName":"配置清理客户","email":"cleanup@example.com"}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").isNumber())
            .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.data.id")).longValue();
        mvc.perform(post("/api/ent-crud/customer/update").header("Authorization", "Bearer demo-platform-admin").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"payload":{"id":%d,"displayName":"已更新客户"}}
                    """.formatted(id)))
            .andExpect(status().isOk());
        mvc.perform(post("/api/ent-crud/customer/detail").header("Authorization", "Bearer demo-platform-admin").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"id":%d}
                    """.formatted(id)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.item.displayName").value("已更新客户"));
        mvc.perform(post("/api/ent-crud/customer/delete").header("Authorization", "Bearer demo-platform-admin").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"payload":{"id":%d}}
                    """.formatted(id)))
            .andExpect(status().isForbidden());
    }

    @Test
    void 文档注册统一订单和明细关系() throws Exception {
        mvc.perform(get("/api/ent-doc/contract"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[*].resourceCode",
                org.hamcrest.Matchers.containsInAnyOrder("customerBankCard", "customer", "customerLevel", "order", "orderItem",
                    "merchant", "merchantProduct", "merchantArticle", "merchantWxConfig")))
            .andExpect(jsonPath("$.entities[?(@.resourceCode == 'merchantProduct')].fields[*].property",
                org.hamcrest.Matchers.containsInAnyOrder("id", "merchantId", "name", "price", "active", "version")))
            .andExpect(jsonPath("$.entities[?(@.resourceCode == 'order')].fields[*].property",
                org.hamcrest.Matchers.containsInAnyOrder("id", "customerId", "merchantId", "customer", "status",
                    "totalAmount", "createdAt", "orderItemList")))
            .andExpect(jsonPath("$.entities[?(@.resourceCode == 'orderItem')].fields[*].property",
                org.hamcrest.Matchers.containsInAnyOrder("id", "orderId", "merchantProductId", "productName", "unitPrice", "quantity", "lineAmount")));
    }
}
