package com.entloom.e5.statictest.fixture;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.api.enums.CrudIdPolicy;

/**
 * 实体 DAO MySQL 8 验收使用的显式主键订单。
 */
@EntCrudEntity(
    name = "dao_order",
    table = "dao_order",
    idField = "id",
    idPolicy = CrudIdPolicy.EXPLICIT,
    logicDeleteField = "isDeleted",
    logicDeleteNotDeletedValue = "0",
    logicDeleteDeletedValue = "1",
    scopeFields = {"schoolId", "tenantId"},
    ownerService = "crud-dao-integration-test"
)
public class DaoOrder {
    /** 显式业务主键。 */
    public Long id;
    /** 订单编号。 */
    public String orderNo;
    /** 学校范围。 */
    public Long schoolId;
    /** 租户范围。 */
    public String tenantId;
    /** 逻辑删除标记，0 表示未删除，1 表示已删除。 */
    public Integer isDeleted;
}
