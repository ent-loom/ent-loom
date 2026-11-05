package com.example.minicommerce.order.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.annotations.EntCrudGrant;
import com.entloom.crud.annotations.EntCrudGrantScope;
import com.entloom.ddl.annotations.EntDdlIndex;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntRelation;
import com.example.minicommerce.merchant.entity.Merchant;
import com.example.minicommerce.customer.entity.Customer;
import com.example.minicommerce.order.enums.CommerceOrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 每笔订单属于一个客户和一个商家；跨商家下单由订单命令服务在同一事务内拆分。
 *
 * <p>标准读取复用客户、商家策略；下单与取消均经过统一身份授权。</p>
 */
@EntEntity("订单")
@EntDdlIndex(fields = "customerId")
@EntDdlIndex(fields = "merchantId")
@EntCrudGrant(identities = "customer", actions = {"view", "create", "cancel"})
@EntCrudGrant(identities = "merchant", actions = {"view", "cancel"})
@EntCrudGrant(identities = "super-admin", actions = {"view", "cancel"}, scope = EntCrudGrantScope.ALL)
@EntCrudEntity(actions = {"view", "create", "cancel"}, operations = {"create="})
@Getter
@Setter
@NoArgsConstructor
public class CommerceOrder {
    public static final String CREATE = "create";
    public static final String CANCEL = "cancel";

    // 值属性：默认映射当前表列。

    /** 订单主键，由数据库自增生成。 */
    @EntField("订单 ID")
    private Long id;

    @EntField("客户 ID")
    @EntRelation(Customer.class)
    private Long customerId;

    @EntField("商家 ID")
    @EntRelation(Merchant.class)
    private Long merchantId;

    /** 订单生命周期状态，参见 {@link CommerceOrderStatus}。 */
    @EntField("订单状态")
    private CommerceOrderStatus status;

    /** 下单时各明细金额的合计。 */
    @EntField("订单总金额")
    private BigDecimal totalAmount;

    /** 订单创建时间，使用应用配置的本地时间。 */
    @EntField("创建时间")
    private LocalDateTime createdAt;

    // 结构属性：不映射当前表列，仅在显式展开时由 CRUD 填充。

    @EntField("客户")
    @TableField(exist = false)
    private Customer customer;

    @EntField("订单明细")
    @TableField(exist = false)
    private List<CommerceOrderItem> orderItemList;
}
