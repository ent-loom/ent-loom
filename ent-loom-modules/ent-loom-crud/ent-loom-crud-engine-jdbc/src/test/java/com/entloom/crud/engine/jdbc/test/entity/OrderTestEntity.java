package com.entloom.crud.engine.jdbc.test.entity;

import com.entloom.crud.annotations.EntCrudEntity;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 订单测试实体。
 */
@EntCrudEntity(table = "t_order", idField = "id", logicDeleteField = "isDeleted", ownerService = "test-service")
@Getter
@Setter
public class OrderTestEntity {
    private Long id;
    private String orderNo;
    private Long schoolId;
    private Integer isDeleted;
    private List<OrderItemTestEntity> items;

}
