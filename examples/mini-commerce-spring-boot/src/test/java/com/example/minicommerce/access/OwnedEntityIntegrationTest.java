package com.example.minicommerce.access;

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
import com.example.minicommerce.order.entity.MerchantOrder;
import com.example.minicommerce.customer.entity.CustomerAddress;
import com.example.minicommerce.merchant.entity.MerchantPoster;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 真实主 YAML 与 JDBC 验证：被访问实体和当前归属身份是两个不同概念。 */
@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:commerce-owned;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password="
})
class OwnedEntityIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthorizedQueryExecutor queries;
    @Autowired BusinessIdentityRegistry identities;
    @Autowired EntityMetaRegistry entities;

    @BeforeEach
    void seed() {
        jdbc.update("delete from customer_address");
        jdbc.update("delete from merchant_poster");
        jdbc.update("""
            insert into customer_address(id, customer_id, recipient, phone, address) values
            (1, 301, '顾客甲', '13800000001', '甲的地址'),
            (2, 302, '顾客乙', '13800000002', '乙的地址')
            """);
        jdbc.update("""
            insert into merchant_poster(id, merchant_id, title, content) values
            (1, 1001, '商户甲的文章', '甲的正文'),
            (2, 2001, '商户乙的文章', '乙的正文')
            """);
    }

    @Test
    void 全局归属字段保护与存量例外均进入实际元数据和Patch() {
        var address = entities.getEntityMeta(CustomerAddress.class);
        assertTrue(address.resolveFieldMeta("customerId").isScopeField());
        assertFalse(address.resolveFieldMeta("customerId").isWritable());
        assertTrue(entities.getEntityMeta(MerchantPoster.class).resolveFieldMeta("merchantId").isScopeField());
        assertTrue(entities.getEntityMeta(MerchantOrder.class).resolveFieldMeta("customerId").isScopeField());
        assertTrue(entities.getEntityMeta(MerchantOrder.class).resolveFieldMeta("merchantId").isScopeField());
        assertFalse(entities.getEntityMeta(Customer.class).resolveFieldMeta("id").isScopeField());
        assertFalse(entities.getEntityMeta(Order.class).resolveFieldMeta("customerId").isScopeField());
        assertTrue(entities.getEntityMeta(Order.class).resolveFieldMeta("customerId").isWritable());
        var patch = new DefaultCommandPayloadBinder().bindUpdatePatch(Map.of("id", 1L, "customerId", 302L),
            CustomerAddress.class, address);
        assertThrows(ValidationException.class, () -> NormalizedUpdatePatch.from(patch, address));
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
        assertEquals(List.of(1001L), list(MerchantPoster.class, merchantA).stream().map(row -> row.longValue("merchantId")).toList());
        assertEquals(List.of(2001L), list(MerchantPoster.class, merchantB).stream().map(row -> row.longValue("merchantId")).toList());
        assertThrows(PermissionDeniedException.class, () -> list(MerchantPoster.class,
            visitor("customer", "customer-user-a", "301", "consumer")));
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
