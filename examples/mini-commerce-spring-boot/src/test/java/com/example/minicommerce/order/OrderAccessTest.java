package com.example.minicommerce.order;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 订单业务动作统一验证可信身份、归属、状态与通用写入边界。 */
@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=${COMMERCE_TEST_JDBC_URL:jdbc:h2:mem:commerce-access;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${COMMERCE_TEST_JDBC_USER:sa}", "spring.datasource.password=${COMMERCE_TEST_JDBC_PASSWORD:}"
})
@AutoConfigureMockMvc
class OrderAccessTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @SpyBean CrudGovernanceAuditRecorder auditRecorder;
    @SpyBean com.example.minicommerce.order.service.OrderService orderService;

    @AfterEach
    void resetAudit() { reset(auditRecorder); }

    @BeforeEach
    void prepare() {
        jdbc.update("delete from order_item");
        jdbc.update("delete from `order`");
        jdbc.update("delete from merchant_product");
        jdbc.update("delete from customer");
        jdbc.update("insert into customer(id, display_name, email, merchant_id) values (301, '测试客户', 'customer@example.com', 1001)");
        jdbc.update("insert into merchant_product(id,merchant_id,name,price,active,version) values (10,1001,'商品',10,true,0)");
        jdbc.update("insert into `order`(id,customer_id,merchant_id,status,total_amount,created_at) values (1,301,1001,'CREATED',10,CURRENT_TIMESTAMP)");
    }

    @Test
    void 外层事务回滚时不能提前记录动作成功() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            cancelSuccessfully();
            verify(auditRecorder, never()).record(any());
            assertEquals("CANCELLED", jdbc.queryForObject("select status from `order` where id=1", String.class));
            transaction.setRollbackOnly();
        });
        assertEquals("CREATED", jdbc.queryForObject("select status from `order` where id=1", String.class));
        var event = ArgumentCaptor.forClass(CrudGovernanceAuditEvent.class);
        verify(auditRecorder, times(1)).record(event.capture());
        assertEquals(CrudGovernanceAuditOutcome.EXECUTION_FAILED, event.getValue().getOutcome());
        assertEquals("customer-user-a", event.getValue().getSubject().getSubjectId());
    }

    @Test
    void 业务失败加入外层事务时最终只记录一次回滚() {
        jdbc.update("update `order` set status='CANCELLED' where id=1");
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            try {
                mvc.perform(post("/api/ent-crud/order/action/cancel").header("Authorization", "Bearer demo-customer-a")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"payload\":{\"orderId\":1}}"))
                    .andExpect(status().isBadRequest());
            } catch (Exception failure) { throw new AssertionError(failure); }
            verify(auditRecorder, never()).record(any());
            transaction.setRollbackOnly();
        });
        var event = ArgumentCaptor.forClass(CrudGovernanceAuditEvent.class);
        verify(auditRecorder, times(1)).record(event.capture());
        assertEquals(CrudGovernanceAuditOutcome.EXECUTION_FAILED, event.getValue().getOutcome());
    }

    @Test
    void 外层事务提交后才记录动作成功() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            cancelSuccessfully();
            verify(auditRecorder, never()).record(any());
        });
        var event = ArgumentCaptor.forClass(CrudGovernanceAuditEvent.class);
        verify(auditRecorder, times(1)).record(event.capture());
        assertEquals(CrudGovernanceAuditOutcome.SUCCESS, event.getValue().getOutcome());
        assertEquals("CANCELLED", jdbc.queryForObject("select status from `order` where id=1", String.class));
    }

    @Test
    void 提交后审计异常不改变业务成功也不重复记录失败() {
        doThrow(new IllegalStateException("模拟审计存储异常")).when(auditRecorder).record(any());
        cancelSuccessfully();
        assertEquals("CANCELLED", jdbc.queryForObject("select status from `order` where id=1", String.class));
        verify(auditRecorder, times(1)).record(any());
    }

    private void cancelSuccessfully() {
        try {
            mvc.perform(post("/api/ent-crud/order/action/cancel").header("Authorization", "Bearer demo-customer-a")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"payload\":{\"orderId\":1}}"))
                .andExpect(status().isOk());
        } catch (Exception failure) { throw new AssertionError(failure); }
    }

    @Test
    void 匿名商家与管理员均不能代替客户下单() throws Exception {
        for (String token : List.of("", "demo-merchant-a", "demo-platform-admin")) {
            var request = post("/api/ent-crud/order/action/create").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"items\":[{\"merchantProductId\":10,\"quantity\":1}]}}");
            if (!token.isEmpty()) request.header("Authorization", "Bearer " + token);
            mvc.perform(request).andExpect(status().isForbidden());
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from `order`", Integer.class));
    }

    @Test
    void 下单归属来自当前身份且不能回退通用创建() throws Exception {
        mvc.perform(post("/api/ent-crud/order/action/create").header("Authorization", "Bearer demo-customer-a")
                .contentType(MediaType.APPLICATION_JSON).content("{\"payload\":{\"items\":[{\"merchantProductId\":10,\"quantity\":1}]}}"))
            .andExpect(status().isOk());
        assertEquals(301L, jdbc.queryForObject("select customer_id from `order` where id=(select max(id) from `order`)", Long.class));
        mvc.perform(post("/api/ent-crud/order/create").header("Authorization", "Bearer demo-customer-a")
                .contentType(MediaType.APPLICATION_JSON).content("{\"payload\":{\"customerId\":302,\"merchantId\":2001}}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/ent-crud/order/action/place").header("Authorization", "Bearer demo-customer-a")
                .contentType(MediaType.APPLICATION_JSON).content("{\"payload\":{\"items\":[]}}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void 客户与商家只能取消所属订单且状态流转不可重复() throws Exception {
        for (String token : List.of("demo-customer-b", "demo-merchant-b")) {
            mvc.perform(post("/api/ent-crud/order/action/cancel").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"payload\":{\"orderId\":1}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
            assertEquals("CREATED", jdbc.queryForObject("select status from `order` where id=1", String.class));
        }
        for (String token : List.of("demo-customer-a", "demo-merchant-a")) {
            jdbc.update("update `order` set status='CREATED' where id=1");
            mvc.perform(post("/api/ent-crud/order/action/cancel").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"payload\":{\"orderId\":1}}"))
                .andExpect(status().isOk());
            assertEquals("CANCELLED", jdbc.queryForObject("select status from `order` where id=1", String.class));
            mvc.perform(post("/api/ent-crud/order/action/cancel").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"payload\":{\"orderId\":1}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
        }
    }

    @Test
    void 取消时并发状态变化造成写入未命中() throws Exception {
        doReturn(false).when(orderService).update(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        mvc.perform(post("/api/ent-crud/order/action/cancel").header("Authorization", "Bearer demo-customer-a")
                .contentType(MediaType.APPLICATION_JSON).content("{\"payload\":{\"orderId\":1}}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WRITE_CONFLICT"));
        assertEquals("CREATED", jdbc.queryForObject("select status from `order` where id=1", String.class));
    }

    @Test
    void 通用更新不能绕过订单业务动作() throws Exception {
        mvc.perform(post("/api/ent-crud/order/update").header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON).content("{\"payload\":{\"id\":1,\"status\":\"CANCELLED\"}}"))
            .andExpect(status().isForbidden());
        assertEquals("CREATED", jdbc.queryForObject("select status from `order` where id=1", String.class));
    }
}
