package com.entloom.crud.engine.jdbc.test.entity;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.annotations.EntCrudField;
import com.entloom.crud.enums.RelationScope;
import com.entloom.meta.enums.RelationCardinality;
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
    private String tenantId;
    private Integer isDeleted;
    @EntCrudField(
        targetClass = OrderItemTestEntity.class,
        sourceField = "id",
        targetField = "orderId",
        cardinality = RelationCardinality.ONE_TO_MANY,
        scope = RelationScope.LOCAL_DB
    )
    private List<OrderItemTestEntity> items;

}
