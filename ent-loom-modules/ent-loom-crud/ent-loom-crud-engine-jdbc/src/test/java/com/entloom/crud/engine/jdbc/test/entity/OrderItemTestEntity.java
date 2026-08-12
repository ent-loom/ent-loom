package com.entloom.crud.engine.jdbc.test.entity;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.annotations.EntCrudField;
import com.entloom.meta.enums.RelationCardinality;
import com.entloom.crud.enums.RelationScope;
import lombok.Getter;
import lombok.Setter;

/**
 * 订单明细测试实体。
 */
@EntCrudEntity(table = "t_order_item", idField = "id", logicDeleteField = "isDeleted", ownerService = "test-service")
@Getter
@Setter
public class OrderItemTestEntity {
    private Long id;

    @EntCrudField(
        targetClass = OrderTestEntity.class,
        targetField = "id",
        cardinality = RelationCardinality.MANY_TO_ONE,
        scope = RelationScope.LOCAL_DB
    )
    private Long orderId;

    private Long studentId;

    private String skuCode;
    private Integer quantity;
    private Integer isDeleted;
    private StudentTestEntity student;

}
