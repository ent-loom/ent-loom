package com.entloom.crud.starter.support;

import com.entloom.crud.annotations.EntCrudEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 仅用于测试的示例实体。
 */
@EntCrudEntity(table = "test_order", idField = "id", ownerService = "starter-test-service")
@Getter
@Setter
public class TestOrderEntity {
    public enum OrderStatus {
        CREATED,
        PAID,
        SHIPPED,
        FINISHED,
        CANCELED
    }

    public enum PaymentChannel {
        WECHAT,
        ALIPAY,
        BANK_CARD,
        CASH
    }

    private Long id;
    private String orderNo;
    private OrderStatus status;
    private PaymentChannel paymentChannel;
    private Boolean paid;
    private Integer deleted;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal payAmount;
    private Integer itemCount;
    private LocalDate orderDate;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
