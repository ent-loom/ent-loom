package com.entloom.crud.engine.jdbc.test.entity;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.api.enums.CrudIdPolicy;
import lombok.Getter;
import lombok.Setter;

/**
 * Repository JDBC 集成测试实体。
 */
@EntCrudEntity(
    table = "t_repository_order",
    idField = "id",
    idPolicy = CrudIdPolicy.GENERATED,
    logicDeleteField = "isDeleted",
    ownerService = "test-service"
)
@Getter
@Setter
public class RepositoryOrderTestEntity {
    /** 数据库自增主键。 */
    private Long id;
    /** 订单编号，集成测试表上有唯一约束。 */
    private String orderNo;
    /** 可条件更新的普通数值字段。 */
    private Integer priority = 0;
    /** 逻辑删除标记，0 表示正常，1 表示已删除。 */
    private Integer isDeleted = 0;

    public RepositoryOrderTestEntity() {
    }

    public RepositoryOrderTestEntity(String orderNo) {
        this.orderNo = orderNo;
    }

    public RepositoryOrderTestEntity(Long id, String orderNo) {
        this.id = id;
        this.orderNo = orderNo;
    }
}
