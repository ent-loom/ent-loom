package com.example.minicommerce.order.security;

/** 订单业务权限动作，对应配置中的 order 资源。 */
public enum OrderAction {
    /** 提交订单。 */
    PLACE,
    /** 查询订单及明细。 */
    DETAIL
}
