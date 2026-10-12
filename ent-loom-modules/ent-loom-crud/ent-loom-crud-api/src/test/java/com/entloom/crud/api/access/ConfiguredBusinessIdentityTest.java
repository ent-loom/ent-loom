package com.entloom.crud.api.access;

import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfiguredBusinessIdentityTest {
    @Test
    void 身份快照保留声明和值类型且同类身份按码隔离() {
        BusinessIdentityDefinition customer = new BusinessIdentityDefinition("customer", "customerId", BusinessIdentityIdType.LONG);
        BusinessIdentityDefinition merchant = new BusinessIdentityDefinition("merchant", "merchantId", BusinessIdentityIdType.LONG);
        ConfiguredBusinessIdentity first = customer.snapshot("301");
        ConfiguredBusinessIdentity second = customer.snapshot("302");
        assertEquals("customer", first.getIdentityCode());
        assertEquals("301", first.getIdentityId());
        assertEquals(301L, first.read("customerId"));
        assertEquals(302L, second.getIdValue());
        assertEquals(first.getClass(), merchant.snapshot("301").getClass());
        assertNotEquals(first, merchant.snapshot("301"));
        assertEquals(first, customer.snapshot("301"));
        assertEquals(first.hashCode(), customer.snapshot("301").hashCode());
        assertThrows(IllegalArgumentException.class, () -> first.read("merchantId"));
        VisitorContext visitor = VisitorContext.authenticated(new AccessSubject("user-a", AccessSubjectType.HUMAN), first, "consumer", null, null);
        assertEquals(first, AccessContext.from(visitor, "customer").getIdentity());
        assertThrows(IllegalStateException.class, () -> AccessContext.from(visitor, "merchant"));
    }

    @Test
    void 显式标量类型统一转换且文本标识保持原意() {
        assertEquals("00301", new BusinessIdentityDefinition("external", "externalId", BusinessIdentityIdType.STRING)
            .snapshot("00301").getIdValue());
        assertEquals(1, new BusinessIdentityDefinition("member", "memberId", BusinessIdentityIdType.INTEGER).snapshot("1").getIdValue());
        UUID id = UUID.randomUUID();
        assertEquals(id, new BusinessIdentityDefinition("device", "deviceId", BusinessIdentityIdType.UUID).snapshot(id.toString()).getIdValue());
    }

    @Test
    void 非法定义及非规范标识拒绝构造() {
        assertThrows(NullPointerException.class, () -> new BusinessIdentityDefinition("customer", "customerId", null));
        assertThrows(IllegalArgumentException.class, () -> new BusinessIdentityDefinition(" customer", "customerId", BusinessIdentityIdType.LONG));
        assertThrows(IllegalArgumentException.class, () -> new BusinessIdentityDefinition("customer", "customer-id", BusinessIdentityIdType.LONG));
        BusinessIdentityDefinition customer = new BusinessIdentityDefinition("customer", "customerId", BusinessIdentityIdType.LONG);
        for (String id : Arrays.asList(null, "", " ", "1 ", "01", "+1", "0", "-1", "bad", "9223372036854775808")) {
            assertThrows(IllegalArgumentException.class, () -> customer.snapshot(id));
        }
        BusinessIdentityDefinition device = new BusinessIdentityDefinition("device", "deviceId", BusinessIdentityIdType.UUID);
        assertThrows(IllegalArgumentException.class, () -> device.snapshot("1-1-1-1-1"));
    }
}
